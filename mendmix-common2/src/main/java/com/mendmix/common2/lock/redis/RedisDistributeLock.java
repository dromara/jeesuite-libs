/*
 * Copyright 2016-2022 www.mendmix.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mendmix.common2.lock.redis;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.mendmix.common.GlobalConstants;
import com.mendmix.common.GlobalRuntimeContext;
import com.mendmix.common.MendmixBaseException;
import com.mendmix.spring.InstanceFactory;

/**
 * 基于redis的锁
 * 
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2018年3月22日
 */
public class RedisDistributeLock  {

	private static Logger logger = LoggerFactory.getLogger("com.mendmix.common2");
	
	private static final String KEY_PREFIX = "_dlock:";

	private static StringRedisTemplate redisTemplate;

	private static StringRedisTemplate getRedisTemplate() {
		if(redisTemplate != null)return redisTemplate;
		if (redisTemplate == null) {
			try {
				redisTemplate = InstanceFactory.getInstance(StringRedisTemplate.class);
			} catch (Exception e) {
			}
		}
		return redisTemplate;
	}

	private static String getLockLua = "local res = redis.call('setnx', KEYS[1],ARGV[1])\n" + 
            "if tonumber(res) > 0 then\n" + 
            "	redis.call('PEXPIRE', KEYS[1], ARGV[2])\n" + 
            "	return 1\n" + 
            "else \n" + 
            "	return 0\n" + 
            "end";
	
	DefaultRedisScript<Long> lockScript = new DefaultRedisScript<>(getLockLua, Long.class);
	private static final long _DEFAULT_MAX_WAIT = 60 * 1000;
	private String lockName;
	private long maxLiveMillis;

	/**
	 * 默认最大存活时间60秒
	 * 
	 * @param resKey
	 *            资源唯一标识，如:user:1
	 */
	public RedisDistributeLock(String resKey) {
		this(resKey, _DEFAULT_MAX_WAIT);
	}

	/**
	 * 
	 * @param resKey
	 * @param maxLiveMillis
	 *            锁最大存活时间（毫秒）
	 */
	public RedisDistributeLock(String resKey, long maxLiveMillis) {
		this.lockName = KEY_PREFIX.concat(resKey);
		this.maxLiveMillis = maxLiveMillis;
	}

	public void lock() {
		boolean locked = tryLock(maxLiveMillis, TimeUnit.MILLISECONDS);
		if (!locked) {
			unlock();
			throw new MendmixBaseException("Lock[" + lockName + "] timeout");
		}

	}

	public boolean tryLock() {
		if (getRedisTemplate() == null)
			return true;
		String threadKey = buildThreadKey();
		Long result = redisTemplate.execute(lockScript, Arrays.asList(lockName), threadKey, String.valueOf(maxLiveMillis));
		return result != null && result == 1;
	}

	public boolean tryLock(long time, TimeUnit unit) {
		long start = System.currentTimeMillis();
		boolean res = tryLock();
		if (res)
			return res;

		long sleep = 100;
		while (!res) {
			try {
				TimeUnit.MILLISECONDS.sleep(sleep);
			} catch (InterruptedException e) {
			}
			if (res = tryLock()) {
				return res;
			} else if (sleep > 20) {
				sleep = sleep - 10;
			}

			if (System.currentTimeMillis() - start > unit.toMillis(time)) {
				return false;
			}
		}

		return false;
	}

	public boolean isIdle() {
		if (getRedisTemplate() == null)
			return true;
		return !redisTemplate.hasKey(lockName);
	}

	public long blockUtilIdle(long time, TimeUnit unit) {
		if (isIdle())
			return 0L;
		long start = System.currentTimeMillis();
		boolean idle = false;
		long sleep = 100;
		while (!idle) {
			try {
				TimeUnit.MILLISECONDS.sleep(sleep);
			} catch (InterruptedException e) {
			}
			if (idle = isIdle()) {
				return System.currentTimeMillis() - start;
			} else if (sleep > 20) {
				sleep = sleep - 10;
			}

			if (System.currentTimeMillis() - start > unit.toMillis(time)) {
				return System.currentTimeMillis() - start;
			}
		}

		return 0L;
	}

	public void unlock() {
		if (getRedisTemplate() == null)
			return;
		String val = redisTemplate.opsForValue().get(lockName);
		String threadKey = buildThreadKey();
		if(threadKey.equals(val)) {			
			redisTemplate.delete(lockName);
		}else if(val != null){
			logger.info(">>线程[{}] 解锁不匹配!! -> lockName:{},期望线程:{}",threadKey,lockName,val);
		}
	}
	
	private String buildThreadKey() {
		return new StringBuilder(GlobalRuntimeContext.getWorkId())
				.append(GlobalConstants.AT)
				.append(Thread.currentThread().getName())
				.toString();
	}

}

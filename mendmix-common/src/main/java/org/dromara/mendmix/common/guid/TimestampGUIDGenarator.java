/*
 * Copyright 2016-2022 dromara.org.
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
package org.dromara.mendmix.common.guid;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomUtils;

import org.dromara.mendmix.common.GlobalContext;
import org.dromara.mendmix.common.util.DateUtils;
import org.dromara.mendmix.common.util.StringConverter;

/**
 * 带时间错信息全局唯一id生成器
 * 
 * <br>
 * Class Name   : TimestampGUIDGenarator
 *
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @version 1.0.0
 * @date May 15, 2021
 */
public class TimestampGUIDGenarator {

	private static final String TIME_PATTERN = "yyyyMMddHHmmss";
	private final AtomicInteger NEXT_COUNTER;
	private int incrMin;
	private int incrMax;
	
	

	public TimestampGUIDGenarator(int incrMax) {
		this.incrMin = Integer.parseInt("1" + String.valueOf(incrMax).substring(1).replaceAll("[0-9]{1}", "0"));
		this.incrMax = incrMax;
		NEXT_COUNTER = new AtomicInteger(RandomUtils.nextInt(incrMin, incrMax/2));
	}

	public String next(String...prefixs) {
		StringBuilder sb = new StringBuilder();
		if(prefixs != null && prefixs.length > 0 && prefixs[0] != null) {
			sb.append(prefixs[0]);
		}
		sb.append(DateUtils.format(new Date(),TIME_PATTERN));
		sb.append(GlobalContext.getWorkerId());
		sb.append(buildIncrNumSequence());
		return sb.toString();
	}

	private String buildIncrNumSequence() {
		int next = NEXT_COUNTER.incrementAndGet();
		if(incrMax - next < 5){
			next = NEXT_COUNTER.updateAndGet( (x) -> x >= incrMax ? RandomUtils.nextInt(incrMin, incrMax/2) : x + 1);
		}
		String seq = String.valueOf(next);
		return StringConverter.paddingZeros(seq, 5);
	}
	
	public static void main(String[] args) {
		TimestampGUIDGenarator genarator = new TimestampGUIDGenarator(9999);
		
		for (int i = 0; i < 10; i++) {
			System.out.println(genarator.next());
		}
	}

}

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
package org.dromara.mendmix.test.cache;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.dromara.mendmix.cache.command.RedisHashMap;
import org.dromara.mendmix.cache.command.RedisNumber;
import org.dromara.mendmix.cache.command.RedisObject;
import org.dromara.mendmix.cache.command.RedisSet;
import org.dromara.mendmix.cache.command.RedisSortSet;
import org.dromara.mendmix.cache.command.RedisStrHashMap;
import org.dromara.mendmix.cache.command.RedisStrSet;
import org.dromara.mendmix.cache.command.RedisStrSortSet;
import org.dromara.mendmix.cache.command.RedisString;
import org.dromara.mendmix.spring.InstanceFactory;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:test-cache.xml"})
public class CacheCommondTest implements ApplicationContextAware{
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();


	@Override
	public void setApplicationContext(ApplicationContext arg0) throws BeansException {	
		InstanceFactory.setApplicationContext(arg0);
	}
	
	@Test
	public void testRedisString(){
		//字符串
		RedisString redisString = new RedisString("User.id:1001");
		redisString.set("user1001",60);
		String value = redisString.get();
		System.out.println(value);
		redisString.getTtl();
		redisString.exists();
		redisString.setExpire(300);
		redisString.remove();
	}
	
	@Test
	public void testRedisObject() {
		// 对象
		RedisObject redisObject = new RedisObject("User.id:1001");
		redisObject.set(new User(1001, "jack"));
		Object user = redisObject.get();
		redisObject.getTtl();
		redisObject.exists();
		redisObject.setExpire(300);
		redisObject.remove();
	}
	
	@Test
	public void testRedisNumber(){
		long increase = new RedisNumber("ins_test").increase(5);
		System.out.println(increase);
		System.out.println(new RedisNumber("ins_test").increase(5));
	}
	
	@Test
	public void testRedisHashMap() {
		RedisHashMap redisHashMap = new RedisHashMap("User.all");
		redisHashMap.set("1001", new User(1001, "jack"));
		redisHashMap.set("1002", new User(1002, "jack2"));
		
		Map<String, User> users = redisHashMap.get("1001","1002");
		System.out.println(users);
		users = redisHashMap.getAll();
		System.out.println(users);
		User one = redisHashMap.getOne("1001");
		
		redisHashMap.containsKey("1001");
		
		redisHashMap.remove();
	}
	
	@Test
	public void testRedisStrHashMap() {
		RedisStrHashMap map = new RedisStrHashMap("redisStrHashMap");
		map.set("a", "aa");
		map.set("b", "bb");
		System.out.println("map.getOne:" + map.getOne("a"));
		System.out.println("map.getAll:" + map.getAll());
	}
	
	@Test
	public void testRedisSet() {
		RedisSet redisSet = new RedisSet("redisSet");
		redisSet.add("aa","bb");
		System.out.println(redisSet.get());
		System.out.println(redisSet.length());
		redisSet.remove();
		System.out.println(redisSet.length());
	}
	
	@Test
	public void testStrRedisSet() {

		RedisStrSet redisSet = new RedisStrSet("redisStrSet");
		redisSet.add("aa","bb");
		System.out.println(redisSet.get());
		System.out.println(redisSet.length());
		redisSet.remove();
		System.out.println(redisSet.length());
	
	}
	
	@Test
	public void testRedisSortSet() {
		//
		RedisSortSet sortSet = new RedisSortSet("redisSortSet");
		
		sortSet.add(1, "1");
		sortSet.add(2, "2");
		sortSet.add(3, "3");
		sortSet.add(4, "4");
		
		System.out.println("==============");
		System.out.println(sortSet.get());
		
		sortSet.removeByScore(1, 2);
		
		System.out.println(sortSet.get());
	}
	
	@Test
	public void testStrRedisSortSet() {
		
	}
	
	@Test
	public void testLocalRedis() throws InterruptedException{
		//字符串
		RedisString redisString = new RedisString("User.id:1001");
		redisString.set("user1001",60);
		String value = redisString.get();
		System.out.println(value);
		value = redisString.get();
		System.out.println(value);
		
		redisString.remove();
		
		Thread.sleep(5000);
	}
	
	@Test
	public void test11(){
		RedisSet redisSet = new RedisSet("setkey");
		
		redisSet.remove();
		
		User user = new User(1, "jim");
		
		User user2 = new User();
		user2.setMobile("13800138000");
		user2.setEmail("@@@");
		
		User user3 = new User();
		user3.setId(1);
		user3.setMobile("13800138000");
		user3.setEmail("@@@222222");
		
		redisSet.add(user,user2,user3);
		
//		for (int i = 0; i < 3; i++) {
//			User userx = new User();
//			userx.setId(1);
//			userx.setName("name"+i);
//			redisSet.add(userx);
//		}
		
		Set<User> set = redisSet.get();
		
		System.out.println(set);
	}
	
	@Test
	public void redisStrSortSetTest(){
		RedisStrSortSet sortSet = new RedisStrSortSet("sorttest");
		sortSet.add(2020091010, "2020091010");
		sortSet.add(2020091011, "2020091011");
		sortSet.add(2020091012, "2020091012");
		sortSet.add(2020091013, "2020091013");
		
		List<String> score = sortSet.rangeByScore(2020091010, 2020091013);
		System.out.println(Arrays.toString(score.toArray()));
		sortSet.removeByScore(2020091011, 2020091011);
		System.out.println("-----------------------");
		score = sortSet.rangeByScore(2020091010, 2020091013);
		System.out.println(Arrays.toString(score.toArray()));
	}
	
}

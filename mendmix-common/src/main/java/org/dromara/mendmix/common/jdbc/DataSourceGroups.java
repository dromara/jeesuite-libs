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
package org.dromara.mendmix.common.jdbc;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.dromara.mendmix.common.GlobalConstants;
import org.dromara.mendmix.common.MendmixBaseException;
import org.dromara.mendmix.common.util.ResourceUtils;
import org.dromara.mendmix.common.util.SimpleCryptUtils;

import com.alibaba.druid.pool.DruidDataSource;

/**
 * 
 * 
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">jiangwei</a>
 * @date 2022年6月7日
 */
public class DataSourceGroups {

	private static Map<String, DataSource> groupDataSources = new HashMap<String, DataSource>();

	public static DataSource getDataSource(String group) {
		if(groupDataSources.containsKey(group)) {
			return groupDataSources.get(group);
		}
		if(!ResourceUtils.containsProperty(group + ".dataSource.url")) {
			throw new MendmixBaseException("未找到[" + group + "]数据库配置");
		}
		synchronized (groupDataSources) {
			if(groupDataSources.containsKey(group)) {
				return groupDataSources.get(group);
			}
			DruidDataSource dataSource = new DruidDataSource();
			dataSource.setDriverClassName(ResourceUtils.getProperty(group + ".dataSource.driverClassName","com.mysql.cj.jdbc.Driver"));
			dataSource.setUrl(ResourceUtils.getProperty(group + ".dataSource.url"));
			dataSource.setUsername(ResourceUtils.getAndValidateProperty(group + ".dataSource.username"));
			String password = ResourceUtils.getAndValidateProperty(group + ".dataSource.password");
			if(password.startsWith(GlobalConstants.CRYPT_PREFIX)) {
				password = SimpleCryptUtils.decrypt(password.replace(GlobalConstants.CRYPT_PREFIX, ""));
			}
			dataSource.setPassword(password);
			dataSource.setMaxActive(ResourceUtils.getInt(group + ".dataSource.maxActive", 2));
			dataSource.setMinIdle(1);
			dataSource.setDefaultAutoCommit(true);
			dataSource.setTestOnBorrow(true);
			try {
				dataSource.init();
			} catch (SQLException e) {
				dataSource.close();
				throw new RuntimeException(e);
			}
			groupDataSources.put(group, dataSource);
			
			return dataSource;
		}
	}
	
	public static void close() {
		Collection<DataSource> dataSources = groupDataSources.values();
		for (DataSource dataSource : dataSources) {
			try {
				((DruidDataSource)dataSource).close();
			} catch (Exception e) {
				
			}
		}
	}
}

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
package com.mendmix.mybatis.datasource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.mendmix.common.util.ClassScanner;
import com.mendmix.common.util.ResourceUtils;

/**
 * 
 * 
 * <br>
 * Class Name   : DataSoureConfigHolder
 *
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @version 1.0.0
 * @date Sep 11, 2021
 */
public class DataSoureConfigHolder {

	private static Map<String, List<DataSourceConfig>> allGroupConfigs;
	
	private static Map<String, List<DataSourceConfig>> getAllGroupConfigs() {
		if(allGroupConfigs == null) {
			Properties properties = ResourceUtils.getAllProperties(".*(\\.db\\.).*", false);
			allGroupConfigs = resolveConfigs(properties);
		}
		return allGroupConfigs;
	}
	
	public static List<String> getGroups(){
		return new ArrayList<>(getAllGroupConfigs().keySet());
	}
	
	public static List<DataSourceConfig> getConfigs(String group){
		return getAllGroupConfigs().get(group);
	}
	
	public static boolean containsSlaveConfig(){
		for (String group : getAllGroupConfigs().keySet()) {
			if(allGroupConfigs.get(group).stream().anyMatch(o -> !o.getMaster())) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean containsTenantConfig(String group){
		if(getAllGroupConfigs().get(group).stream().anyMatch(o -> StringUtils.isNotBlank(o.getTenantKey()))) {
			return true;
		}
		return false;
	}
	
    public synchronized static Map<String, List<DataSourceConfig>> resolveConfigs(Properties properties){
		
		Map<String, List<DataSourceConfig>> groupConfigs = new HashMap<>();
		
		Map<String,Field> fieldMap = new HashMap<>();
		Field[] fields = DataSourceConfig.class.getDeclaredFields();
		for (Field field : fields) {
			field.setAccessible(true);
			fieldMap.put(field.getName(), field);
		}
		ClassScanner.whoUseMeReport();
		Map<String,DataSourceConfig> configs = new HashMap<>();

		Set<Entry<Object, Object>> entrySet = properties.entrySet();
		
		DataSourceConfig config;
		String dsKey;
		String fieldName;
		for (Entry<Object, Object> entry : entrySet) {
			String key = entry.getKey().toString();
			String value = entry.getValue().toString();
			
			String[] arrays = StringUtils.split(key, ".");
			config = buildDataSourceConfig(arrays);
			dsKey = config.dataSourceKey();
			
			if(configs.containsKey(dsKey)) {
				config = configs.get(dsKey);
			}else {
				configs.put(dsKey, config);
			}
			
			fieldName = arrays[arrays.length - 1];
			if(fieldMap.containsKey(fieldName)) {
				try {fieldMap.get(fieldName).set(config, value);} catch (Exception e) {}
			}
		}
		
		Validate.isTrue(!configs.isEmpty(), "Not Any DataSource Config Found");
		
		List<DataSourceConfig> groupList;
		for (DataSourceConfig dsConfig : configs.values()) {
			groupList = groupConfigs.get(dsConfig.getGroup());
			if(groupList == null) {
				groupConfigs.put(dsConfig.getGroup(), (groupList = new ArrayList<>()));
			}
			groupList.add(dsConfig);
		}
		
		return groupConfigs;
	}
	
	//group[default].tenant[abc].master.db.url=xx
	//group[default].tenant[abc].slave[0].db.url=xx
	private static DataSourceConfig buildDataSourceConfig(String[] arrays) {
		DataSourceConfig config = new DataSourceConfig();
		for (String item : arrays) {
			if(item.startsWith("group")) {
				config.setGroup(parseFieldValue(item));
			}else if(item.startsWith("tenant")) {
				config.setTenantKey(parseFieldValue(item));
			}else if(item.startsWith("slave")) {
				String value = parseFieldValue(item);
				if(value != null) {
					config.setIndex(Integer.parseInt(value));
				}
				config.setMaster(false);
			}else if(item.equals("master")) {
				config.setIndex(0);
				config.setMaster(true);
			}
		}
		
		return config;
	}
	
	private static String parseFieldValue(String field) {
		if(!field.contains("["))return null;
		return StringUtils.split(field, "[]")[1];
	}
	
}

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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.mendmix.common.GlobalConstants;
import com.mendmix.common.GlobalRuntimeContext;
import com.mendmix.common.MendmixBaseException;
import com.mendmix.common.model.RoundRobinSelecter;
import com.mendmix.common.util.BeanUtils;
import com.mendmix.common.util.ResourceUtils;
import com.mendmix.mybatis.MybatisConfigs;
import com.mendmix.mybatis.MybatisRuntimeContext;
import com.mendmix.mybatis.datasource.builder.DruidDataSourceBuilder;
import com.mendmix.spring.InstanceFactory;

/**
 * 自动路由多数据源（读写分离/多租户）
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2015年11月18日
 * @Copyright (c) 2015, jwww
 */
public class MultiRouteDataSource extends AbstractDataSource implements ApplicationContextAware,InitializingBean{  

	private static final Logger logger = LoggerFactory.getLogger(MultiRouteDataSource.class);

	private DataSourceType dataSourceType = DataSourceType.druid;
	
	private ApplicationContext context;
	
	private Map<String, DataSource> targetDataSources = new HashMap<>();

	private String group;
	private boolean dsKeyWithTenant = false;
	//每个master对应slave数
	private Map<String, RoundRobinSelecter> slaveNumSelecters = new HashMap<>();
	
	private RouteTenantKeyResolver routeTenantKeyResolver;

	private RouteTenantKeyResolver routeTenantKeyResolver() {
		if(routeTenantKeyResolver != null)return routeTenantKeyResolver;
		synchronized (this) {
			if(routeTenantKeyResolver != null)return routeTenantKeyResolver;
			routeTenantKeyResolver = InstanceFactory.getInstance(RouteTenantKeyResolver.class);
			if(routeTenantKeyResolver == null) {
				routeTenantKeyResolver = new RouteTenantKeyResolver() {
					@Override
					public String resolve(String tenantId) {
						return tenantId;
					}
				};
			}
		}
		return routeTenantKeyResolver;
	}

	public MultiRouteDataSource() {
		this(DataSourceConfig.DEFAULT_GROUP_NAME);
	}

	public MultiRouteDataSource(String group) {
		this.group = group;
		dsKeyWithTenant = MybatisConfigs.isSchameSharddingTenant(group);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		List<DataSourceConfig> dsConfigs = DataSoureConfigHolder.getConfigs(group);

		for (DataSourceConfig dataSourceConfig : dsConfigs) {			
			registerRealDataSource(dataSourceConfig);
			//
			if(dataSourceConfig.getTenantId() != null) {				
				GlobalRuntimeContext.addTenantId(dataSourceConfig.getTenantId());
			}
		}

		if (this.targetDataSources == null || targetDataSources.isEmpty()) {
			throw new IllegalArgumentException("Property 'targetDataSources' is required");
		}
		
		logger.info("MENDMIX-TRACE-LOGGGING-->> init multiRouteDataSource[{}] finished -> dsKeyWithTenant:{}",group,dsKeyWithTenant);
	}

	private String currentDataSourceKey() {
		DataSourceContextVals context = MybatisRuntimeContext.getDataSourceContextVals();
		boolean useMaster = context.master == null ? true : context.master;
		int index = 0;
		String tenantId = dsKeyWithTenant ? context.tenantId : null;
		if(dsKeyWithTenant && StringUtils.isBlank(tenantId)) {
			throw new MendmixBaseException("Can't get [tentantId] from currentContext");
		}
		if(!useMaster) {
			if(slaveNumSelecters.isEmpty()) {
				useMaster = true;
			}else {
				String subGroup = dsKeyWithTenant ? group + GlobalConstants.UNDER_LINE + tenantId : group;
				if(!slaveNumSelecters.containsKey(subGroup)) {
					useMaster = true;
				}else {
					index = slaveNumSelecters.get(subGroup).select();
				}
			}
		}
		
		if(tenantId != null) {
			tenantId = routeTenantKeyResolver().resolve(tenantId);
		}
		return DataSourceConfig.buildDataSourceKey(group, tenantId, useMaster, index);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return determineTargetDataSource().getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return determineTargetDataSource().getConnection(username, password);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(Class<T> iface) throws SQLException {
		if (iface.isInstance(this)) {
			return (T) this;
		}
		return determineTargetDataSource().unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return (iface.isInstance(this) || determineTargetDataSource().isWrapperFor(iface));
	}

	protected DataSource determineTargetDataSource() {
		String lookupKey = currentDataSourceKey();
		DataSource dataSource = targetDataSources.get(lookupKey);
	    if (dataSource == null) {
			throw new MendmixBaseException("Cannot determine target DataSource for lookup key [" + lookupKey + "]");
		}
		return dataSource;
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.context = applicationContext;
		InstanceFactory.setApplicationContext(context);
	}


	/**
	 * 功能说明：根据DataSource创建bean并注册到容器中
	 * @param config
	 * @throws SQLException 
	 */
	private void registerRealDataSource(DataSourceConfig config) throws SQLException {
		
		config.validate();
		//
		mergeGlobalDataSourceConfig(config);
		
		DataSource dataSource = null;
		if (DataSourceType.druid == dataSourceType) {
			dataSource = DruidDataSourceBuilder.builder(config);
		} else if (DataSourceType.hikariCP == dataSourceType) {
			
		}

		String dsKey = config.dataSourceKey();
		targetDataSources.put(dsKey, dataSource);
		//保存slave节点数
		if(dsKey.contains(DataSourceConfig.SLAVE_KEY)) {
			String subGroup = StringUtils.splitByWholeSeparator(dsKey, "_slave")[0];
			if(slaveNumSelecters.containsKey(subGroup)) {
				slaveNumSelecters.get(subGroup).incrNode();
			}else {
				slaveNumSelecters.put(subGroup, new RoundRobinSelecter(1));
			}
		}
		logger.info("MENDMIX-TRACE-LOGGGING-->> register realDataSource[{}] finished! -> config:{}",config.dataSourceKey(),config.toString());
	}

	private void mergeGlobalDataSourceConfig(DataSourceConfig config) {
		String groupName = config.getGroup();
		DataSourceConfig globalConfig = ResourceUtils.getBean("db.", DataSourceConfig.class);
		BeanUtils.copy(globalConfig, config);
		config.setGroup(groupName);
		if(config.getTestOnBorrow() == null) {
			config.setTestOnBorrow(true);
		}
	}  

    
} 

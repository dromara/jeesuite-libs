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
package com.mendmix.mybatis.plugin.cache;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.persistence.Id;
import javax.persistence.Table;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mendmix.cache.CacheExpires;
import com.mendmix.cache.CacheUtils;
import com.mendmix.common.CurrentRuntimeContext;
import com.mendmix.common.GlobalConstants;
import com.mendmix.common.async.StandardThreadExecutor.StandardThreadFactory;
import com.mendmix.common.model.AuthUser;
import com.mendmix.common.util.DigestUtils;
import com.mendmix.common.util.JsonUtils;
import com.mendmix.common.util.ReflectUtils;
import com.mendmix.mybatis.MybatisConfigs;
import com.mendmix.mybatis.MybatisRuntimeContext;
import com.mendmix.mybatis.core.BaseEntity;
import com.mendmix.mybatis.core.InterceptorHandler;
import com.mendmix.mybatis.crud.CrudMethods;
import com.mendmix.mybatis.exception.MybatisHanlerInitException;
import com.mendmix.mybatis.kit.CacheKeyUtils;
import com.mendmix.mybatis.kit.MybatisSqlUtils;
import com.mendmix.mybatis.metadata.ColumnMetadata;
import com.mendmix.mybatis.metadata.MapperMetadata;
import com.mendmix.mybatis.metadata.MapperMetadata.MapperMethod;
import com.mendmix.mybatis.metadata.SqlMetadata;
import com.mendmix.mybatis.parser.MybatisMapperParser;
import com.mendmix.mybatis.plugin.InvocationVals;
import com.mendmix.mybatis.plugin.MendmixMybatisInterceptor;
import com.mendmix.mybatis.plugin.cache.annotation.Cache;
import com.mendmix.mybatis.plugin.cache.annotation.CacheIgnore;
import com.mendmix.spring.InstanceFactory;


/**
 * 自动缓存拦截处理
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2015年12月7日
 * @Copyright (c) 2015, jwww
 */
public class CacheHandler implements InterceptorHandler {

	protected static final Logger logger = LoggerFactory.getLogger("com.mendmix.mybatis.plugin.cache");

	public static final String CURRENT_USER_CONTEXT_NAME = "currentUser";
	private static final String ID_PART_CACHEKEY = ".id:";
	private static final String BLOCK_ON_CONCURRENT_LOCK_RETURN = "_block_on_concurrentLock";
	public static final String NAME = "cache";
	public final static long IN_1MINS = 60;
    public final static long IN_1HOUR = 60 * 60;
	public static long defaultCacheExpire = 0;
	
	private static String dataSourceGroupName;
	
	private static final String STR_PARAM = "param";
	
	public static final String GROUPKEY_SUFFIX = "~keys";
	private static final String ID_CACHEKEY_JOIN = ID_PART_CACHEKEY;
	private boolean nullValueCache = true;
	//null缓存占位符（避免频繁查询不存在对象造成缓存穿透导致频繁查询db）
	public static final String NULL_PLACEHOLDER = "~null";
	
	private static List<String> groupKeys = new ArrayList<>();
	
	//需要缓存的所有mapper
	private static List<String> cacheEnableMappers = new ArrayList<>();
	
	private static Map<String, Map<String, QueryCacheMethodMetadata>> queryCacheMethods = new HashMap<>();
	
	private static List<String> updateByPkCacheMethods = Arrays.asList(
			CrudMethods.insert.name(),
			CrudMethods.insertSelective.name(),
			CrudMethods.updateByPrimaryKey.name(),
			CrudMethods.updateByPrimaryKeySelective.name(),
			CrudMethods.deleteByPrimaryKey.name()
	);
	private static List<String> batchUpdateByPkCacheMethods = Arrays.asList("batchUpdateByPrimaryKeys");
	
	//<更新方法msId,[关联查询方法列表]>
	private static Map<String, List<String>> customUpdateCacheMapppings = new HashMap<>();
	
	private DataSource dataSource;
	
	private ExecutorService cleanCacheExecutor = Executors.newFixedThreadPool(1, new StandardThreadFactory("cleanCacheExecutor"));
	
	@Override
	public void start(MendmixMybatisInterceptor context) {
		
		dataSourceGroupName = context.getGroupName();
		
		Map<String, DataSource> dataSources = InstanceFactory.getBeansOfType(DataSource.class);
		if(dataSources.size() == 1) {
			dataSource = new ArrayList<>(dataSources.values()).get(0);
		}else {
			for (String beanName : dataSources.keySet()) {
				if(beanName.startsWith(dataSourceGroupName)) {
					dataSource = dataSources.get(beanName);
					break;
				}
			}
		}
		
		defaultCacheExpire = Long.parseLong(MybatisConfigs.getProperty(context.getGroupName(), MybatisConfigs.CACHE_EXPIRE_SECONDS, "0"));

		List<MapperMetadata> mappers = MybatisMapperParser.getMapperMetadatas(context.getGroupName());
		
		Class<BaseEntity> baseEntityClass = BaseEntity.class;
		QueryCacheMethodMetadata methodCache = null;
		for (MapperMetadata mm : mappers) {
			if(mm.getMapperClass().isAnnotationPresent(CacheIgnore.class))continue;
			if(!baseEntityClass.isAssignableFrom(mm.getEntityClass())){
				logger.warn("MENDMIX-TRACE-LOGGGING-->> [{}] not extends from [{}],ignore register auto cache!!!!",mm.getEntityClass().getName(),baseEntityClass.getName());
				continue;
			}
			Class<?> mapperClass = mm.getMapperClass();
			
			//按主键查询方法定义
			QueryCacheMethodMetadata queryByPKMethod = generateQueryByPKMethod(mapperClass, mm.getEntityClass());
			if(queryByPKMethod == null)continue;
			Map<String, QueryCacheMethodMetadata> tmpMap = new HashMap<>();
			//主键查询方法
			tmpMap.put(queryByPKMethod.methodName, queryByPKMethod);
			
			//接口定义的自动缓存方法
			for (MapperMethod method : mm.getMapperMethods().values()) {
				if(method.getMethod().isAnnotationPresent(Cache.class)){
					if(tmpMap.containsKey(method.getFullName()))continue;
					methodCache = generateQueryMethodCacheByMethod(mm, method);
					tmpMap.put(method.getFullName(), methodCache);
					logger.info("MENDMIX-TRACE-LOGGGING-->> 解析查询方法{}自动缓存配置 ok,keyPattern:[{}]",methodCache.methodName,methodCache.keyPattern);
				}
			}
			//缓存需要自动缓存的mapper
			cacheEnableMappers.add(mm.getMapperClass().getName());
			logger.info("MENDMIX-TRACE-LOGGGING-->> 解析查询方法{}自动缓存配置 ok,keyPattern:[{}]",queryByPKMethod.methodName,queryByPKMethod.keyPattern);
			
			queryCacheMethods.put(mapperClass.getName(), tmpMap);
		}
		//
		logger.info("MENDMIX-TRACE-LOGGGING-->> customUpdateCacheMapppings:{}",customUpdateCacheMapppings);
	}

	@Override
	public Object onInterceptor(InvocationVals invocationVal) throws Throwable {

		MappedStatement mt = invocationVal.getMappedStatement(); 

		boolean getLock = false;
		String cacheKey = null;
		if(mt.getSqlCommandType().equals(SqlCommandType.SELECT)){	
			//事务方法内部的查询不走缓存
			if(MybatisRuntimeContext.isForceUseMaster()){
				if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process skipCache[isTransactionalOn] -> mapperId:{}",mt.getId());
				return null;
			}
			//按主键查询
			QueryCacheMethodMetadata metadata = getQueryMethodCache(mt.getId());
			if(metadata == null) {
				return null;
			}
			invocationVal.setQueryCacheMetadata(metadata);
			cacheKey = genarateQueryCacheKey(invocationVal,metadata.keyPattern, invocationVal.getParameter());
			invocationVal.setCacheKey(cacheKey);

			//并发控制防止缓存穿透
			if(!metadata.concurrency){
				String concurrentLockKey = "concurrent:" + cacheKey;
				invocationVal.setConcurrentLockKey(concurrentLockKey);
				getLock = CacheUtils.setIfAbsent(concurrentLockKey, "1", 30,TimeUnit.SECONDS);
				if(!getLock){
					if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process notGetConcurrentLock -> mapperId:{}",mt.getId());
					return BLOCK_ON_CONCURRENT_LOCK_RETURN;
				}
				if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process getConcurrentLock -> mapperId:{}",mt.getId());
			}
			
			Object cacheObject = null;
			boolean nullPlaceholder = false;
			//
			if(!metadata.isSecondQueryById()){
				//从缓存读取
				cacheObject = CacheUtils.get(cacheKey);
				nullPlaceholder = nullValueCache && NULL_PLACEHOLDER.equals(cacheObject);
				if(StringUtils.isNotBlank(metadata.refKey) && (nullPlaceholder || cacheObject == null)){
					cacheObject = CacheUtils.get(metadata.refKey);
					nullPlaceholder = nullValueCache && NULL_PLACEHOLDER.equals(cacheObject);
				}
				if(nullPlaceholder){
					if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process hitCache -> mapperId:{},cacheKey:{}",mt.getId(),cacheKey);
				}else if(cacheObject != null){
					if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process hitCache -> mapperId:{},cacheKey:{}",mt.getId(),cacheKey);
				}
			}else{
				//新根据缓存KEY找到与按ID缓存的KEY
				String refCacheKey = nullValueCache ? CacheUtils.get(cacheKey) : CacheUtils.getStr(cacheKey);
				if(refCacheKey != null){
					if(nullPlaceholder = (nullValueCache && NULL_PLACEHOLDER.equals(refCacheKey))){
						cacheObject = NULL_PLACEHOLDER;
					}else{						
						cacheObject = CacheUtils.get(refCacheKey);
						if(cacheObject != null && logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process  hitRefCache -> mapperId:{},cacheKey:{},refCacheKey:{}",mt.getId(),cacheKey,refCacheKey);
					}
				}
			}
			
			if(nullPlaceholder){
				cacheObject = new ArrayList<>(0);
			}else if(cacheObject != null && !(cacheObject instanceof Collection)){	
				List<Object> tmpList = new ArrayList<>(1);
				tmpList.add(cacheObject);
				cacheObject = tmpList;
			}
			
			return cacheObject;
		} 
		
		return null;
	
	}
	

	@SuppressWarnings("rawtypes")
	@Override
	public void onFinished(InvocationVals invocationVal,Object result) {
		try {
			if(BLOCK_ON_CONCURRENT_LOCK_RETURN.equals(result))return;
			MappedStatement mt = invocationVal.getMappedStatement(); 
			
			QueryCacheMethodMetadata metadata = null;
			if(mt.getSqlCommandType().equals(SqlCommandType.SELECT)){	
				if(result == null)return; 
				if((metadata = invocationVal.getQueryMethodMetadata()) == null)return;
	
				final String cacheKey = invocationVal.getCacheKey();
				if(result instanceof List){
					List list = (List)result;
					if(list.isEmpty()){
						if(nullValueCache){
							CacheUtils.set(cacheKey,NULL_PLACEHOLDER, IN_1MINS);
						}
						return;
					}
					result = metadata.collectionResult ? result : list.get(0);
				}else if(nullValueCache && NULL_PLACEHOLDER.equals(result)) {
					CacheUtils.set(cacheKey,NULL_PLACEHOLDER, IN_1MINS);
					return;
				}
				//
				if(!metadata.isSecondQueryById()){
					CacheUtils.set(cacheKey,result, metadata.getExpire());
					if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process addCache -> mapperId:{},cacheKey:{}",mt.getId(),cacheKey);
                    
					if(metadata.isPk){
						//唯一索引（业务上）
						cacheUniqueSelectRef(invocationVal,result, mt, cacheKey);
					}else if(metadata.isGroupRalated()){//结果为集合的情况，增加key到cacheGroup
						CacheUtils.addStrItemToList(metadata.cacheGroupKey, cacheKey);
					}
				}else{
					//之前没有按主键的缓存，增加按主键缓存
					String idCacheKey = genarateQueryCacheKey(invocationVal,getQueryByPkMethodCache(mt.getId()).keyPattern,result);
					
					if(idCacheKey != null && cacheKey != null){
						if(!CacheUtils.exists(idCacheKey)){						
							//缓存idkey->实体
							CacheUtils.set(idCacheKey,result, metadata.getExpire());
						}
						//缓存fieldkey->idkey
						cacheFieldRefKey(cacheKey,idCacheKey, metadata.getExpire());
						if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process addCache -> mapperId:{},idCacheKey:{},cacheKey:{}",mt.getId(),idCacheKey,cacheKey);
					}
				}
			}else{
				String mapperClassName = invocationVal.getMapperNameSpace();
				if(!cacheEnableMappers.contains(mapperClassName) && !customUpdateCacheMapppings.containsKey(mt.getId()))return;
				//返回0，未更新成功
				if(result != null && ((int)result) == 0)return;
				//更新方法移除缓存，避免事务回滚导致缓存不一致，所以更新方法直接移除缓存
				if(mt.getSqlCommandType().equals(SqlCommandType.INSERT)) {
					//TODO 写入缓存 ，考虑回滚
				}else {
					MapperMetadata mapperMeta = MybatisMapperParser.getMapperMetadata(mapperClassName);
					if(updateByPkCacheMethods.contains(mt.getId().substring(mapperClassName.length() + 1))){
						StringBuilder keyBuilder = new StringBuilder(mapperMeta.getEntityClass().getSimpleName()).append(ID_PART_CACHEKEY);
                        if(invocationVal.getParameter() instanceof BaseEntity) {
                        	keyBuilder.append(((BaseEntity) invocationVal.getParameter()).getId());
                        }else {
                        	keyBuilder.append(invocationVal.getParameter());
                        }
						CacheUtils.remove(keyBuilder.toString());
						if(logger.isDebugEnabled())logger.debug(">>auto_cache_process removeCache -> mapperId:{},cacheKey:{}",mt.getId(),keyBuilder);
					}else if(batchUpdateByPkCacheMethods.contains(mt.getId().substring(mapperClassName.length() + 1))){
						List<String> idCacheKeys = buildBatchUpdateIdCacheKeys(mapperMeta, invocationVal.getParameter());
						if(!idCacheKeys.isEmpty())CacheUtils.remove(idCacheKeys.toArray(new String[0]));
						if(logger.isDebugEnabled())logger.debug(">>auto_cache_process removeCache -> mapperId:{},cacheKeys:{}",mt.getId(),idCacheKeys);
					}else {
						//针对按条件更新或者删除的方法，按查询条件查询相关内容，然后清理对应主键缓存内容
						final Object parameter = invocationVal.getArgs()[1];
						BoundSql boundSql = mt.getBoundSql(parameter);
						String orignSql = boundSql.getSql();
						ColumnMetadata idColumn = mapperMeta.getEntityMetadata().getIdColumn();
						SqlMetadata sqlMetadata = MybatisSqlUtils.rewriteAsSelectPkField(orignSql, idColumn.getColumn());
						//
						String tenantId = CurrentRuntimeContext.getTenantId();
						cleanCacheExecutor.execute(new Runnable() {
							@Override
							public void run() {
								if(tenantId != null) {
									CurrentRuntimeContext.setTenantId(tenantId);
								}
								removeCacheByDyncQuery(mapperMeta,boundSql, sqlMetadata);
							}
						});
					}
				}
				//删除同一cachegroup关联缓存
				removeCacheByGroup(mt.getId(), mapperClassName);
				//删除自定义关联缓存
				if(customUpdateCacheMapppings.containsKey(mt.getId())) {
					removeCustomRelateCache(mt.getId());
				}
			}
		} finally {
			//清除并发控制锁
			if(invocationVal.getConcurrentLockKey() != null){
				CacheUtils.remove(invocationVal.getConcurrentLockKey());
			}
		}
	}

	/**
	 * 缓存其他唯一结果查询方法和主键缓存的引用
	 * @param object
	 * @param mt
	 * @param cacheKey
	 */
	private void cacheUniqueSelectRef(InvocationVals invocationVal,Object object, MappedStatement mt, String cacheKey) {
		Collection<QueryCacheMethodMetadata> mcs = queryCacheMethods.get(mt.getId().substring(0, mt.getId().lastIndexOf(InvocationVals.DOT))).values();
		outter:for (QueryCacheMethodMetadata methodCache : mcs) {
			if(!methodCache.isSecondQueryById())continue;
			try {	
				Object[] cacheFieldValues = new Object[methodCache.fieldNames.length];
				for (int i = 0; i < cacheFieldValues.length; i++) {
					if(methodCache.fieldNames[i] == null)break outter;
					cacheFieldValues[i] = ReflectUtils.getObjectValue(object, methodCache.fieldNames[i]);
					if(cacheFieldValues[i] == null)continue outter;
				}
				String fieldCacheKey = genarateQueryCacheKey(invocationVal,methodCache.keyPattern , cacheFieldValues);
				
				cacheFieldRefKey(fieldCacheKey,cacheKey, methodCache.getExpire());
				if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process addRefCache -> mapperId:{},cacheKey:{},refkey:{}",mt.getId(),fieldCacheKey,cacheKey);
			} catch (Exception e) {
				logger.warn("MENDMIX-TRACE-LOGGGING-->> cacheUniqueSelectRef:"+cacheKey,e);
			}
		}
	}
	
	/**
	 * 缓存字段查询到idkey
	 * @param fieldCacheKey
	 * @param idCacheKey
	 * @param expired
	 */
	private void cacheFieldRefKey(String fieldCacheKey,String idCacheKey,long expired){
		if(nullValueCache){
			CacheUtils.set(fieldCacheKey, idCacheKey, expired);
		}else{
			CacheUtils.setStr(fieldCacheKey, idCacheKey, expired);
		}
	}
	
	
	/**
	 * 根据动态查询内容清理缓存
	 * @param sqlMetadata 查询主键列表SQL语句信息
	 * @param parameter 参数
	 * @throws Exception 
	 */
	private void removeCacheByDyncQuery(MapperMetadata mapperMeta,BoundSql boundSql,SqlMetadata sqlMetadata) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			MybatisSqlUtils.parseDyncQueryParameters(boundSql, sqlMetadata);
			connection = dataSource.getConnection();
			statement = connection.prepareStatement(sqlMetadata.getSql());
			
			List<Object> parameters = sqlMetadata.getParameters();
			for (int i = 0; i < parameters.size(); i++) {
				statement.setObject(i+1, parameters.get(i));
			}
			
			rs = statement.executeQuery();
			List<String> ids = new ArrayList<>();
			while (rs.next()) {
				ids.add(rs.getString(1));
			}
			if(ids != null && !ids.isEmpty()){
				List<String> idCacheKeys = ids.stream().map(id -> {
					return mapperMeta.getEntityClass().getSimpleName() + ID_CACHEKEY_JOIN + id.toString();
				}).collect(Collectors.toList());
				CacheUtils.remove(idCacheKeys.toArray(new String[0]));
				if(logger.isDebugEnabled()) {
					logger.debug("remove cacheKeys:{}",idCacheKeys);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			final String groupName = mapperMeta.getEntityClass().getSimpleName();
			clearCacheGroup(groupName);
		}finally {
			try {rs.close();} catch (Exception e2) {}
			try {statement.close();} catch (Exception e2) {}
			try {connection.close();} catch (Exception e2) {}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<String> buildBatchUpdateIdCacheKeys(MapperMetadata mapperMeta,Object parameter){
		Map map = (Map) parameter;
		List<Object> ids;
		if(map.containsKey("arg0")) {
			ids = (List<Object>) map.get("arg0");
		}else {
			ids = (List<Object>) map.get("param1");
		}
		
		List<String> keys = new ArrayList<>(ids.size());
		StringBuilder keyBuilder = new StringBuilder(mapperMeta.getEntityClass().getSimpleName()).append(ID_PART_CACHEKEY);
		int prefixLen = keyBuilder.length();
		for (Object id : ids) {
			keyBuilder.append(id);
			keys.add(keyBuilder.toString());
			keyBuilder.setLength(prefixLen);
		}
		
		return keys;
	}
	
	/**
	 * 删除缓存组
	 * @param msId
	 * @param mapperClassName
	 * @param removePkCache  是否同时删除按主键的缓存
	 */
	private void removeCacheByGroup(String msId, String mapperClassName) {
		MapperMetadata entityInfo = MybatisMapperParser.getMapperMetadata(mapperClassName);
		if(entityInfo == null)return;
		final String groupName = entityInfo.getEntityClass().getSimpleName();
		cleanCacheExecutor.execute(new Runnable() {
			@Override
			public void run() {
				clearCacheGroup(groupName);
				if(logger.isDebugEnabled())logger.debug("MENDMIX-TRACE-LOGGGING-->>  auto_cache_process removeGroupCache -> mapperId:{},groupName:{}",msId,groupName);
			}
		});
	}
	
	/**
	 * 删除更新方法自定义缓存关系
	 * @param updateId
	 */
	private void removeCustomRelateCache(String updateId) {
		final List<String> queryMethods = customUpdateCacheMapppings.get(updateId);
		cleanCacheExecutor.execute(new Runnable() {
			@Override
			public void run() {
				QueryCacheMethodMetadata metadata;
				for (String method : queryMethods) {
					metadata = getQueryMethodCache(method);
					String prefix = StringUtils.splitByWholeSeparator(metadata.keyPattern, "%s")[0];
					clearCacheGroup(metadata.cacheGroupKey,prefix);
				}
			}
		});
	}
	
	/**
	 * 生成查询缓存key
	 * @param cacheInfo
	 * @param param
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static String genarateQueryCacheKey(InvocationVals invocationVal,String keyPattern,Object param){
		String text;
		try {
			Object[] args;
			if(param instanceof Map){
				Map<String, Object> map = (Map<String, Object>) param;
				if(map.containsKey(STR_PARAM + "1")){
					args = new String[map.size()/2];
					for (int i = 0; i < args.length; i++) {
						args[i] = CacheKeyUtils.objcetToString(map.get(STR_PARAM + (i+1)));
					}
				}else{
					args = new String[]{CacheKeyUtils.objcetToString(map)};
				}
			}else if(param instanceof BaseEntity){
				Serializable id = ((BaseEntity)param).getId();
				if(id != null && !"0".equals(id.toString())){	
					args = new String[]{(((BaseEntity)param).getId()).toString()};
				}else{
					args = new String[]{CacheKeyUtils.objcetToString(param)};
				}
			}else if(param instanceof Object[]){
				args = (Object[])param;
			}else if(param == null){
				args = new Object[0];
			}else{
				args = new String[]{CacheKeyUtils.objcetToString(param)};
			}
			
			text = StringUtils.join(args,GlobalConstants.UNDER_LINE);
		} catch (Exception e) {
			text = JsonUtils.toJson(param);
			e.printStackTrace();
		}
		if(text.length() > 64)text = DigestUtils.md5(text);

		String key = String.format(keyPattern, text);	
		QueryCacheMethodMetadata methodMetadata = invocationVal.getQueryMethodMetadata();
		if(methodMetadata == null || methodMetadata.isPk || methodMetadata.uniqueIndex) {
			return key;
		}
		
		StringBuilder sb = new StringBuilder(key);
		String tenantId = CurrentRuntimeContext.getTenantId();
		if(tenantId != null) {
			sb.append(GlobalConstants.AT).append(tenantId);
		}
		
		AuthUser currentUser;
		if(invocationVal.isDynaDataPermEnaled() && (currentUser = CurrentRuntimeContext.getCurrentUser()) != null) {
			sb.append(GlobalConstants.AT).append(currentUser.getId());
		}
		
		return sb.toString();
	}
	

	
	private QueryCacheMethodMetadata getQueryMethodCache(String mtId){
		String key1 = mtId.substring(0, mtId.lastIndexOf(InvocationVals.DOT));
		if(queryCacheMethods.containsKey(key1)){
			return queryCacheMethods.get(key1).get(mtId);
		}
		return null;
	}
	
	private QueryCacheMethodMetadata getQueryByPkMethodCache(String mtId){
		mtId = mtId.substring(0, mtId.lastIndexOf(InvocationVals.DOT));
		if(queryCacheMethods.containsKey(mtId)){
			return queryCacheMethods.get(mtId).get(mtId + "." + CrudMethods.selectByPrimaryKey.name());
		}
		return null;
	}

	/**
	 * 生成按主键查询缓存定义
	 * @param mapperClass
	 * @param entityClass
	 * @return
	 */
	private QueryCacheMethodMetadata generateQueryByPKMethod(Class<?> mapperClass,Class<?> entityClass){
		QueryCacheMethodMetadata methodCache = null;
		Field[] fields = entityClass.getDeclaredFields();
		//主键key前缀
		for (Field field : fields) {
			if(field.isAnnotationPresent(Id.class)){
				methodCache = new QueryCacheMethodMetadata();
				methodCache.isPk = true;
				methodCache.collectionResult = false;
				methodCache.keyPattern = entityClass.getSimpleName() + ".id:%s";
				methodCache.methodName = mapperClass.getName() + "." + CrudMethods.selectByPrimaryKey.name();
				methodCache.expire = defaultCacheExpire;
				methodCache.cacheGroupKey = entityClass.getSimpleName() + GROUPKEY_SUFFIX;
				break;
			}
		}
		
		groupKeys.add(methodCache.cacheGroupKey);
		
		return methodCache;
	}

	/**
	 * 按查询方法生成缓存key前缀
	 * @param entityClassName
	 * @param method
	 * @return
	 */
	private QueryCacheMethodMetadata generateQueryMethodCacheByMethod(MapperMetadata mapperMeta,MapperMethod mapperMethod){

		Method method = mapperMethod.getMethod();
		Cache cacheAnnotation = method.getAnnotation(Cache.class);
		String[] evictOnMethods = cacheAnnotation.evictOnMethods();
		Class<?> mapperClass = mapperMeta.getMapperClass();
		Class<?> entityClass = mapperMeta.getEntityClass();
		QueryCacheMethodMetadata methodCache = new QueryCacheMethodMetadata();
		methodCache.methodName = mapperClass.getName() + InvocationVals.DOT + method.getName();
		methodCache.concurrency = cacheAnnotation.concurrency();
		methodCache.uniqueIndex = cacheAnnotation.uniqueIndex();
		methodCache.cacheGroupKey = entityClass.getSimpleName() + GROUPKEY_SUFFIX;
		if(cacheAnnotation.userScope()){
			methodCache.contextParam = CURRENT_USER_CONTEXT_NAME;
		}else if(cacheAnnotation.scopeContext().length > 0){
			methodCache.contextParam = cacheAnnotation.scopeContext()[0];
		}
		
		if(cacheAnnotation.refKey().length > 0){
			methodCache.refKey = cacheAnnotation.refKey()[0];
		}
		
		if(methodCache.contextParam != null && evictOnMethods.length == 0){
			evictOnMethods = new String[]{"*"};
		}
		
		methodCache.checkExpired = evictOnMethods.length > 0;
		if(cacheAnnotation.expire() > 0){
			methodCache.expire = cacheAnnotation.expire();
		}else if(cacheAnnotation.userScope()){
			methodCache.expire = IN_1MINS * 10 < defaultCacheExpire ? IN_1MINS * 10 : defaultCacheExpire;
		}
		
		if(methodCache.uniqueIndex && method.getReturnType() != entityClass){
			throw new MybatisHanlerInitException("@Cache with[uniqueIndex = true] but ReturnType not Match ["+entityClass.getName()+"]");
		}
		methodCache.collectionResult = method.getReturnType() == List.class || method.getReturnType() == Set.class;
		methodCache.fieldNames = new String[method.getParameterTypes().length];
		Annotation[][] annotations = method.getParameterAnnotations();
		boolean uniqueQuery = method.getReturnType().isAnnotationPresent(Table.class);
		for (int i = 0; i < annotations.length; i++) {
			Annotation[] aa = annotations[i];
			if(aa.length > 0){
				String fieldName = null;
				inner:for (Annotation annotation : aa) {
					if(annotation.toString().contains(Param.class.getName())){
						fieldName = ((Param)annotation).value();
						break inner;
					}
				}
				if(uniqueQuery && mapperMeta.getEntityMetadata().getProp2ColumnMappings().containsKey(fieldName)){					
					methodCache.fieldNames[i] = fieldName;
				}
			}
			//
		}
		methodCache.keyPattern = new StringBuilder(entityClass.getSimpleName()).append(InvocationVals.DOT).append(method.getName()).append(":%s").toString();
		
		if(uniqueQuery){
			for (String name : methodCache.fieldNames) {
				if(StringUtils.isBlank(name)){
					methodCache.fieldNames = null;
					break;
				}
			}
		}
		//
		buildEvictOnMethods(mapperClass.getName(),mapperMethod,evictOnMethods);

		return methodCache;
	}
	
	/**
	 * 构建自定义缓存更新关系
	 * @param mapperClassName
	 * @param method
	 * @param evictOnMethods
	 */
	private void buildEvictOnMethods(String mapperClassName,MapperMethod method,String[] evictOnMethods) {
		if(evictOnMethods == null|| evictOnMethods.length == 0){
			return;
		}
		
		String targetMethodFullNamePrefix = mapperClassName.substring(0, mapperClassName.lastIndexOf(".") + 1);
		String targetMapperClassName = null;
		for (String methodName : evictOnMethods) {
			if("*".equals(methodName)){
				methodName = mapperClassName + ".*";
			}else if(!methodName.contains(InvocationVals.DOT)) {
				methodName = mapperClassName + InvocationVals.DOT + methodName;
			}
			if(!methodName.startsWith(targetMethodFullNamePrefix)){
				methodName = targetMethodFullNamePrefix + methodName;
			}
			targetMapperClassName = methodName.substring(0,methodName.lastIndexOf("."));
			if(!methodName.endsWith("*")){
				addCacheCheckRelations(methodName, method.getFullName());
			}else{
				MapperMetadata methodEntityInfo = MybatisMapperParser.getMapperMetadata(targetMapperClassName);
				if(methodEntityInfo == null){
					continue;
				}

				for (MapperMethod mm : methodEntityInfo.getMapperMethods().values()) {
					if(mm.getSqlType() == SqlCommandType.SELECT)continue;
					if(mm.getFullName().contains(methodName.replace("*", ""))){
						addCacheCheckRelations(mm.getFullName(), method.getFullName());
					}
				}
			}
		}
		
	}
	
	
	private void addCacheCheckRelations(String updateMethodName,String queryMethodName){
		 List<String> list = customUpdateCacheMapppings.get(updateMethodName);
		 if(list == null){
			 list = new ArrayList<>();
			 customUpdateCacheMapppings.put(updateMethodName, list);
		 }
		 list.add(queryMethodName);
	}
	
	public void clearCacheGroup(final String groupName,String ...prefixs) {
		String cacheGroupKey = groupName.endsWith(CacheHandler.GROUPKEY_SUFFIX) ? groupName : groupName + CacheHandler.GROUPKEY_SUFFIX;
		int keyCount = (int) CacheUtils.getListSize(cacheGroupKey);
		if(keyCount <= 0)return;
		int batchSize = 1000;
	    //保护策略
		if(keyCount > batchSize) {
			CacheUtils.setExpire(cacheGroupKey, CacheExpires.todayEndSeconds());
		}
		
		boolean withPrefixs = prefixs != null && prefixs.length > 0 && prefixs[0] != null;
		
		int toIndex;
		List<String> keys;
		for (int i = 0; i <= keyCount; i+=batchSize) {
			toIndex = (i + batchSize) > keyCount ? keyCount : (i + batchSize);
			keys = CacheUtils.getStrListItems(cacheGroupKey,i, toIndex);
			if(keys.isEmpty())break;
			//
			if(withPrefixs) {
				keys = keys.stream().filter(key -> {
					for (String prefix : prefixs) {
						if(key.contains(prefix))return true;
					}
					return false;
				}).collect(Collectors.toList());
			}
			if(keys.isEmpty())continue;
			//
			CacheUtils.remove(keys.toArray(new String[0]));
			if(logger.isDebugEnabled()) {
				logger.debug("_clearGroupKey -> group:{},keys:{}",groupName,Arrays.toString(keys.toArray()));
			}
		}
	}
	
	@Override
	public void close() {}

	@Override
	public int interceptorOrder() {
		return 1;
	}
}

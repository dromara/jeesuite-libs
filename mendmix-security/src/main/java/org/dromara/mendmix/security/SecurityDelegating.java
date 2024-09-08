/*
 * Copyright 2016-2018 dromara.org.
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
package org.dromara.mendmix.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dromara.mendmix.common.CurrentRuntimeContext;
import org.dromara.mendmix.common.async.RetryAsyncTaskExecutor;
import org.dromara.mendmix.common.async.RetryTask;
import org.dromara.mendmix.common.constants.PermissionLevel;
import org.dromara.mendmix.common.exception.ForbiddenAccessException;
import org.dromara.mendmix.common.exception.UnauthorizedException;
import org.dromara.mendmix.common.model.AuthUser;
import org.dromara.mendmix.common.util.ExceptionFormatUtils;
import org.dromara.mendmix.security.event.SessionEventType;
import org.dromara.mendmix.security.event.SessionLifeCycleEvent;
import org.dromara.mendmix.security.model.ApiPermission;
import org.dromara.mendmix.security.model.UserSession;
import org.dromara.mendmix.security.util.ApiPermssionHelper;
import org.dromara.mendmix.spring.InstanceFactory;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2018年11月30日
 */
public class SecurityDelegating {
	
	
	private static Logger logger = LoggerFactory.getLogger("org.dromara.mendmix.security");
	
	private static final int SESSION_INTERVAL_MILLS =  60 * 1000;
	private static final String TMP_STATUS_CACHE_NAME = "tmpStatus";
	private SecurityDecisionProvider decisionProvider;
	private SecuritySessionManager sessionManager;
	private SecurityStorageManager storageManager;
	
	private static volatile SecurityDelegating instance;

	private SecurityDelegating() {
		decisionProvider = InstanceFactory.getInstance(SecurityDecisionProvider.class);
		storageManager = new SecurityStorageManager(decisionProvider.cacheType());
		sessionManager = new SecuritySessionManager(decisionProvider,storageManager);
		storageManager.addCahe(TMP_STATUS_CACHE_NAME, 60);
		logger.info("MENDMIX-TRACE-LOGGGING-->> SecurityDelegating inited !!,sessisonStorageType:{}",decisionProvider.cacheType());
	}

	private static SecurityDelegating getInstance() {
		if(instance != null)return instance;
		synchronized (SecurityDelegating.class) {
			if(instance != null)return instance;
			instance = new SecurityDelegating();
		}
		return instance;
	}
	
	public static void init() {
		getInstance();
	}

	public static SecurityDecisionProvider decisionProvider() {
		return getInstance().decisionProvider;
	}

	
	/**
	 * 认证
	 * @param name
	 * @param password
	 */
	public static UserSession doAuthentication(String name,String password){
		AuthUser userInfo = getInstance().decisionProvider.validateUser(name, password);
		UserSession session = updateSession(userInfo,true);
		//
		InstanceFactory.getContext().publishEvent(new SessionLifeCycleEvent(SessionEventType.create, session));
		return session;
	}

	
	public static UserSession updateSession(AuthUser userInfo,boolean loadPermssion){
		UserSession session = getCurrentSession();
		if(session == null)session = UserSession.create();
		//多系统情况，已第一次登录的系统id为准
		if(session.getSystemId() == null) {
			session.setSystemId(CurrentRuntimeContext.getSystemId());
		}
		session.setTenanId(CurrentRuntimeContext.getTenantId());
		session.setClientType(CurrentRuntimeContext.getClientType());
		session.setUser(userInfo);
		
		if(getInstance().decisionProvider.kickOff()){
			UserSession otherSession = getInstance().sessionManager.getLoginSessionByUserId(userInfo);
			if(otherSession != null && !otherSession.getSessionId().equals(session.getSessionId())){
				getInstance().sessionManager.removeLoginSession(otherSession.getSessionId());
			}
		}
		getInstance().sessionManager.storageLoginSession(session);
		
		if(loadPermssion) {
			UserSession _session = session;
			if(!userInfo.isAdmin() && getInstance().decisionProvider.apiAuthzEnabled()) {
				CurrentRuntimeContext.setAuthUser(userInfo);
				CurrentRuntimeContext.setSystemId(session.getSystemId());
				CurrentRuntimeContext.setTenantId(session.getTenanId());
				RetryAsyncTaskExecutor.execute(new RetryTask() {
					@Override
					public String traceId() {
						return ExceptionFormatUtils.buildLogHeader("fetchUserPermissions", userInfo.getName());
					}
					@Override
					public boolean process() throws Exception {
						getInstance().fetchUserPermissions(_session);
						return true;
					}
				});
			}
		}
		
		return session;
	}
	
	/**
	 * 鉴权
	 * @param userId
	 * @param uri
	 */
	public static UserSession doAuthorization(String method,String uri) throws UnauthorizedException,ForbiddenAccessException{
		
		UserSession session = getCurrentSession();
		//续租
		if(session != null) {
			long interval = System.currentTimeMillis() - getInstance().sessionManager.getUpdateTime(session);
			if(interval > SESSION_INTERVAL_MILLS) {
				getInstance().sessionManager.storageLoginSession(session);
				InstanceFactory.getContext().publishEvent(new SessionLifeCycleEvent(SessionEventType.renewal, session));
			}
		}

		boolean isAdmin = session != null && session.getUser() != null && session.getUser().isAdmin();
		
		
		ApiPermission permissionObject = ApiPermssionHelper.matchPermissionObject(method, uri);
		if((session == null || session.isAnonymous()) && PermissionLevel.Anonymous != permissionObject.getPermissionLevel()) {
			throw new UnauthorizedException();
		}
		//兼容多系统切换
		if(session != null) {
			session.setSystemId(CurrentRuntimeContext.getSystemId());
		}
		
		if(!isAdmin && getInstance().decisionProvider.apiAuthzEnabled()) {
			//如果需鉴权
			if(permissionObject.getPermissionLevel() == PermissionLevel.PermissionRequired){
				List<String> permissions = getInstance().getUserPermissions(session);
				if(!permissions.contains(permissionObject.getPermissionKey())){
					throw new ForbiddenAccessException();
				}
			}
		}

		return session;
	}
	
	
	public static UserSession getAndValidateCurrentSession(){
		UserSession session = getCurrentSession();
		if(session == null || session.isAnonymous()){
			throw new UnauthorizedException();
		}
		return session;
	}
	
	public static UserSession getCurrentSession(){
		UserSession session = getInstance().sessionManager.getSession();
		return session;
	}
	
	public static String getCurrentSessionId(){
		return getInstance().sessionManager.getSessionId();
	}
	
	public static UserSession genUserSession(String sessionId){
    	return getInstance().sessionManager.getLoginSession(sessionId);
    }
	
	public static boolean validateSessionId(String sessionId){
		UserSession session = getInstance().sessionManager.getLoginSession(sessionId);
		return session != null && session.isAnonymous() == false; 
	}
	
	public static void refreshUserPermssion(Serializable...userIds){
		if(userIds != null && userIds.length > 0 && userIds[1] != null){
			
		}else{
			
		}
	}

    public static void doLogout(){
    	UserSession session = getCurrentSession();
    	if(session == null)return;
    	InstanceFactory.getContext().publishEvent(new SessionLifeCycleEvent(SessionEventType.destory, session));
    	getInstance().sessionManager.destroySessionAndCookies(session);
	}
    
    public static void setSessionAttribute(String name, Object object) {
    	getInstance().sessionManager.setSessionAttribute(name, object);
    }

	public static <T> T getSessionAttribute(String name) {
		return getInstance().sessionManager.getSessionAttribute(name);
	}

	public static void setTemporaryState(String key,String value) {
		Cache cache = getInstance().storageManager.getCache(TMP_STATUS_CACHE_NAME);
		cache.setString(key, value);
	}
	
    public static String getTemporaryState(String key) {
    	Cache cache = getInstance().storageManager.getCache(TMP_STATUS_CACHE_NAME);
    	return cache.getString(key);
	}
    
    private List<String> getUserPermissions(UserSession session){
    	List<String> permissions = sessionManager.getUserPermissions(session);
    	if(permissions != null)return permissions;
    	synchronized (getInstance()) {
    		permissions = fetchUserPermissions(session);
    		return permissions;
		}
    }

	private List<String> fetchUserPermissions(UserSession session) {
		List<String> permissions;
		List<ApiPermission> apiPermissions = decisionProvider.getUserApiPermissions(session.getUser().getId());
		if(apiPermissions == null || apiPermissions.isEmpty()) {
			permissions = new ArrayList<>(0);
		}else {
			permissions = new ArrayList<>(apiPermissions.size());
			for (ApiPermission api : apiPermissions) {
				permissions.add(ApiPermssionHelper.buildPermissionKey(api.getMethod(), api.getUri()));
			}
		}
		sessionManager.updateUserPermissions(session, permissions);
		return permissions;
	}

}

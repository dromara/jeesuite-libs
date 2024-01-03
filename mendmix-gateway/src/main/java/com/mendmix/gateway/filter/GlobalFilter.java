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
package com.mendmix.gateway.filter;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.mendmix.common.CurrentRuntimeContext;
import com.mendmix.common.CustomRequestHeaders;
import com.mendmix.common.GlobalConstants;
import com.mendmix.common.MendmixBaseException;
import com.mendmix.common.ThreadLocalContext;
import com.mendmix.common.exception.ForbiddenAccessException;
import com.mendmix.common.exception.UnauthorizedException;
import com.mendmix.common.model.ApiInfo;
import com.mendmix.common.model.AuthUser;
import com.mendmix.common.model.WrapperResponse;
import com.mendmix.common.util.JsonUtils;
import com.mendmix.gateway.CurrentSystemHolder;
import com.mendmix.gateway.GatewayConfigs;
import com.mendmix.gateway.GatewayConstants;
import com.mendmix.gateway.helper.RequestContextHelper;
import com.mendmix.gateway.model.BizSystem;
import com.mendmix.gateway.model.BizSystemModule;
import com.mendmix.gateway.model.BizSystemPortal;
import com.mendmix.gateway.security.AuthorizationProvider;
import com.mendmix.gateway.security.SpecUnauthorizedHandler;
import com.mendmix.logging.actionlog.ActionLog;
import com.mendmix.logging.actionlog.ActionLogCollector;

import reactor.core.publisher.Mono;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakinge</a>
 * @date Jun 11, 2022
 */
public class GlobalFilter implements WebFilter {

	private static Logger logger = LoggerFactory.getLogger("com.mendmix.security");
	
	private String matchUriPrefix;
	private AuthorizationProvider authorizationProvider;
	private SpecUnauthorizedHandler specUnauthorizedHandler = new SpecUnauthorizedHandler();


	public GlobalFilter(String matchUriPrefix, AuthorizationProvider authorizationProvider) {
		this.matchUriPrefix = matchUriPrefix;
		this.authorizationProvider = authorizationProvider;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		try {
			String uri = RequestContextHelper.getCurrentOriginUri(exchange);
			if((matchUriPrefix != null && !uri.startsWith(matchUriPrefix)) 
					|| RequestContextHelper.isWebSocketRequest(exchange.getRequest())) {
				exchange.getAttributes().put(GatewayConstants.CONTEXT_IGNORE_FILTER, Boolean.TRUE);
				return chain.filter(exchange);
			}
			
			if(request.getMethod().equals(HttpMethod.OPTIONS)) {
				return chain.filter(exchange);
			}
			
			RequestContextHelper.clearContextAttributes(exchange);
			
			beforeAuthentication(exchange);
			
			ServerHttpResponse response = exchange.getResponse();
			
			AuthUser currentUser = null;
			try {
				currentUser = authorizationProvider.doAuthorization(request);
			} catch (UnauthorizedException e) {
				if(!specUnauthorizedHandler.customAuthentication(exchange)) {
					RequestContextHelper.clearContextAttributes(exchange);
					return writeErrorResponse(request,response, e);
				}
				if(request.getHeaders().containsKey(CustomRequestHeaders.HEADER_AUTH_USER)) {
					AuthUser authUser = AuthUser.decode(request.getHeaders().getFirst(CustomRequestHeaders.HEADER_AUTH_USER));
					CurrentRuntimeContext.setAuthUser(authUser);
				}
			}catch (ForbiddenAccessException e) {	
				RequestContextHelper.clearContextAttributes(exchange);
				return writeErrorResponse(request,response, e);
			}
			//
			afterAuthentication(exchange,currentUser);
			
			return chain.filter(exchange) //
				    .doFinally(s -> {
				    	ActionLog actionLog = exchange.getAttribute(ActionLogCollector.CURRENT_LOG_CONTEXT_NAME);
				    	if(actionLog != null) {
				    		int respCode = response.getRawStatusCode();
				    		if(response.getHeaders().containsKey(CustomRequestHeaders.HEADER_EXCEPTION_CODE)) {
				               respCode = Integer.parseInt(response.getHeaders().getFirst(CustomRequestHeaders.HEADER_EXCEPTION_CODE));
				               actionLog.setSuccessed(false);
				    		}else {
				    			actionLog.setSuccessed(true);
				    		}
				    		ActionLogCollector.onResponseEnd(actionLog, respCode, null);
				    	}
				    	RequestContextHelper.clearContextAttributes(exchange);
				    });
		} catch (Exception e) {
			logger.error("MENDMIX-TRACE-LOGGGING-->> _global_filter_error",e);
			ThreadLocalContext.unset();
			exchange.getAttributes().clear();
			byte[] bytes = JsonUtils.toJsonBytes(WrapperResponse.fail(e));
			return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
		} 
	}

	
	
	private void beforeAuthentication(ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest();
		String domain = RequestContextHelper.getOriginDomain(request);
		BizSystemPortal portal = CurrentSystemHolder.getSystemPortal(domain);
		if(portal != null) {
			CurrentRuntimeContext.setTenantId(portal.getTenantId());
			CurrentRuntimeContext.setClientType(portal.getClientType());
		}
		//
		String systemId = getHeaderSystemId(request);
		if(systemId == null) {
			BizSystem system = CurrentSystemHolder.getSystem();
			if(system != null)systemId = system.getId();
		}
		if(systemId != null) {
			CurrentRuntimeContext.setSystemId(systemId);
		}
	}
	
	private String getHeaderSystemId(ServerHttpRequest request) {
		String systemId = request.getHeaders().getFirst(CustomRequestHeaders.HEADER_SYSTEM_ID);
		if(systemId != null) {
			if(logger.isTraceEnabled())logger.trace("header[x-system-id]={}",systemId);
			boolean matched = false;
			List<BizSystem> systems = CurrentSystemHolder.getSystems();
			for (BizSystem system : systems) {
				if(systemId.equals(system.getId()) || systemId.equals(system.getCode())) {
					systemId = system.getId();
					matched = true;
					break;
				}
			}
			if(!matched) {
				logger.warn("MENDMIX-TRACE-LOGGGING-->> header[x-system-id]={} can't matched",systemId);
				systemId = null;
			}
		
		}
		return systemId;
	}
	
	private void afterAuthentication(ServerWebExchange exchange,AuthUser currentUser) {
		if(!GatewayConfigs.actionLogEnabled)return;
		ServerHttpRequest request = exchange.getRequest();
		BizSystemModule module = CurrentSystemHolder.getModule(RequestContextHelper.resolveRouteName(request.getPath().value()));
		
		ApiInfo apiInfo = module.getApiInfo(request.getMethodValue(), request.getPath().value());
		boolean logging = apiInfo != null ? apiInfo.isActionLog() : true;
		if(logging) {
			logging = !GatewayConfigs.actionLogGetMethodIngore || !request.getMethod().equals(HttpMethod.GET);
		}
		if(logging){
			String clientIp = RequestContextHelper.getIpAddr(request);
			ActionLog actionLog = ActionLogCollector.onRequestStart(request.getMethodValue(),request.getPath().value(),clientIp).apiMeta(apiInfo);
		    exchange.getAttributes().put(ActionLogCollector.CURRENT_LOG_CONTEXT_NAME, actionLog);
		}
	}
	
	private Mono<Void> writeErrorResponse(ServerHttpRequest request,ServerHttpResponse response,MendmixBaseException e){
		if(GlobalConstants.FEIGN_CLIENT.equalsIgnoreCase(request.getHeaders().getFirst(HttpHeaders.USER_AGENT))) {
			response.setRawStatusCode(e.getCode());
		}
		response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
		byte[] bytes = JsonUtils.toJsonBytes(WrapperResponse.fail(e));
		return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
	}

}

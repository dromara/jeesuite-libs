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
package org.dromara.mendmix.gateway.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.ApplicationListener;

import org.dromara.mendmix.common.async.AsyncInitializer;
import org.dromara.mendmix.common.http.HttpMethod;
import org.dromara.mendmix.common.model.ApiInfo;
import org.dromara.mendmix.common.model.ApiModel;
import org.dromara.mendmix.gateway.CurrentSystemHolder;
import org.dromara.mendmix.gateway.GatewayConfigs;
import org.dromara.mendmix.gateway.model.BizSystemModule;
import org.dromara.mendmix.security.SecurityDecisionProvider;
import org.dromara.mendmix.security.SecurityDelegating;
import org.dromara.mendmix.security.model.ApiPermission;
import org.dromara.mendmix.security.util.ApiPermssionHelper;
import org.dromara.mendmix.spring.DataChangeEvent;

public abstract class GatewaySecurityDecisionProvider extends SecurityDecisionProvider implements AsyncInitializer ,ApplicationListener<DataChangeEvent>{
	
	
	@Override
	public boolean isServletType() {
		return false;
	}
	
	
	@Override
	public List<ApiModel> anonymousUris() {
		List<ApiModel> apis = new ArrayList<>();
		apis.add(new ApiModel(HttpMethod.GET,GatewayConfigs.PATH_PREFIX + "/actuator/health"));
		apis.add(new ApiModel(HttpMethod.GET,GatewayConfigs.PATH_PREFIX + "/oauth2/*"));
		return apis;
	}

	@Override
	public List<ApiPermission> getAllApiPermissions() {
		Collection<BizSystemModule> modules = CurrentSystemHolder.getModules();
		
		List<ApiPermission> result = new ArrayList<>();
		Collection<ApiInfo> apis;
		ApiPermission apiPermission;
		for (BizSystemModule module : modules) {
			if(module.getApiInfos() == null)continue;
			apis = module.getApiInfos().values();
			for (ApiInfo apiInfo : apis) {
				apiPermission = new ApiPermission();
				apiPermission.setPermissionLevel(apiInfo.getPermissionLevel());
				apiPermission.setMethod(apiInfo.getMethod());
				apiPermission.setUri(apiInfo.getUri());
				result.add(apiPermission);
			}
		}
		return result;
	}

	@Override
	public void doInitialize() {
		SecurityDelegating.init();
	}

	@Override
	public void onApplicationEvent(DataChangeEvent event) {
		if(event.getDataType().equals("moduleApis")) {
			ApiPermssionHelper.reload();
		}
	}
	
	

	
}

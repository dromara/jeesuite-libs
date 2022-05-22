/*
 * Copyright 2016-2022 www.jeesuite.com.
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
package com.jeesuite.gateway.endpoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jeesuite.common.GlobalRuntimeContext;
import com.jeesuite.common.JeesuiteBaseException;
import com.jeesuite.common.http.HttpRequestEntity;
import com.jeesuite.gateway.CurrentSystemHolder;
import com.jeesuite.gateway.GatewayConstants;
import com.jeesuite.gateway.model.BizSystemModule;

/**
 * 
 * <br>
 * Class Name   : ActuatorController
 *
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @version 1.0.0
 * @date May 18, 2022
 */
@RestController
@RequestMapping(GatewayConstants.PATH_PREFIX + "/actuator")
public class ActuatorController {

	@GetMapping("/health")
	public Map<String, Object> health(@RequestParam(value = "details",required = false) boolean details){
		Map<String, Object> result = new HashMap<>(1);
		result.put("status", "UP");
		result.put("startTime", GlobalRuntimeContext.STARTUP_TIME);
		
		if(details) {
			Collection<BizSystemModule> modules = CurrentSystemHolder.getModules();
			Map<String, Object> moduleStatus = new HashMap<>(modules.size());
			for (BizSystemModule module : modules) {
				try {
					String status = HttpRequestEntity.get(module.getHealthUri()).execute().toValue("status");
					moduleStatus.put(module.getServiceId(), status);
				}catch (JeesuiteBaseException e) {
					if(e.getCode() == 404 || e.getCode() == 401 || e.getCode() == 403) {
						result.put(module.getServiceId(), "UP");
					}else {
						result.put(module.getServiceId(), "UNKNOW");
					}
				} catch (Exception e) {
					result.put(module.getServiceId(), "UNKNOW");
				}
			}
			result.put("modules", moduleStatus);
		}
		
		
		return result;
	}
}


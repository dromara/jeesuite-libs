/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mendmix.common.model;

import java.lang.reflect.Method;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mendmix.common.GlobalConstants;
import com.mendmix.common.constants.PermissionLevel;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2018年4月15日
 */
public class ApiInfo extends ApiModel{

	private String name;
	private PermissionLevel permissionLevel;
	private String identifier;
	private boolean actionLog;
	private boolean requestLog;
	private boolean responseLog;
	private boolean openApi;
	@JsonIgnore
	private Method controllerMethod;
	@JsonIgnore
	private String controllerMethodName;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public String getIdentifier() {
		if(identifier == null && getUri() != null && getMethod() != null) {
			identifier = new StringBuilder(getMethod()).append(GlobalConstants.UNDER_LINE).append(getUri()).toString();
		}
		return identifier;
	}
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	public PermissionLevel getPermissionLevel() {
		return permissionLevel;
	}
	public void setPermissionLevel(PermissionLevel permissionLevel) {
		this.permissionLevel = permissionLevel;
	}
	public boolean isActionLog() {
		return actionLog;
	}
	public void setActionLog(boolean actionLog) {
		this.actionLog = actionLog;
	}
	public boolean isRequestLog() {
		return requestLog;
	}
	public void setRequestLog(boolean requestLog) {
		this.requestLog = requestLog;
	}
	public boolean isResponseLog() {
		return responseLog;
	}
	public void setResponseLog(boolean responseLog) {
		this.responseLog = responseLog;
	}
	public boolean isOpenApi() {
		return openApi;
	}
	public void setOpenApi(boolean openApi) {
		this.openApi = openApi;
	}
	public Method getControllerMethod() {
		return controllerMethod;
	}
	public void setControllerMethod(Method controllerMethod) {
		this.controllerMethod = controllerMethod;
	}
	public String getControllerMethodName() {
		return controllerMethodName;
	}
	public void setControllerMethodName(String controllerMethodName) {
		this.controllerMethodName = controllerMethodName;
	}
	

}

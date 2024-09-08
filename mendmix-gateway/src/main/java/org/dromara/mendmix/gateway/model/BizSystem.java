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
package org.dromara.mendmix.gateway.model;

import java.util.ArrayList;
import java.util.List;


public class BizSystem {

	private String id;
	/**
	 * 系统标识
	 */
	private String code;

	/**
	 * 系统名称
	 */
	private String name;

	/**
	 * 系统状态
	 */
	private Integer status;
	
	private String apiBaseUrl;

	
	private List<BizSystemPortal> portals;
	
	private List<BizSystemModule> modules;
	

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}


	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	public void setApiBaseUrl(String apiBaseUrl) {
		this.apiBaseUrl = apiBaseUrl;
	}

	public List<BizSystemPortal> getPortals() {
		return portals == null ? (portals = new ArrayList<>(0)) : portals;
	}

	public void setPortals(List<BizSystemPortal> portals) {
		this.portals = portals;
	}

	public List<BizSystemModule> getModules() {
		return modules == null ? (modules = new ArrayList<>(0)) : modules;
	}

	public void setModules(List<BizSystemModule> modules) {
		this.modules = modules;
	}

	@Override
	public String toString() {
		return "[id:" + id + ", code:" + code + ", name:" + name + ", modules:" + getModules().size() + ", portals:"
				+ getPortals().size() + "]";
	}

	
}

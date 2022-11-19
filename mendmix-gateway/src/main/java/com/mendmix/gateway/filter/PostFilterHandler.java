/*
 * Copyright 2016-2020 www.mendmix.com.
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

import org.springframework.web.server.ServerWebExchange;

import com.mendmix.gateway.model.BizSystemModule;

/**
 * 
 * 
 * <br>
 * Class Name   : PreFilterHandler
 *
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @version 1.0.0
 * @date 2020年9月15日
 */
public interface PostFilterHandler {

	default void onStarted() {}
	
	String process(ServerWebExchange exchange,BizSystemModule module,String respBodyAsString);
	
	int order();
	
}

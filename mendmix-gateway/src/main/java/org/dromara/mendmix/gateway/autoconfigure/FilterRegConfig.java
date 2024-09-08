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
package org.dromara.mendmix.gateway.autoconfigure;

import org.dromara.mendmix.gateway.GatewayConfigs;
import org.dromara.mendmix.gateway.filter.CachingRequestBodyFilter;
import org.dromara.mendmix.gateway.filter.GlobalFilter;
import org.dromara.mendmix.gateway.filter.GrayRouteFilter;
import org.dromara.mendmix.gateway.security.AuthorizationProvider;
import org.dromara.mendmix.gateway.security.DefaultAuthorizationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;  
  
@Configuration  
public class FilterRegConfig {
	
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ConditionalOnProperty(name = GatewayConfigs.CORS_ENABLED_CONFIG_KEY,havingValue = "true")
	public CorsWebFilter corsWebFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.addAllowedHeader("*");
		corsConfiguration.addAllowedMethod("*");
		corsConfiguration.addAllowedOriginPattern("*");
		corsConfiguration.setAllowCredentials(true);

		source.registerCorsConfiguration("/**", corsConfiguration);

		return new CorsWebFilter(source);
	}
	
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	public CachingRequestBodyFilter cachingRequestBodyFilter() {
		return new CachingRequestBodyFilter();
	}
	
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 2)
	public GlobalFilter globalFilter(@Autowired(required = false) AuthorizationProvider authorizationProvider) {
		if(authorizationProvider == null)authorizationProvider = new DefaultAuthorizationProvider();
		return new GlobalFilter(GatewayConfigs.PATH_PREFIX,authorizationProvider);
	}
	
	@Bean
	public GrayRouteFilter grayRouteFilter() {
		return new GrayRouteFilter();
	}
}  

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
package org.dromara.mendmix.security.connect.oauth;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakinge</a>
 * @date Nov 6, 2022
 */
public enum OauthParameter {

	clientId("{clientId}"),
	clientSecret("{clientSecret}"),
	redirectUri("{redirectUri}"),
	state("{state}"),
	code("{code}"),
	access_token("{access_token}"),
	refresh_token("{refresh_token}"),
	;
	
	private final String expression;

	private OauthParameter(String expression) {
		this.expression = expression;
	}

	public String getExpression() {
		return expression;
	}
	
	
}

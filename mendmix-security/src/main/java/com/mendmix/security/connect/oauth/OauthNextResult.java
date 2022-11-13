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
package com.mendmix.security.connect.oauth;

import com.mendmix.common.model.AuthUser;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakinge</a>
 * @date Nov 6, 2022
 */
public class OauthNextResult<T extends AuthUser> {
 
    private T user;
    private String redirectUrl;
    
    
	public OauthNextResult(T user, String redirectUrl) {
		super();
		this.user = user;
		this.redirectUrl = redirectUrl;
	}
	
	public T getUser() {
		return user;
	}
	public void setUser(T user) {
		this.user = user;
	}
	public String getRedirectUrl() {
		return redirectUrl;
	}
	public void setRedirectUrl(String redirectUrl) {
		this.redirectUrl = redirectUrl;
	}
    
    
    
}

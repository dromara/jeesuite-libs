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
package test;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.RandomStringUtils;

import org.dromara.mendmix.common.util.DigestUtils;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2016年11月4日
 */
public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	private Integer id;

	private String name;

	private String password = DigestUtils.md5(RandomStringUtils.random(8, true, true));

	private String mobile = "13800138000";

	private String email;

	private Short type = 1;

	private Short status = 1;

	private Date createdAt;
	
	private User father;

	public User() {}
	
	public User(Integer id, String name) {
		super();
		this.id = id;
		this.name = name;
		this.email = name + "@163.com";
		this.createdAt = new Date();
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Short getType() {
		return type;
	}

	public void setType(Short type) {
		this.type = type;
	}

	public Short getStatus() {
		return status;
	}

	public void setStatus(Short status) {
		this.status = status;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public User getFather() {
		return father;
	}

	public void setFather(User father) {
		this.father = father;
	}
	
	

}

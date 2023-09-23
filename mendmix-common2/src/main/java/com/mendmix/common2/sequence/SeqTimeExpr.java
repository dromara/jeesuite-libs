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
package com.mendmix.common2.sequence;

/**
 * 
 * <br>
 * Class Name   : SeqExpr
 *
 * @author jiangwei
 * @version 1.0.0
 * @date 2019年10月8日
 */
public enum SeqTimeExpr {

	/**
	 * 年
	 */
	YEAR("{yyyy}"),
	
	/**
	 * 年
	 */
	SHORT_YEAR("{yy}"),

	/**
	 * 月
	 */
	MONTH("{MM}"),

	/**
	 * 日
	 */
	DAY("{dd}"),

	/**
	 * 时
	 */
	HOUR("{HH}"),

	/**
	 * 分
	 */
	MINUTE("{mm}"),

	/**
	 * 秒
	 */
	SECOND("{ss}");

	private final String expr;

	/**
	 * @param expr
	 */
	private SeqTimeExpr(String expr) {
		this.expr = expr;
	}

	/**
	 * @return the expr
	 */
	public String getExpr() {
		return expr;
	}
	
	
	
}

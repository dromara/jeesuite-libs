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
package org.dromara.mendmix.gateway.exception;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.dromara.mendmix.common.GlobalConstants;
import org.dromara.mendmix.common.MendmixBaseException;
import org.dromara.mendmix.common.model.WrapperResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class ReactiveGlobalExceptionHandler {


	@ExceptionHandler(Exception.class)
	@ResponseBody
	public WrapperResponse<?> exceptionHandler(ServerHttpRequest request, ServerHttpResponse response,Exception e) {

		WrapperResponse<?> resp = new WrapperResponse<>();
		
		Throwable throwable = getActualThrowable(e);
		if(throwable instanceof Exception) {
			e = (Exception) throwable;
		}else {
			e.printStackTrace();
			e = new MendmixBaseException(500, e.getMessage());
		}
		if (e instanceof MendmixBaseException) {
			MendmixBaseException e1 = (MendmixBaseException) e;
			resp.setCode(e1.getCode());
			resp.setMsg(e1.getMessage());
		} else if(e instanceof MethodArgumentNotValidException){
			resp.setCode(400);
			List<ObjectError> errors = ((MethodArgumentNotValidException)e).getBindingResult().getAllErrors();
			
			String fieldName;
			StringBuilder fieldNames = new StringBuilder();
			for (ObjectError error : errors) {
				fieldName = parseFieldName(error);
				fieldNames.append(fieldName).append(",");
			}
			resp.setBizCode("error.parameter.notValid");
			resp.setMsg("参数错误["+fieldNames.toString()+"]");
		} else {
			Throwable parent = e.getCause();
			if (parent instanceof IllegalStateException) {
				resp.setCode(501);
				resp.setMsg(e.getMessage());
			} else {
				resp.setCode(500);
				resp.setMsg("系统繁忙");
			}
		}
		return resp;
	}
	
	private Throwable getActualThrowable(Throwable e){
		Throwable cause = e;
		while(cause.getCause() != null){
			cause = cause.getCause();
		}
		return cause;
	}
	
	private String parseFieldName(ObjectError error) {
		String[] codes = error.getCodes();
		if(codes.length >= 2) {
			return StringUtils.split(codes[1], GlobalConstants.DOT)[1];
		}
		if(codes.length >= 1) {
			return StringUtils.split(codes[0], GlobalConstants.DOT)[2];
		}
		return error.getCode();
	}
}
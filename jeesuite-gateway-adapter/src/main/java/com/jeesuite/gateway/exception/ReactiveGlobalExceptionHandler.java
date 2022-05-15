package com.jeesuite.gateway.exception;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import com.jeesuite.common.GlobalConstants;
import com.jeesuite.common.JeesuiteBaseException;
import com.jeesuite.common.model.WrapperResponse;
import com.jeesuite.logging.integrate.ActionLogCollector;

@ControllerAdvice
public class ReactiveGlobalExceptionHandler {


	@ExceptionHandler(Exception.class)
	@ResponseBody
	public WrapperResponse<?> exceptionHandler(ServerHttpRequest request, ServerHttpResponse response,Exception e) {

		WrapperResponse<?> resp = new WrapperResponse<>();
		
		e = (Exception) getActualThrowable(e);
		if (e instanceof JeesuiteBaseException) {
			JeesuiteBaseException e1 = (JeesuiteBaseException) e;
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
		//
		ActionLogCollector.onResponseEnd(response.getRawStatusCode(), e);
		
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
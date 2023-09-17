/*
 * Copyright 2016-2020 www.jeesuite.com.
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
package com.mendmix.amqp.adapter.rocketmq;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mendmix.amqp.MQContext;
import com.mendmix.amqp.MQMessage;
import com.mendmix.amqp.MessageHeaderNames;
import com.mendmix.amqp.adapter.AbstractProducer;
import com.mendmix.common.util.ResourceUtils;

/**
 * 
 * <br>
 * Class Name   : RocketProducerAdapter
 *
 * @author jiangwei
 * @version 1.0.0
 * @date 2017年7月11日
 */
public class RocketProducerAdapter extends AbstractProducer {

	private final Logger logger = LoggerFactory.getLogger("com.mendmix.amqp");
	
	private String namesrvAddr;
	
	private DefaultMQProducer producer;
	
	/**
	 * @param groupName
	 * @param namesrvAddr
	 */
	public RocketProducerAdapter(MQContext context) {
		super(context);
		this.namesrvAddr = ResourceUtils.getAndValidateProperty(context.getInstanceGroup() + ".amqp.rocketmq[namesrvAddr]");		
	}

	@Override
	public void start() throws Exception{
		super.start();
		producer = new DefaultMQProducer(context.getGroupName());
		producer.setNamesrvAddr(namesrvAddr);
		producer.start();
	}
	
	//MessageQueueSelector
	@Override
	public String sendMessage(MQMessage message,boolean async) {
		Message _message = new Message(message.getTopic(), message.getTag(), message.getBizKey(), message.bodyAsBytes());
	
		if(StringUtils.isNotBlank(message.getProduceBy())){
			_message.putUserProperty(MessageHeaderNames.produceBy.name(), message.getProduceBy());
		}
		if(StringUtils.isNotBlank(message.getRequestId())){
			_message.putUserProperty(MessageHeaderNames.requestId.name(), message.getRequestId());
		}
		if(StringUtils.isNotBlank(message.getTenantId())){
			_message.putUserProperty(MessageHeaderNames.tenantId.name(), message.getTenantId());
		}
		if(StringUtils.isNotBlank(message.getStatusCheckUrl())){
			_message.putUserProperty(MessageHeaderNames.statusCheckUrl.name(), message.getStatusCheckUrl());
		}

		try {
			if(async){
				producer.send(_message, new SendCallback() {
					@Override
					public void onSuccess(SendResult sendResult) {
						if(logger.isDebugEnabled())logger.debug("MQ_SEND_SUCCESS:{} -> msgId:{},status:{},offset:{}",message.getTopic(),sendResult.getMsgId(),sendResult.getSendStatus().name(),sendResult.getQueueOffset());
						message.onProducerFinished(sendResult.getMsgId(),0,sendResult.getQueueOffset());
						handleSuccess(message);
					}
					
					@Override
					public void onException(Throwable e) {
						handleError(message, e);
						logger.warn("MQ_SEND_FAIL:"+message.getTopic(),e);
					}
				});
			}else{
				SendResult sendResult = producer.send(_message);	
				message.onProducerFinished(sendResult.getMsgId(),0,sendResult.getQueueOffset());
				if(sendResult.getSendStatus() == SendStatus.SEND_OK) {
					handleSuccess(message);
				}else {
					handleError(message, new MQClientException(0, sendResult.getSendStatus().name()));
				}
			}
		} catch (Exception e) {
			handleError(message, e);
			throw new RuntimeException(e);
		}
		
		return null;
	}

	@Override
	public void shutdown() {
		super.shutdown();
		producer.shutdown();
	}

	

}

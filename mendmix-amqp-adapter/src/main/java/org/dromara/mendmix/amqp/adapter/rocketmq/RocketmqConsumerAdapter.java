/*
 * Copyright 2016-2020 dromara.org.
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
package org.dromara.mendmix.amqp.adapter.rocketmq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dromara.mendmix.amqp.MQConsumer;
import org.dromara.mendmix.amqp.MQContext;
import org.dromara.mendmix.amqp.MQContext.ActionType;
import org.dromara.mendmix.amqp.MQMessage;
import org.dromara.mendmix.amqp.MessageHandler;
import org.dromara.mendmix.common.ThreadLocalContext;
import org.dromara.mendmix.common.util.ResourceUtils;

/**
 * 
 * <br>
 * Class Name   : RocketmqConsumerAdapter
 *
 * @author jiangwei
 * @version 1.0.0
 * @date 2017年7月11日
 */
public class RocketmqConsumerAdapter implements MQConsumer {
	
	private final static Logger logger = LoggerFactory.getLogger("org.dromara.mendmix.amqp.adapter");
	
	private String namesrvAddr;
	
	private Map<String, MessageHandler> messageHandlers = new HashMap<>(); 
	
	private DefaultMQPushConsumer consumer;

	private MQContext context;
	
	/**
	 * @param groupName
	 * @param namesrvAddr
	 * @param messageHandlers
	 */
	public RocketmqConsumerAdapter(MQContext context,Map<String, MessageHandler> messageHandlers) {
        this.context = context;
        this.messageHandlers = messageHandlers;
		this.namesrvAddr = ResourceUtils.getAndValidateProperty(context.getInstance() + ".amqp.rocketmq[namesrvAddr]");
	}


	/**
	 * @param namesrvAddr the namesrvAddr to set
	 */
	public void setNamesrvAddr(String namesrvAddr) {
		this.namesrvAddr = namesrvAddr;
	}

	@Override
	public void start() throws Exception {

		int consumeThreads = context.getMaxProcessThreads();
		String groupName = context.getGroupName();
		consumer = new DefaultMQPushConsumer(groupName);
		consumer.setNamesrvAddr(namesrvAddr);
		consumer.setConsumeMessageBatchMaxSize(1); //每次拉取一条
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(consumeThreads);
        consumer.setConsumeThreadMax(consumeThreads);
        consumer.setPullThresholdForQueue(1000);
        consumer.setConsumeConcurrentlyMaxSpan(500);
		for (String topic : messageHandlers.keySet()) {
			consumer.subscribe(topic, "*");
		}
		consumer.registerMessageListener(new CustomMessageListener());
		consumer.start();
	}


	
	private class CustomMessageListener implements MessageListenerConcurrently{

		@Override
		public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext consumeContext) {
			if(msgs.isEmpty())return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			MessageExt msg = msgs.get(0); //每次只拉取一条
			if(!messageHandlers.containsKey(msg.getTopic())) {
				logger.warn("not messageHandler found for:{}",msg.getTopic());
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			if(context.getConsumeMaxRetryTimes() > 0 && msg.getReconsumeTimes() > context.getConsumeMaxRetryTimes()) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			if(context.getConsumeMaxInterval() > 0 && msg.getReconsumeTimes() > 1 && System.currentTimeMillis() - msg.getBornTimestamp() > context.getConsumeMaxInterval()) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			MQMessage message = new MQMessage(msg.getTopic(),msg.getTags(),msg.getKeys(), msg.getBody());
			message.setHeaders(msg.getProperties());
            //
			if(!context.matchedOnFilter(message)) {
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
			//用户上下文
			message.setUserContextOnConsume();
			try {
				messageHandlers.get(message.getTopic()).process(message);
				if(logger.isDebugEnabled())logger.debug("MQ_MESSAGE_CONSUME_SUCCESS ->message:{}",message);
				//
				MQContext.processMessageLog(context,message, ActionType.sub,null);
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			} catch (Exception e) {
				logger.error(String.format("MQ_MESSAGE_CONSUME_ERROR ->message:%s",message.toString()),e);
				//
				MQContext.processMessageLog(context,message,ActionType.sub, e);
				return ConsumeConcurrentlyStatus.RECONSUME_LATER;
			}finally{
				ThreadLocalContext.unset();
			}				
		}
		
	}

	@Override
	public void shutdown() {
		consumer.shutdown();
	}
	
}

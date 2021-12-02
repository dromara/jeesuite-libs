package com.jeesuite.amqp.rocketmq;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jeesuite.amqp.AbstractProducer;
import com.jeesuite.amqp.MQContext;
import com.jeesuite.amqp.MQMessage;
import com.jeesuite.amqp.MessageHeaderNames;
import com.jeesuite.common.util.ResourceUtils;

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

	private final Logger logger = LoggerFactory.getLogger("com.jeesuite.amqp");
	
	private String groupName;
	private String namesrvAddr;
	
	private DefaultMQProducer producer;
	
	/**
	 * @param groupName
	 * @param namesrvAddr
	 */
	public RocketProducerAdapter() {
		this.groupName = MQContext.getGroupName();
		this.namesrvAddr = ResourceUtils.getAndValidateProperty("jeesuite.amqp.rocketmq.namesrvAddr");		
	}

	@Override
	public void start() throws Exception{
		super.start();
		producer = new DefaultMQProducer(groupName);
		producer.setNamesrvAddr(namesrvAddr);
		producer.start();
	}
	
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
		if(StringUtils.isNotBlank(message.getCheckUrl())){
			_message.putUserProperty(MessageHeaderNames.checkUrl.name(), message.getProduceBy());
		}
		if(StringUtils.isNotBlank(message.getTransactionId())){
			_message.putUserProperty(MessageHeaderNames.transactionId.name(), message.getTransactionId());
		}

		try {
			if(async){
				producer.send(_message, new SendCallback() {
					@Override
					public void onSuccess(SendResult sendResult) {
						if(logger.isDebugEnabled())logger.debug("MQ_SEND_SUCCESS:{} -> msgId:{},status:{},offset:{}",message.getTopic(),sendResult.getMsgId(),sendResult.getSendStatus().name(),sendResult.getQueueOffset());
						message.setMsgId(sendResult.getMsgId());
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
				message.setMsgId(sendResult.getMsgId());
				if(sendResult.getSendStatus() == SendStatus.SEND_OK) {
					handleSuccess(message);
				}else {
					handleError(message, new MQClientException(0, sendResult.getSendStatus().name()));
				}
			}
		} catch (Exception e) {
			handleError(message, e);
			logger.warn("MQ_SEND_FAIL:"+message.getTopic(),e);
		}
		
		return null;
	}

	@Override
	public void shutdown() {
		super.shutdown();
		producer.shutdown();
	}

	

}

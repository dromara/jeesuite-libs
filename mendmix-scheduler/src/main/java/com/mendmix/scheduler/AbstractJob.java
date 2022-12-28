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
package com.mendmix.scheduler;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.mendmix.common.ThreadLocalContext;
import com.mendmix.common.constants.ContextKeys;
import com.mendmix.common.util.ResourceUtils;
import com.mendmix.logging.actionlog.ActionLogCollector;
import com.mendmix.scheduler.model.JobConfig;
import com.mendmix.spring.InstanceFactory;

/**
 * 类    名：AbstractJob.java<br />
 *   
 * 功能描述：定时任务基类  <br />
 *  
 * 创建日期：2012-2-13上午11:04:13  <br />   
 * 
 * 版本信息：v 1.0<br />
 * 
 * 作    者：<a href="mailto:vakinge@gmail.com">vakin jiang</a><br />
 * 
 * 修改记录： <br />
 * 修 改 者    修改日期     文件版本   修改说明    
 */
public abstract class AbstractJob implements DisposableBean{
    private static final Logger logger = LoggerFactory.getLogger("com.mendmix.scheduler");
    
    private static boolean loggingEnabled = ResourceUtils.getBoolean("mendmix.task.logging.enabled", true);

  //默认允许多个节点时间误差
    private static final long DEFAULT_ALLOW_DEVIATION = 1000 * 60 * 15;
    
    protected String group;
    protected String jobName;
    protected String cronExpr;
    
    protected String triggerName;
    private Scheduler scheduler;
    private CronTriggerImpl cronTrigger;
    private TriggerKey triggerKey;
    
    
    private boolean executeOnStarted;//启动是否立即执行
    
    private AtomicBoolean runing = new AtomicBoolean(false);
    private AtomicInteger runCount = new AtomicInteger(0);

	public void setGroup(String group) {
		this.group = StringUtils.trimToNull(group);
	}
	
	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = StringUtils.trimToNull(jobName);
	}

	public String getCronExpr() {
		return cronExpr;
	}

	public void setCronExpr(String cronExpr) {
		this.cronExpr = StringUtils.trimToNull(cronExpr);
	}

	public boolean isExecuteOnStarted() {
		return executeOnStarted;
	}

	public void setExecuteOnStarted(boolean executeOnStarted) {
		this.executeOnStarted = executeOnStarted;
	}

	public String getTriggerName() {
		return triggerName;
	}

	protected Scheduler getScheduler() {
        if (scheduler == null)
            scheduler = InstanceFactory.getInstance(Scheduler.class);
        return scheduler;
    }

	public void execute() {
		if(runing.get())return;
		JobConfig schConf = JobContext.getContext().getRegistry().getConf(jobName,false);
		if (currentNodeIgnore(schConf))
			return;

		runing.set(true);
		Date beginTime = null;
		Exception exception = null;
		try {
			// 更新状态
			beginTime = getPreviousFireTime();
			JobContext.getContext().getRegistry().setRuning(jobName, beginTime);
			logger.debug("MENDMIX-TRACE-LOGGGING-->> Job_{} at node[{}] execute begin...", jobName, JobContext.getContext().getNodeId());
			
			if(loggingEnabled && logging()) {
				ActionLogCollector.onSystemBackendTaskStart(jobName, jobName);
			}
			//
			if(ignoreTenant()) {
				ThreadLocalContext.set(ContextKeys.IGNORE_TENENT_ID, true);
			}
			// 执行
			doJob(JobContext.getContext());
			logger.debug("MENDMIX-TRACE-LOGGGING-->> Job_{} at node[{}] execute finish", jobName, JobContext.getContext().getNodeId());
		} catch (Exception e) {
			//重试
			if(retries() > 0)JobContext.getContext().getRetryProcessor().submit(this, retries());
			logger.error("Job_" + jobName + " execute error", e);
			exception = e;
		}finally {			
			runing.set(false);
			//执行次数累加1
			runCount.incrementAndGet();
			Date nextFireTime = getTrigger().getNextFireTime();
			JobContext.getContext().getRegistry().setStoping(jobName, nextFireTime,exception);
			//运行日志持久化
			if(JobContext.getContext().getPersistHandler() != null){
				try {
					JobContext.getContext().getPersistHandler().saveLog(schConf, exception);
				} catch (Exception e2) {
					logger.warn("MENDMIX-TRACE-LOGGGING-->> JobLogPersistHandler run error",e2);
				}
			}
			// 重置cronTrigger，重新获取才会更新previousFireTime，nextFireTime
			cronTrigger = null;
			//
			if(loggingEnabled && logging())ActionLogCollector.onSystemBackendTaskEnd(exception);
		}
	}

    
	protected Date getPreviousFireTime(){
    	return getTrigger().getPreviousFireTime() == null ? new Date() : getTrigger().getPreviousFireTime();
    }
  
    
    protected boolean currentNodeIgnore(JobConfig schConf) {
    	if(parallelEnabled())return false;
        try {
            if (!schConf.isActive()) {
            	logger.debug("MENDMIX-TRACE-LOGGGING-->> Job_{} 已禁用,终止执行", jobName);
                return true;
            }
            
            //执行间隔（秒）
           // long interval = getJobFireInterval();
            long currentTimes = Calendar.getInstance().getTime().getTime();
            
            if(schConf.getNextFireTime() != null){
            	//下次执行时间 < 当前时间强制执行
            	if(currentTimes - schConf.getNextFireTime().getTime() > DEFAULT_ALLOW_DEVIATION){
                	logger.debug("MENDMIX-TRACE-LOGGGING-->> Job_{} NextFireTime[{}] before currentTime[{}],re-join-execute task ",jobName,currentTimes,schConf.getNextFireTime().getTime());
                	return false;
                }
            	//如果多个节点做了时间同步，那么误差应该为0才触发任务执行，但是考虑一些误差因素，可以做一个误差容错
//            	if(schConf.getLastFireTime() != null){            		
//            		long deviation = Math.abs(currentTimes - schConf.getLastFireTime().getTime() - interval);
//            		if(interval > 0 && deviation > DEFAULT_ALLOW_DEVIATION){
//            			logger.info("Job_{} interval:{},currentTimes:{},expect tiggertime:{}", jobName,interval,currentTimes, schConf.getLastFireTime().getTime());
//            			return true;
//            		}
//            	}
            }
			
            
          //如果执行节点不为空,且不等于当前节点
            if(StringUtils.isNotBlank(schConf.getCurrentNodeId()) ){            	
            	if(!JobContext.getContext().getNodeId().equals(schConf.getCurrentNodeId())){
            		logger.debug("MENDMIX-TRACE-LOGGGING-->> Job_{} 指定执行节点:{}，不匹配当前节点:{}", jobName,schConf.getCurrentNodeId(),JobContext.getContext().getNodeId());
            		return true;
            	}
            	//如果分配了节点，则可以保证本节点不会重复执行则不需要判断runing状态
            }else{  
            	if (schConf.isRunning()) {
            		//如果某个节点开始了任务但是没有正常结束导致没有更新任务执行状态
            		logger.info("MENDMIX-TRACE-LOGGGING-->> Job_{} 其他节点[{}]正在执行,终止当前执行", schConf.getCurrentNodeId(),jobName);
            		return true;
            	}
            }

            
            this.cronExpr = schConf.getCronExpr();
        } catch (Exception e) {
        	e.printStackTrace();
        }
        return false;
    }
    
    public void resetTriggerCronExpr(String newCronExpr) {  
        try {   
        	if(getTrigger() == null)return;
            String originConExpression = getTrigger().getCronExpression();  
            //判断任务时间是否更新过  
            if (!originConExpression.equalsIgnoreCase(newCronExpr)) {  
            	getTrigger().setCronExpression(newCronExpr);  
                getScheduler().rescheduleJob(triggerKey, getTrigger()); 
                getScheduler().resumeTrigger(triggerKey);
                logger.info("MENDMIX-TRACE-LOGGGING-->> Job_{} CronExpression changed, origin:{},current:{}",jobName,originConExpression,newCronExpr);
            }  
        } catch (Exception e) {
        	logger.error("checkConExprChange error",e);
        }  
         
    }  
    
    
    private CronTriggerImpl getTrigger() {
    	try {
    		if(this.cronTrigger == null){   
    			if(getScheduler() == null)return null;
        		Trigger trigger = getScheduler().getTrigger(triggerKey);
        		this.cronTrigger = (CronTriggerImpl)trigger;
        	}
		} catch (SchedulerException e) {
			logger.error("Job_"+jobName+" Invoke getTrigger error",e);
		}
        return cronTrigger;
    }
    
    

    @Override
	public void destroy() throws Exception {
    	JobContext.getContext().getRegistry().unregister(jobName);
    }

	public void init()  {
		
		triggerName = jobName + "Trigger";
		
		triggerKey = new TriggerKey(triggerName, group);
		
		JobConfig jobConfg = new JobConfig(group,jobName,cronExpr);
		
		//从持久化配置合并
		if(JobContext.getContext().getPersistHandler() != null){
			JobConfig persistConfig = null;
			try {persistConfig = JobContext.getContext().getPersistHandler().get(jobConfg.getJobName());} catch (Exception e) {}
			if(persistConfig != null) {
				jobConfg.setActive(persistConfig.isActive());
				jobConfg.setCronExpr(persistConfig.getCronExpr());
			}
		}
    	
        JobContext.getContext().getRegistry().register(jobConfg);
        
        logger.info("MENDMIX-TRACE-LOGGGING-->> Initialized Job_{} OK!!", jobName);
    }
	
	public void afterInitialized()  {
		//启动重试任务
		if(retries() > 0){
			JobContext.getContext().startRetryProcessor();
		}
		//这里不能提前去拿下一次更新时间，否则真正执行后下次执行时间不更新
//		if(executeOnStarted)return;
//		JobConfig conf = JobContext.getContext().getRegistry().getConf(jobName,false);
//		Date nextFireTime = getNextFireTime();
//		if(nextFireTime != null){			
//			conf.setNextFireTime(nextFireTime);
//			JobContext.getContext().getRegistry().updateJobConfig(conf);
//		}
		
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((jobName == null) ? 0 : jobName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractJob other = (AbstractJob) obj;
		if (jobName == null) {
			if (other.jobName != null)
				return false;
		} else if (!jobName.equals(other.jobName))
			return false;
		return true;
	}
	
	/**
	 * 重试次数
	 * @return
	 */
	public int retries() {
		return 0;
	}
	
	public boolean logging() {
		return  true;
	}
	
	public boolean ignoreTenant() {
		return  true;
	}
	
	/**
	 * 是否开启并行处理
	 * @return
	 */
	public boolean parallelEnabled() {
		return false;
	}

	public abstract void doJob(JobContext context) throws Exception;

}

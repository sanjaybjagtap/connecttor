package com.ffusion.ffs.bpw.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class OSGIUtil implements ApplicationContextAware {
	
	private static ApplicationContext context;
	private static final Logger logger = LoggerFactory.getLogger(com.ffusion.ffs.bpw.handler.OSGIUtil.class);
	
	public static Object getBean( Class type ) throws
            BeansException {
		
		Object bean = null;
		
		try {
			
			bean = context.getBean(type);
			
		} catch (NoSuchBeanDefinitionException noSuchBeanDefinitionException) {
			logger.debug("No such bean definition "+type.getClass().toString());			
			throw noSuchBeanDefinitionException;			
		}
		
		return bean;
		
	}
	
	public static Object getBean( String beanName ) throws
            BeansException {
		
		Object bean = null;
		
		try {
			
			bean = context.getBean(beanName);
			
		} catch (NoSuchBeanDefinitionException noSuchBeanDefinitionException) {
			logger.debug("No such bean definition "+beanName);			
			throw noSuchBeanDefinitionException;			
		}
		
		return bean;
		
	}

	@Override
	public void setApplicationContext(ApplicationContext appContext)
			throws BeansException {
		context = appContext;		
	}

}

/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * 系统启动时候的监听器
 * @author Phillip Webb
 * @author Dave Syer
 */
public interface SpringApplicationRunListener {

	/**
	 * 在Spring刚启动的时候调用。
	 */
	void started();

	/**
	 * 在环境准备完毕，但是在应用上下文还没有创建的时候回调用一次。
	 * @param environment the environment
	 */
	void environmentPrepared(ConfigurableEnvironment environment);

	/**
	 * 在应用上下文创建了，但还没有加载资源的时候调用一次。
	 * @param context the application context
	 */
	void contextPrepared(ConfigurableApplicationContext context);

	/**
	 * 在应用上下文加载了，但是还没有加载bean的时候。
	 * @param context the application context
	 */
	void contextLoaded(ConfigurableApplicationContext context);

	/**
	 * 在应用run方法完成之前执行一次。
	 */
	void finished(ConfigurableApplicationContext context, Throwable exception);

}

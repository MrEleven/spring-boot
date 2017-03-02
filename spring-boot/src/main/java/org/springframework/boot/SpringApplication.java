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

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.diagnostics.FailureAnalyzers;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * 这个类主要是用来启动Spring应用的。
 * 默认的启动步骤如下：
 * 1. 根据classpath中存在的类创建一个ApplicationContext实例。
 * 2. 注册一个命令行属性源用于暴露命令行参数作为属性。
 * 3. 刷新应用上下文，家在所有单例模式的Bean
 * 4. 触发CommandLineRunner的Bean
 *
 * 在大部分情况下，我们可以通过直接调用静态方法run(Object, String[])来启动我们的应用：(PS:我就是经常这么用的)
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 * // ... Bean definitions
 *
 * public static void main(String[] args) throws Exception {
 *   SpringApplication.run(MyApplication.class, args);
 * }
 * </pre>
 *
 * 为了一些更高级的配置，我们可以创建一个SpringApplication实例，并且在启动它之前进行配置
 * <p>
 *
 * <pre class="code">
 * public static void main(String[] args) throws Exception {
 *   SpringApplication app = new SpringApplication(MyApplication.class);
 *   // ... customize app settings here
 *   app.run(args)
 * }
 * </pre>
 * SpringApplication可以从各种源读取Bean信息，一个十分推荐的做法是提供一个被@Configuration注解的类来
 * 启动应用，然而任何下面源都可以被使用。
 *
 * 1. 被AnnotatedBeanDefinitionReader注解的Java类
 * 2. 被XmlBeanDefinitionReader加载的XML资源，或者被GroovyBeanDefinitionReader加载的groovy脚本。
 * 3. 被ClassPathBeanDefinitionScanner扫描的Java包。
 * 4. 一个作为属性被加载的代表类名，资源处理器或者包名的字符串。如果找不到字符串代表的类和资源，这个字符串会被当作是一个包名。
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @see #run(Object, String[])
 * @see #run(Object[], String[])
 * @see #SpringApplication(Object...)
 */
public class SpringApplication {

	/**
     * 非web环境默认的应用上下文
	 */
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
     * web环境下默认的应用上下文
	 */
	public static final String DEFAULT_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext";

    /* 通过这两个类是否在classPath下同时存在来判断当前环境是web环境，还是非web环境 */
	private static final String[] WEB_ENVIRONMENT_CLASSES = { "javax.servlet.Servlet",
			"org.springframework.web.context.ConfigurableWebApplicationContext" };

	/**
	 * 默认Banner地址
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
     * 默认的Banner地址的属性名称
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String CONFIGURABLE_WEB_ENVIRONMENT_CLASS = "org.springframework.web.context.ConfigurableWebEnvironment";

    /* java.awt.headless模式 的系统参数名称，这个模式一般在没有装GUI的环境下才会启用，主要用来处理图形操作 */
	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

    /* Servlet环境类名集合，一般用来自动发现Servlet */
	private static final Set<String> SERVLET_ENVIRONMENT_SOURCE_NAMES;

	static {
		Set<String> names = new HashSet<String>();
		names.add(StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME);
		names.add(StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME);
		names.add(StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME);
		SERVLET_ENVIRONMENT_SOURCE_NAMES = Collections.unmodifiableSet(names);
	}

	private static final Log logger = LogFactory.getLog(SpringApplication.class);

    /* 保存传进来的source，虽然目前不知道source干嘛的 */
	private final Set<Object> sources = new LinkedHashSet<Object>();
    /* 启动类 */
	private Class<?> mainApplicationClass;
    /* 默认将Banner输出到控制台 */
	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;
    /* banner，没什么软用，只是用来装逼的 */
	private Banner banner;
    /* 什么鬼，装逼用的 */
	private boolean printedCustomBannerViaDeprecatedMethod;
    /* 资源加载器 */
	private ResourceLoader resourceLoader;
    /* Bean名称生成器 */
	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private Class<? extends ConfigurableApplicationContext> applicationContextClass;

    /* 是否是web环境 */
	private boolean webEnvironment;
    /* 是否是headless环境 */
	private boolean headless = true;
    /* 默认启用Shutdown的钩子 */
	private boolean registerShutdownHook = true;
    /* 初始化起集合 */
	private List<ApplicationContextInitializer<?>> initializers;
    /* 应用监听器集合 */
	private List<ApplicationListener<?>> listeners;
    /* 默认属性 */
	private Map<String, Object> defaultProperties;

	private Set<String> additionalProfiles = new HashSet<String>();

	/**
     * 构造器，创建一个实例，这个实例的上下文会从指定的源加载相应的bean。在调用run方法之前可以做一些自定义配置
	 */
	public SpringApplication(Object... sources) {
		initialize(sources);
	}

	/**
	 * 跟上面没有什么区别，只是可以传一个资源加载起进来。
	 */
	public SpringApplication(ResourceLoader resourceLoader, Object... sources) {
		this.resourceLoader = resourceLoader;
		initialize(sources);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void initialize(Object[] sources) {
		if (sources != null && sources.length > 0) {
			this.sources.addAll(Arrays.asList(sources));
		}
		this.webEnvironment = deduceWebEnvironment();
		setInitializers((Collection) getSpringFactoriesInstances(
				ApplicationContextInitializer.class));
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		this.mainApplicationClass = deduceMainApplicationClass();
	}

    /* 检测是否是web环境 */
	private boolean deduceWebEnvironment() {
		for (String className : WEB_ENVIRONMENT_CLASSES) {
			if (!ClassUtils.isPresent(className, null)) {
				return false;
			}
		}
		return true;
	}

    /**
     * 获取MainApplicationClass
     * @return
     */
	private Class<?> deduceMainApplicationClass() {
		try {
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
			for (StackTraceElement stackTraceElement : stackTrace) {
				if ("main".equals(stackTraceElement.getMethodName())) {
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue
		}
		return null;
	}

	/**
	 * 启动一个Spring应用。创建并刷新上下文
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		ConfigurableApplicationContext context = null;
		FailureAnalyzers analyzers = null;
        // 将系统是否是headless模式配置进系统环境变量
		configureHeadlessProperty();
        // 获取监听器
		SpringApplicationRunListeners listeners = getRunListeners(args);
		listeners.started();
		try {
            // 保存启动参数
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(
					args);
            // 创建环境信息，一些property和Profile
			ConfigurableEnvironment environment = prepareEnvironment(listeners,
					applicationArguments);
            // 打印Banner信息
			Banner printedBanner = printBanner(environment);
            // 创建应用上下文
			context = createApplicationContext();
            // 创建一些错误分析器
			analyzers = new FailureAnalyzers(context);
            // 执行一些准备操作
			prepareContext(context, environment, listeners, applicationArguments,
					printedBanner);
            // 加载各种Bean
			refreshContext(context);
            // 执行一些refresh之后的操作，虽然看不明白在干啥
			afterRefresh(context, applicationArguments);
            // 通知监听器refresh完成
			listeners.finished(context, null);
			stopWatch.stop();
            // 打出初始化日志
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass)
						.logStarted(getApplicationLog(), stopWatch);
			}
			return context;
		}
		catch (Throwable ex) {
            // 启动失败，打出错误日志
			handleRunFailure(context, listeners, analyzers, ex);
			throw new IllegalStateException(ex);
		}
	}

    /**
     * 创建环境信息，所谓的环境就是指profile和properties
     * @param listeners
     * @param applicationArguments
     * @return
     */
	private ConfigurableEnvironment prepareEnvironment(
			SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		// Create and configure the environment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		listeners.environmentPrepared(environment);
		if (isWebEnvironment(environment) && !this.webEnvironment) {
			environment = convertToStandardEnvironment(environment);
		}
		return environment;
	}

	private void prepareContext(ConfigurableApplicationContext context,
			ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments, Banner printedBanner) {
        // 设置环境
		context.setEnvironment(environment);
        // 做一些配置Context之前的工作
		postProcessApplicationContext(context);
        // 执行一些初始化起的工作
		applyInitializers(context);
        // 执行监听器的钩子
		listeners.contextPrepared(context);
        // 打印一些启动信息
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}

        // 搞不明白为什么要把Banner和启动参数保存下来，哪里还会用到？
		context.getBeanFactory().registerSingleton("springApplicationArguments",
				applicationArguments);
		if (printedBanner != null) {
			context.getBeanFactory().registerSingleton("springBootBanner", printedBanner);
		}

        // 加载指定的Source变成Bean注册到上下文
		Set<Object> sources = getSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		load(context, sources.toArray(new Object[sources.size()]));
		listeners.contextLoaded(context);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		refresh(context);
		if (this.registerShutdownHook) {
			try {
				context.registerShutdownHook();
			}
			catch (AccessControlException ex) {
				// Not allowed in some environments.
			}
		}
	}

	/* 这个方法比较傻逼，如果没有在系统中指定是否是headless模式就赋予默认值 */
	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, System.getProperty(
				SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

    /* 获取所有spring.factories中配置的SpringApplicationRunListener，并组合成一个SpringApplicationRunListeners对象 */
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		return new SpringApplicationRunListeners(logger, getSpringFactoriesInstances(
				SpringApplicationRunListener.class, types, this, args));
	}

    /**
     * 获取spring.factory中指定类型的所有类并且实例化，然后根据Order排序。
     * @param type
     * @param <T>
     * @return
     */
	private <T> Collection<? extends T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

    /**
     * 这个方法比较屌，获取spring.factory中指定类型的所有类并且实例化，然后根据Order排序。
     * @param type
     * @param parameterTypes
     * @param args
     * @param <T>
     * @return
     */
	private <T> Collection<? extends T> getSpringFactoriesInstances(Class<T> type,
			Class<?>[] parameterTypes, Object... args) {
        // 获取当前线程的类加载器
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		// Use names and ensure unique to protect against duplicates
		Set<String> names = new LinkedHashSet<String>(
				SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes,
				classLoader, args, names);
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}

    /**
     * 将传进来的names全部实例话，返回实例列表
     * @param type
     * @param parameterTypes
     * @param classLoader
     * @param args
     * @param names
     * @param <T>
     * @return
     */
	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type,
			Class<?>[] parameterTypes, ClassLoader classLoader, Object[] args,
			Set<String> names) {
		List<T> instances = new ArrayList<T>(names.size());
		for (String name : names) {
			try {
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				Assert.isAssignable(type, instanceClass);
				Constructor<?> constructor = instanceClass
						.getDeclaredConstructor(parameterTypes);
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException(
						"Cannot instantiate " + type + " : " + name, ex);
			}
		}
		return instances;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			return this.environment;
		}
		if (this.webEnvironment) {
			return new StandardServletEnvironment();
		}
		return new StandardEnvironment();
	}

	/**
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment,
			String[] args) {
		configurePropertySources(environment, args);
		configureProfiles(environment, args);
	}

	private boolean isWebEnvironment(ConfigurableEnvironment environment) {
		try {
			Class<?> webEnvironmentClass = ClassUtils
					.forName(CONFIGURABLE_WEB_ENVIRONMENT_CLASS, getClassLoader());
			return (webEnvironmentClass.isInstance(environment));
		}
		catch (Throwable ex) {
			return false;
		}
	}

	private ConfigurableEnvironment convertToStandardEnvironment(
			ConfigurableEnvironment environment) {
		StandardEnvironment result = new StandardEnvironment();
		removeAllPropertySources(result.getPropertySources());
		result.setActiveProfiles(environment.getActiveProfiles());
		for (PropertySource<?> propertySource : environment.getPropertySources()) {
			if (!SERVLET_ENVIRONMENT_SOURCE_NAMES.contains(propertySource.getName())) {
				result.getPropertySources().addLast(propertySource);
			}
		}
		return result;
	}

	private void removeAllPropertySources(MutablePropertySources propertySources) {
		Set<String> names = new HashSet<String>();
		for (PropertySource<?> propertySource : propertySources) {
			names.add(propertySource.getName());
		}
		for (String name : names) {
			propertySources.remove(name);
		}
	}

	/**
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment,
			String[] args) {
		MutablePropertySources sources = environment.getPropertySources();
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(
					new MapPropertySource("defaultProperties", this.defaultProperties));
		}
		if (this.addCommandLineProperties && args.length > 0) {
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(new SimpleCommandLinePropertySource(
						name + "-" + args.hashCode(), args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		environment.getActiveProfiles(); // ensure they are initialized
		// But these ones should go first (last wins in a property key clash)
		Set<String> profiles = new LinkedHashSet<String>(this.additionalProfiles);
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(profiles.toArray(new String[profiles.size()]));
	}

    /* 打印Banner */
	private Banner printBanner(ConfigurableEnvironment environment) {
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		if (printBannerViaDeprecatedMethod(environment)) {
			return null;
		}
		ResourceLoader resourceLoader = this.resourceLoader != null ? this.resourceLoader
				: new DefaultResourceLoader(getClassLoader());
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(
				resourceLoader, this.banner);
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	private boolean printBannerViaDeprecatedMethod(Environment environment) {
		this.printedCustomBannerViaDeprecatedMethod = true;
		printBanner(environment);
		return this.printedCustomBannerViaDeprecatedMethod;
	}

	/**
	 * Print a custom banner message to the console, optionally extracting its location or
	 * content from the Environment (banner.location and banner.charset). The defaults are
	 * banner.location=classpath:banner.txt, banner.charset=UTF-8. If the banner file does
	 * not exist or cannot be printed, a simple default is created.
	 * @param environment the environment
	 * @see #setBannerMode
	 * @deprecated as of 1.4 in favor of @{@link #setBanner(Banner)}
	 */
	@Deprecated
	protected void printBanner(Environment environment) {
		this.printedCustomBannerViaDeprecatedMethod = false;
	}

	/**
     * 创建ApplicationContext实例。
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		Class<?> contextClass = this.applicationContextClass;
		if (contextClass == null) {
			try {
				contextClass = Class.forName(this.webEnvironment
						? DEFAULT_WEB_CONTEXT_CLASS : DEFAULT_CONTEXT_CLASS);
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Unable create a default ApplicationContext, "
								+ "please specify an ApplicationContextClass",
						ex);
			}
		}
		return (ConfigurableApplicationContext) BeanUtils.instantiate(contextClass);
	}

	/**
	 * 在Context开始之前的一些工作
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        // 貌似默认的都没有配置beanNameGenerator
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(
					AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context)
						.setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context)
						.setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
	}

	/**
     * 应用一些初始化器到应用上下文中
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(
					initializer.getClass(), ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			initializer.initialize(context);
		}
	}

	/**
     * 打印出启动信息
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass)
					.logStarting(getApplicationLog());
		}
	}

	/**
	 * 打印活跃profile信息
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: "
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: "
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
     * 加载指定的Source实例化成Bean注册到上下文
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug(
					"Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
		BeanDefinitionLoader loader = createBeanDefinitionLoader(
				getBeanDefinitionRegistry(context), sources);
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context)
					.getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
     * 创建BeanDefinitionLoader用于加载Bean
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(
			BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ApplicationContext applicationContext) {
		Assert.isInstanceOf(AbstractApplicationContext.class, applicationContext);
		((AbstractApplicationContext) applicationContext).refresh();
	}

	/**
	 * 执行一些Refresh之后的操作
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context,
			ApplicationArguments args) {
		callRunners(context, args);
	}

    /* 执行各种Runner，虽然不知道Runner是干嘛用的 */
	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<Object>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<Object>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

    /* 不明白Runner是干嘛用的 */
	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

    /* 不知道在干嘛 */
	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

    /* 打出错误日志 */
	private void handleRunFailure(ConfigurableApplicationContext context,
			SpringApplicationRunListeners listeners, FailureAnalyzers analyzers,
			Throwable exception) {
		try {
			try {
				handleExitCode(context, exception);
				listeners.finished(context, exception);
			}
			finally {
				reportFailure(analyzers, exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private void reportFailure(FailureAnalyzers analyzers, Throwable failure) {
		try {
			if (analyzers != null && analyzers.analyzeAndReport(failure)) {
				registerLoggedException(failure);
				return;
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application startup failed", failure);
			registerLoggedException(failure);
		}
	}

	/**
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

	private void handleExitCode(ConfigurableApplicationContext context,
			Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(ConfigurableApplicationContext context,
			Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(ConfigurableApplicationContext context,
			Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context
				.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName())
				|| "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns whether this {@link SpringApplication} is running within a web environment.
	 * @return {@code true} if running within a web environment, otherwise {@code false}.
	 * @see #setWebEnvironment(boolean)
	 */
	public boolean isWebEnvironment() {
		return this.webEnvironment;
	}

	/**
	 * Sets if this application is running within a web environment. If not specified will
	 * attempt to deduce the environment based on the classpath.
	 * @param webEnvironment if the application is running in a web environment
	 */
	public void setWebEnvironment(boolean webEnvironment) {
		this.webEnvironment = webEnvironment;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner The Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<String, Object>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = new LinkedHashSet<String>(Arrays.asList(profiles));
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * @return the sources the application sources.
	 * @see #SpringApplication(Object...)
	 */
	public Set<Object> getSources() {
		return this.sources;
	}

	/**
	 * The sources that will be used to create an ApplicationContext. A valid source is
	 * one of: a class, class name, package, package name, or an XML resource location.
	 * Can also be set using constructors and static convenience methods (e.g.
	 * {@link #run(Object[], String[])}).
	 * <p>
	 * NOTE: sources defined here will be used in addition to any sources specified on
	 * construction.
	 * @param sources the sources to set
	 * @see #SpringApplication(Object...)
	 */
	public void setSources(Set<Object> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources.addAll(sources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_WEB_CONTEXT_CLASS} for web based applications
	 * or {@link AnnotationConfigApplicationContext} for non web based applications.
	 * @param applicationContextClass the context class to set
	 */
	public void setApplicationContextClass(
			Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.applicationContextClass = applicationContextClass;
		if (!isWebApplicationContext(applicationContextClass)) {
			this.webEnvironment = false;
		}
	}

	private boolean isWebApplicationContext(Class<?> applicationContextClass) {
		try {
			return WebApplicationContext.class.isAssignableFrom(applicationContextClass);
		}
		catch (NoClassDefFoundError ex) {
			return false;
		}
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(
			Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<ApplicationContextInitializer<?>>();
		this.initializers.addAll(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<ApplicationListener<?>>();
		this.listeners.addAll(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * 返回监听器
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * 启动一个springBoot应用
	 * @param source the source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Object source, String... args) {
		return run(new Object[] { source }, args);
	}

	/**
	 * 启动一个SpringBoot应用
	 * @param sources the sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	public static ConfigurableApplicationContext run(Object[] sources, String[] args) {
		return new SpringApplication(sources).run(args);
	}

	/**
	 * 感觉主要是用来命令行启动用的。SpringBoot应用通过args传递给容器。
	 * Most developers will want to define their own main method can call the
	 * {@link #run(Object, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Object[], String[])
	 * @see SpringApplication#run(Object, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Object[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context,
			ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context
						.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}

		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode == 0 ? 1 : exitCode);
		}
		return exitCode;
	}

    /* 销毁，没啥好说的 */
	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<E>();
		list.addAll(elements);
		Collections.sort(list, AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<E>(list);
	}

}

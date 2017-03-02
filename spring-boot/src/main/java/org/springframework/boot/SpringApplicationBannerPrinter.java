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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * 没啥用，主要用来输出Banner，所谓Banner就是指Spring启动时候答应出的比较大的"Spring"字符串图形。
 * @author Phillip Webb
 */
class SpringApplicationBannerPrinter {
	/* banner地址的属性名称，如果banner打开banner的内容会从指定的地址读取 */
	static final String BANNER_LOCATION_PROPERTY = "banner.location";
    /* banner图片地址的属性名称 */
	static final String BANNER_IMAGE_LOCATION_PROPERTY = "banner.image.location";
    /* 默认的banner文件，banner的内容会从这个文件读取 */
	static final String DEFAULT_BANNER_LOCATION = "banner.txt";
    /* banner图形的扩展名 */
	static final String[] IMAGE_EXTENSION = { "gif", "jpg", "png" };

	private static final Banner DEFAULT_BANNER = new SpringBootBanner();
    /* 主要用来加载Banner在文件系统中的资源 */
	private final ResourceLoader resourceLoader;
    /* 回退banner，不知道是干啥用的 */
	private final Banner fallbackBanner;

	SpringApplicationBannerPrinter(ResourceLoader resourceLoader, Banner fallbackBanner) {
		this.resourceLoader = resourceLoader;
		this.fallbackBanner = fallbackBanner;
	}

    /* 输出Banner到日志(logger) */
	public Banner print(Environment environment, Class<?> sourceClass, Log logger) {
		Banner banner = getBanner(environment, this.fallbackBanner);
		try {
			logger.info(createStringFromBanner(banner, environment, sourceClass));
		}
		catch (UnsupportedEncodingException ex) {
			logger.warn("Failed to create String for banner", ex);
		}
		return new PrintedBanner(banner, sourceClass);
	}

	public Banner print(Environment environment, Class<?> sourceClass, PrintStream out) {
		Banner banner = getBanner(environment, this.fallbackBanner);
		banner.printBanner(environment, sourceClass, out);
		return new PrintedBanner(banner, sourceClass);
	}

    /**
     * 获取Banner，其实获取了两次，一次是图形Banner，一次是文本banner。
     * 如果两种Banner都没有，则返回fallbackBanner。
     * 如果连fallbackBanner都没有就返回默认Banner。
     * @param environment
     * @param definedBanner 打酱油的参数
     * @return
     */
	private Banner getBanner(Environment environment, Banner definedBanner) {
		Banners banners = new Banners();
		banners.addIfNotNull(getImageBanner(environment));
		banners.addIfNotNull(getTextBanner(environment));
		if (banners.hasAtLeastOneBanner()) {
			return banners;
		}
		if (this.fallbackBanner != null) {
			return this.fallbackBanner;
		}
		return DEFAULT_BANNER;
	}

    /**
     * 获取文本Banner
     * @param environment
     * @return
     */
	private Banner getTextBanner(Environment environment) {
		String location = environment.getProperty(BANNER_LOCATION_PROPERTY,
				DEFAULT_BANNER_LOCATION);
		Resource resource = this.resourceLoader.getResource(location);
		if (resource.exists()) {
			return new ResourceBanner(resource);
		}
		return null;
	}

    /**
     * 获取图形Banner
     * 先从启动参数找，如果有就加载。
     * 如果没有启动参数，直接通过banner ＋ 预先设定的扩展名找。
     * @param environment
     * @return
     */
	private Banner getImageBanner(Environment environment) {
		String location = environment.getProperty(BANNER_IMAGE_LOCATION_PROPERTY);
		if (StringUtils.hasLength(location)) {
			Resource resource = this.resourceLoader.getResource(location);
			return (resource.exists() ? new ImageBanner(resource) : null);
		}
		for (String ext : IMAGE_EXTENSION) {
			Resource resource = this.resourceLoader.getResource("banner." + ext);
			if (resource.exists()) {
				return new ImageBanner(resource);
			}
		}
		return null;
	}

    /**
     * 将Banner转化成一个字符串，如果是图片会包不支持的错误，这里面通过二进制输出流获取字符串的方式还是挺实用的。
     * @return
     * @throws UnsupportedEncodingException
     */
	private String createStringFromBanner(Banner banner, Environment environment,
			Class<?> mainApplicationClass) throws UnsupportedEncodingException {
        /* 定义一个二进制输出流 */
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		banner.printBanner(environment, mainApplicationClass, new PrintStream(baos));
		String charset = environment.getProperty("banner.charset", "UTF-8");
		return baos.toString(charset);
	}

	/**
     * Banner组合，可以把多个Banner组合成一个Banner，输出的时候所有子Banner全部输出。厉害了，我的哥。
     * 这里Junit4的Description也是用comprised of这个单词，看来一个类型实例嵌套同类型的另外一个实例就叫comprised of。
	 */
	private static class Banners implements Banner {

		private final List<Banner> banners = new ArrayList<Banner>();

		public void addIfNotNull(Banner banner) {
			if (banner != null) {
				this.banners.add(banner);
			}
		}

		public boolean hasAtLeastOneBanner() {
			return !this.banners.isEmpty();
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass,
				PrintStream out) {
			for (Banner banner : this.banners) {
				banner.printBanner(environment, sourceClass, out);
			}
		}

	}

	/**
     * 一个Banner的装饰器，其实就是保存了Banner和源码类。
     * 按照原来的注释来说主要为了可以重复输出，而不需要重新制定资源类。感觉没啥意义。
	 */
	private static class PrintedBanner implements Banner {

		private final Banner banner;

		private final Class<?> sourceClass;

		PrintedBanner(Banner banner, Class<?> sourceClass) {
			this.banner = banner;
			this.sourceClass = sourceClass;
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass,
				PrintStream out) {
			sourceClass = (sourceClass == null ? this.sourceClass : sourceClass);
			this.banner.printBanner(environment, sourceClass, out);
		}

	}

}

/*
 * Copyright 2012-2014 the original author or authors.
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

import java.io.PrintStream;

import org.springframework.core.env.Environment;

/**
 * 其实就是启动时候答应出的字符图形，没啥用。
 *
 * @author Phillip Webb
 * @author Michael Stummvoll
 * @author Jeremy Rickard
 * @since 1.2.0
 */
public interface Banner {

	/**
	 * 输出Banner信息。
	 * @param environment spring环境
	 * @param sourceClass the source class for the application
	 * @param out 输出流
	 */
	void printBanner(Environment environment, Class<?> sourceClass, PrintStream out);

	/**
	 * An enumeration of possible values for configuring the Banner.
	 */
	enum Mode {

		/**
		 * Disable printing of the banner.
		 */
		OFF,

		/**
		 * Print the banner to System.out.
		 */
		CONSOLE,

		/**
		 * Print the banner to the log file.
		 */
		LOG

	}

}

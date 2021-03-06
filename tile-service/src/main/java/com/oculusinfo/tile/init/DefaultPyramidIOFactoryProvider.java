/*
 * Copyright (c) 2014 Oculus Info Inc.
 * http://www.oculusinfo.com/
 *
 * Released under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oculusinfo.tile.init;

import com.oculusinfo.binning.io.*;
import com.oculusinfo.factory.ConfigurableFactory;

import java.util.List;

/**
 * Basic enum of all the default {@link DelegateFactoryProviderTarget} types availables in the system.<br>
 * <br>
 * To create one use the create method for the desired type. Example:<br>
 * <pre><code>
 * DefaultPyramidIOFactoryProvider.FILE_SYSTEM.create();
 * </code></pre>
 * 
 * @author cregnier
 *
 */
public enum DefaultPyramidIOFactoryProvider {
	HBASE ("hbase"),
	FILE_SYSTEM ("file-system"),
	FILE ("file"), 
	JDBC ("jdbc"),
	RESOURCE ("resource"), 
	ZIP ("zip"),
	SQLITE ("sqlite"),
	DUMMY ("dummy");

	//-------------------------------------

	private final String factoryTypeName;

	private DefaultPyramidIOFactoryProvider(String factoryTypeName) {
		this.factoryTypeName = factoryTypeName;
	}
	
	/**
	 * Creates the {@link DelegateFactoryProviderTarget} for the desired type.
	 */
	public DelegateFactoryProviderTarget<PyramidIO> create() {
		return new DefaultPyramidIOFactoryProviderImpl(factoryTypeName);
	}

	//-------------------------------------

	protected static class DefaultPyramidIOFactoryProviderImpl implements DelegateFactoryProviderTarget<PyramidIO> {
	
		private final String factoryType;
		
		protected DefaultPyramidIOFactoryProviderImpl(String factoryType) {
			this.factoryType = factoryType;
		}
		
		@Override
		public ConfigurableFactory<PyramidIO> createFactory(List<String> path) {
			return createFactory(null, null, path);
		}
	
		@Override
		public ConfigurableFactory<PyramidIO> createFactory(ConfigurableFactory<?> parent, List<String> path) {
			return createFactory(null, parent, path);
		}
	
		@Override
		public ConfigurableFactory<PyramidIO> createFactory(String factoryName, ConfigurableFactory<?> parent, List<String> path) {
			ConfigurableFactory<PyramidIO> factory = null;
			if (factoryType.equals("hbase")) {
				factory = new HBasePyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("file-system") || factoryName.equals("file")) {
				factory = new FileSystemPyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("jdbc")) {
				factory = new JDBCPyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("resource")) {
				factory = new ResourcePyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("zip")) {
				factory = new ZipPyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("sqlite")) {
				factory = new SQLitePyramidIOFactory(factoryName, parent, path);				
			}
			else if (factoryType.equals("dummy")) {
			    factory = new DummyPyramidIOFactory(factoryName, parent, path);
			}
			return factory;
		}
	
		@Override
		public String getFactoryName() {
			return factoryType;
		}
	
		@Override
		public List<String> getPath() {
			return null;
		}
	
	}
}

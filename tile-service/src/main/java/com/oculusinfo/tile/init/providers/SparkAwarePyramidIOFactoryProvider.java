/*
 * Copyright (c) 2014 Oculus Info Inc. http://www.oculusinfo.com/
 * 
 * Released under the MIT License.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oculusinfo.tile.init.providers;


import com.google.inject.Inject;
import com.oculusinfo.binning.io.PyramidIO;
import com.oculusinfo.factory.ConfigurableFactory;
import com.oculusinfo.tile.init.DelegateFactoryProviderTarget;
import com.oculusinfo.tile.rest.tile.caching.LiveTilePyramidIOFactory;
import com.oculusinfo.tile.spark.SparkContextProvider;

import java.util.List;



public class SparkAwarePyramidIOFactoryProvider implements DelegateFactoryProviderTarget<PyramidIO> {
	@Inject
	private SparkContextProvider _contextProvider;

	@Override
	public ConfigurableFactory<PyramidIO> createFactory (List<String> path) {
		return new LiveTilePyramidIOFactory(null, path, _contextProvider);
	}
	
	@Override
	public ConfigurableFactory<PyramidIO> createFactory (ConfigurableFactory<?> parent,
	                                                     List<String> path) {
		return new LiveTilePyramidIOFactory(parent, path, _contextProvider);
	}

	@Override
	public ConfigurableFactory<PyramidIO> createFactory (String factoryName,
	                                                     ConfigurableFactory<?> parent,
	                                                     List<String> path) {
		return new LiveTilePyramidIOFactory(factoryName, parent, path, _contextProvider);
	}
	
	@Override
	public String getFactoryName() {
		return "live";
	}
	
	@Override
	public List<String> getPath() {
		return null;
	}
}

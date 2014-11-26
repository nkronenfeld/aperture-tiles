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


import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oculusinfo.binning.io.serialization.TileSerializer;
import com.oculusinfo.binning.io.serialization.TileSerializerFactory;
import com.oculusinfo.factory.ConfigurableFactory;
import com.oculusinfo.factory.providers.DelegateFactoryProviderTarget;
import com.oculusinfo.factory.providers.StandardUberFactoryProvider;



@Singleton
public class StandardTileSerializerFactoryProvider extends StandardUberFactoryProvider<TileSerializer<?>> {
	@Inject
	public StandardTileSerializerFactoryProvider (Set<DelegateFactoryProviderTarget<TileSerializer<?>>> providers) {
		super(providers);
	}

	@Override
	public ConfigurableFactory<TileSerializer<?>> createFactory (List<String> path) {
		return new TileSerializerFactory(null, path, createChildren(path));
	}

	@Override
	public ConfigurableFactory<TileSerializer<?>> createFactory (ConfigurableFactory<?> parent,
	                                                             List<String> path) {
		return new TileSerializerFactory(parent, path, createChildren(getMergedPath(parent.getRootPath(), path)));
	}

	@Override
	public ConfigurableFactory<TileSerializer<?>> createFactory (String factoryName,
	                                                             ConfigurableFactory<?> parent,
	                                                             List<String> path) {
		return new TileSerializerFactory(factoryName, parent, path, createChildren(getMergedPath(parent.getRootPath(), path)));
	}
}

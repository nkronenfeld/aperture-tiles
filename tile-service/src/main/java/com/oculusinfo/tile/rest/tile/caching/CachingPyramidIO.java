/**
 * Copyright (c) 2013 Oculus Info Inc. http://www.oculusinfo.com/
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
package com.oculusinfo.tile.rest.tile.caching;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oculusinfo.binning.TileData;
import com.oculusinfo.binning.TileIndex;
import com.oculusinfo.binning.io.PyramidIO;
import com.oculusinfo.binning.io.serialization.TileSerializer;
import com.oculusinfo.factory.ConfigurableFactory;
import com.oculusinfo.factory.ConfigurationException;
import com.oculusinfo.tile.rest.tile.TileBounds;

public class CachingPyramidIO implements PyramidIO {
	private static final Logger LOGGER = LoggerFactory.getLogger(CachingPyramidIO.class);

	private Map<String, LayerInfo<?>>                         _layers;
	private List<LayerDataChangedListener>                    _layerListeners;
	private Thread                                            _requestThread;
	private Stack<String>                                     _mruIDs;



	public CachingPyramidIO () {
		_layers = new HashMap<>();
		_layerListeners = new ArrayList<>();
		_mruIDs = new Stack<>();
		_requestThread = new Thread(new TileRequestRunner(), "Tile Request Thread");
		_requestThread.setDaemon(true);
		_requestThread.start();
	}

	public void addLayerListener (LayerDataChangedListener listener) {
		_layerListeners.add(listener);
	}

	public void removeLayerListener (LayerDataChangedListener listener) {
		_layerListeners.remove(listener);
	}

	synchronized private <T> RequestCache<TileIndex, TileData<T>> getTileCache (String pyramidId, TileSerializer<T> serializer) {
		// We rely on configuration to make sure types match here.
		@SuppressWarnings({"rawtypes", "unchecked"})
		LayerInfo<T> info = (LayerInfo) _layers.get(pyramidId);

		if (null == info) {
			info = new LayerInfo<>();
			_layers.put(pyramidId, info);
		}

		if (null != serializer)
			info._serializer = serializer;

		if (null == info._tileCache) {
			info._tileCache = new RequestCache<TileIndex, TileData<T>>(10000, 100);
			info._tileCache.addGlobalCallback(new GlobalCallback(pyramidId));
		}

		return info._tileCache;
	}

	// Using the callback mechanism in the tile cache, get a tile and hand it
	// back synchronously.
	//
	// This does _not_ handle making the request; that must be done separately
	// with RequestData.
	private <T> TileData<T> getTileData (String pyramidId, TileIndex index) {
		RequestCache<TileIndex, TileData<T>> cache = getTileCache(pyramidId, null);
		CacheListenerCallback<T> callback = new CacheListenerCallback<>();
		cache.request(index, callback);

		TileData<T> tile = callback.waitForTile();

		return tile;
	}




	@Override
	public void initializeForWrite (String pyramidId) throws IOException {
		throw new UnsupportedOperationException("Caching Pyramid IO only supports reading");
	}

	@Override
	public <T> void writeTiles (String pyramidId,
	                            TileSerializer<T> serializer,
	                            Iterable<TileData<T>> data) throws IOException {
		throw new UnsupportedOperationException("Caching Pyramid IO only supports reading");
	}

	@Override
	public void writeMetaData (String pyramidId, String metaData) throws IOException {
		throw new UnsupportedOperationException("Caching Pyramid IO only supports reading");
	}



	/*
	 * Set up a base pyramid from which to read when we get a cache miss
	 */
	public void setupBasePyramidIO (String pyramidId, ConfigurableFactory<PyramidIO> factory) {
		if (!_layers.containsKey(pyramidId) || null == _layers.get(pyramidId)._basePyramidIO) {
			synchronized (_layers) {
				if (!_layerListeners.contains(pyramidId)) {
					_layers.put(pyramidId, new LayerInfo<>());
				}
				if (null == _layers.get(pyramidId)._basePyramidIO) {
					try {
						_layers.get(pyramidId)._basePyramidIO = factory.produce(PyramidIO.class);
					} catch (ConfigurationException e) {
						LOGGER.warn("Error creating base pyramid IO for pyramid "+pyramidId, e);
					}
				}
			}
		}
	}


	@Override
	public void initializeForRead (String pyramidId, int width, int height,
	                               Properties dataDescription) {
		if (!_layers.containsKey(pyramidId) || null == _layers.get(pyramidId)._basePyramidIO) {
			LOGGER.info("Attempt to initialize unknown pyramid" + pyramidId + "'.");
		} else {
			_layers.get(pyramidId)._basePyramidIO.initializeForRead(pyramidId, width, height, dataDescription);
		}
	}

	/**
	 * Request a set of tiles, retrieving some of them immediately, and setting
	 * the rest up for eventual retrieval
	 * 
	 * @param pyramidId
	 * @param serializer
	 * @param indices Indices of tiles to be requested.  May not be null.
	 * @return
	 * @throws IOException
	 */
	public <T> void requestTiles (String pyramidId,
	                              TileSerializer<T> serializer,
	                              Iterable<TileIndex> indices) throws IOException {
		RequestCache<TileIndex, TileData<T>> cache = getTileCache(pyramidId, serializer);

		// Actually reqeust tiles from our cache
		for (TileIndex index: indices) cache.request(index, null);

		// Update the list of most recently used pyramids, so we can update them first.
		synchronized (_mruIDs) {
			// Move this id to the head of the list.
			_mruIDs.remove(pyramidId);
			_mruIDs.push(pyramidId);
		}
	}

	@Override
	public <T> List<TileData<T>> readTiles (String pyramidId,
	                                        TileSerializer<T> serializer,
	                                        Iterable<TileIndex> indices) throws IOException {
		List<TileData<T>> tiles = new ArrayList<>();
		for (TileIndex index: indices) {
			// We rely on configuration to make sure types match here
			@SuppressWarnings({"unchecked", "rawtypes"})
			TileData<T> tile = (TileData) getTileData(pyramidId, index);

			if (null != tile)
				tiles.add(tile);
		}
    
		return(tiles);
	}

	@Override
	public <T> InputStream getTileStream (String pyramidId,
	                                      TileSerializer<T> serializer,
	                                      TileIndex index) throws IOException {
		// We cache tiles, not streams, so we need to serialize the tile into a
		// stream, in order to return a stream.
		TileData<T> tile = getTileData(pyramidId, index);

		if (null == tile) {
			return null;
		} else {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			serializer.serialize(tile, baos);
			baos.flush();
			baos.close();
			return new ByteArrayInputStream(baos.toByteArray());
		}
	}

	@Override
	public String readMetaData (String pyramidId) throws IOException {
		if (_layers.containsKey(pyramidId) && null != _layers.get(pyramidId)._basePyramidIO)
			return _layers.get(pyramidId)._basePyramidIO.readMetaData(pyramidId);
		return "";
	}

	@Override
	public void removeTiles (String id, Iterable<TileIndex> tiles ) throws IOException {
		throw new IOException("removeTiles not currently supported for CachingPyramidIO");
	}

	private class CacheListenerCallback<T> implements RequestCache.RequestCallback<TileIndex, TileData<T>> {
		private TileData<T> _tile;
		private boolean _waiting;
		private boolean _notified;



		public CacheListenerCallback () {
			_tile = null;
			_waiting = false;
			_notified = false;
		}

		synchronized public TileData<T> waitForTile () {
				if (!_notified)
					try {
						_waiting = true;
						wait();
					} catch (InterruptedException e) {
						LOGGER.warn("Error waiting for return for tile.", e);
						return null;
					} finally {
						_waiting = false;
					}

				return _tile;
			}

		@Override
		public boolean onRequestFulfilled (TileIndex key, TileData<T> value) {
			_tile = value;
			_notified = true;
			if (_waiting)
				this.notify();
			return true;
		}
	}

	private class GlobalCallback implements RequestCache.GlobalRequestCallback {
		private String _layer;
		GlobalCallback (String layer) {
			_layer = layer;
		}

		@Override
		public void onRequestsFulfilled () {
			for (LayerDataChangedListener listener: _layerListeners) {
				listener.onLayerDataChanged(_layer);
			}
		}
	}
	public interface LayerDataChangedListener {
		public void onLayerDataChanged (String layer);
	}



	private class TileRequestRunner implements Runnable {
		private <T> void requestTiles (String pyramidId,
		                               LayerInfo<T> info,
		                               Collection<TileIndex> indices) throws IOException {
			PyramidIO base = info._basePyramidIO;
			RequestCache<TileIndex, TileData<T>> cache = info._tileCache;
			TileSerializer<T> serializer = info._serializer;

			List<TileData<T>> tiles = base.readTiles(pyramidId, serializer, indices);

			// New way
			Map<TileIndex, TileData<T>> results = new HashMap<>();
			for (TileData<T> tile: tiles) {
				results.put(tile.getDefinition(), tile);
				indices.remove(tile.getDefinition());
			}
			for (TileIndex index: indices) {
				results.put(index, null);
			}
			cache.provide(results);
		}

		@Override
		public void run () {
			while (true) {
				while (!_mruIDs.isEmpty()) {
					String id = null;
					try {
						synchronized (_mruIDs) {
							id = _mruIDs.pop();
						}

						Collection<TileIndex> pending = null;
						LayerInfo<?> info = _layers.get(id);
						if (null != info && null != info._tileCache)
							pending = info._tileCache.reserveKeys(0);
						if (!pending.isEmpty()) {
							// Actually request tiles
							requestTiles(id, info, pending);
						}
					} catch (Throwable t) {
						LOGGER.warn("Error retreiving tiles for pyramid "+id, t);
					}
				}
				// And pause briefly before trying again.
				try {
					this.wait(250);
				} catch (Throwable t) {
				}
			}
		}
	}

	private class LayerInfo<T> {
		RequestCache<TileIndex, TileData<T>> _tileCache;
		PyramidIO                            _basePyramidIO;
		TileSerializer<T>                    _serializer;
	}
}

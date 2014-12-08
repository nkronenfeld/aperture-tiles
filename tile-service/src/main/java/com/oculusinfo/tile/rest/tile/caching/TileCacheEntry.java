/**
 * Copyright (c) 2013 Oculus Info Inc.
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
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
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

import com.oculusinfo.binning.TileData;
import com.oculusinfo.binning.TileIndex;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class tracks an entry in an LRU cache for a particular tile; it keeps 
 * track of whether or not the tile has been directly or indirectly requested, 
 * and the age of the original request, so that the LRUCache's RemovalPolicy 
 * can determine correctly whether or not the tile should be purged when 
 * requested.
 * 
 * @author nkronenfeld
 */
public class TileCacheEntry<T> {
	/* The index of our data */
	private TileIndex                     _index;
	/* Our actual data */
	private TileData<T>                   _tile;
	/* The time of our original request */
	private long                          _requestTime;
	/* True only if the data for this tile has been received */
	private boolean                       _received;
	/* True only if this tile has ever actually been retrieved */
	private boolean                       _retreived;
	/* True only if this request has been explicitly reserved for fulfillment */
    private boolean                       _reserved;
	/* A list of listeners for tile requests */
	private List<CacheRequestCallback<T>> _requests;

	public TileCacheEntry (TileIndex index) {
		_index = index;
		_tile = null;
		_requestTime = System.currentTimeMillis();
		_received = false;
		_retreived = false;
		_reserved = false;
		_requests = new ArrayList<>();
	}

	/**
	 * Used to request a tile, of course. The callback will be called once when
	 * the tile is received (immediately if it is already there)
	 */
	public void requestTile (CacheRequestCallback<T> callback) {
		if (_received) {
			callback.onTileReceived(_index, _tile);
			_retreived = true;
		} else {
			_requests.add(callback);
		}
	}

	/*
	 * Notify the anyone who cares that a tile has been received.
	 * 
	 * @param tile
	 *            The requested tile
	 */
	void setTile (TileData<T> tile) {
		_received = true;
		_tile = tile;
		Iterator<CacheRequestCallback<T>> i = _requests.iterator();
		while (i.hasNext()) {
			CacheRequestCallback<T> callback = i.next();
			if (callback.onTileReceived(_index, _tile)) {
				_retreived = true;
				i.remove();
			}
		}
	}

	/*
	 * Notify the anyone who cares that a tile has been abandoned.
	 */
	void abandonTile () {
		Iterator<CacheRequestCallback<T>> i = _requests.iterator();
		while (i.hasNext()) {
			CacheRequestCallback<T> callback = i.next();
			callback.onTileAbandoned(_index);
			i.remove();
		}
	}

	/**
	 * Get the index of the tile described by this cache entry
	 */
	public TileIndex getIndex () {
	    return _index;
	}

	/**
	 * Indicates if the requested tile has been received.
	 * @return
	 */
	public boolean hasBeenReceived () {
	    return _received;
	}

	/**
	 * Indicates whether or not the requested tile has yet been retrieved
	 */
	public boolean hasBeenRetrieved () {
		return _retreived;
	}

	/**
     * Indicates whether or not something has reserved the requested tile for
     * retreival.
     */
	synchronized public boolean hasBeenReserved () {
	    return _reserved;
	}

	/**
     * Reserve the requested tile for retreival.
     * 
     * @return true if the tile was properly reserved, false if it has already
     *         been reserved.
     */
	synchronized public boolean reserve () {
	    if (_reserved) return false;
	    _reserved = true;
	    return true;
	}

	/**
	 * Indicates the first time at which this request was made
	 */
	public long initialRequestTime () {
		return _requestTime;
	}

	/**
	 * Indicates the age of the request
	 * 
	 * @return How long ago the request was first made, in milliseconds
	 */
	public long age () {
		return System.currentTimeMillis()-_requestTime;
	}

	@Override
	public String toString () {
	    return String.format("Tile cache entry for %s (age: %.2fs, %s, %s, %s, %s, %d requests)",
	                         _index.toString(),
	                         (System.currentTimeMillis()-_requestTime)/1000.0,
	                         (null == _tile ? "empty" : "filled"),
	                         (_received ? "received" : "waiting"),
	                         (_retreived ? "retreived" : "waiting"),
	                         (_reserved ? "reserved" : "pending"),
	                         _requests.size());
	}


	/**
	 * A callback object that allows notifcation of the reception of a tile
	 */
	public static interface CacheRequestCallback<T> {
		/**
		 * Called when the data for a tile is found.
		 * 
		 * @param index The index of the requested tile
		 * @param tile The tile data that was requested
		 * @return True if the tile was processed, and can freely be deleted
		 *         from the repository (and this callback won't be called
		 *         again). False if it was not so processed (in which case this
		 *         callback may be called again, if, for instance, the tile is
		 *         received a second time) If false, some other callback may
		 *         still process it, in which case it may still be deleted.
		 */
		public boolean onTileReceived (TileIndex index, TileData<T> tile);
		
		/**
		 * Called when the system has given up on listening for a tile
		 */
		public void onTileAbandoned (TileIndex index);
	}
}

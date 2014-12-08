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



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oculusinfo.binning.util.SynchronizedLRUCache;



/**
 * A cache of requests for objects, with their results as they come in.
 * 
 * One can think of this cache as a fairly simple state machine.
 * 
 * A request comes in in the REQUESTED stated.
 * 
 * When something promises to fulfill that request, it moves to the PENDING
 * state.
 * 
 * When something actually fulfills it, it moves to the RECEIVED state.
 * 
 * The RECEIVED state is an LRU cache - the cache only holds so many received
 * objects. The maximum number is configurable, and is passed in at
 * construction.
 * 
 * @author nkronenfeld
 * 
 * @param <K> The type of key by which each request is known.
 * @param <T> The type of object being cached
 */
public class RequestCache<K, V> {
    // The set of requests made, but not promised.
    private Deque<K>                                  _requestKeys;
    // The set of requests promised, but not received
    private Set<K>                                    _pendingKeys;
    // The set of requests whose final results have been received.
    private SynchronizedLRUCache<K, V>                _received;
    // The set of callbacks to call when results are received, by key.
    private Map<K, Collection<RequestCallback<K, V>>> _callbacks;
    // A set of callbacks to call when any results are received.
    private Collection<GlobalRequestCallback>         _globalCallbacks;

    private ReadWriteLock                             _lock;



    /**
     * 
     * @param maxSize The maximum number of retained objects
     * @param maxTileAge The maximum time to keep retained objects
     */
    public RequestCache (int maxSize, int maxTileAge) {
        _requestKeys = new LinkedList<>();
        _pendingKeys = new HashSet<>();
        _received = new SynchronizedLRUCache<>(maxSize);
        _callbacks = new HashMap<>();
        _globalCallbacks = new LinkedList<>();
        _lock = new ReentrantReadWriteLock();
    }

    /**
     * Add in a callback to be notified when any request is fulfilled.
     * 
     * Note this is note locked - it is expected that global callbacks will be
     * set up at initialization time, and not added or removed while requests
     * are being fulfilled, so there should be no need for locking..
     */
    public void addGlobalCallback (GlobalRequestCallback callback) {
        _globalCallbacks.add(callback);
    }

    /**
     * Remove a callback from being notified when any request is fulfilled.
     * 
     * Note this is note locked - it is expected that global callbacks will be
     * set up at initialization time, and not added or removed while requests
     * are being fulfilled, so there should be no need for locking..
     */
    public void removeGlobalCallback(GlobalRequestCallback callback) {
        _globalCallbacks.remove(callback);
    }

    /**
     * Request an object.
     * 
     * @param key The key by which the object is identified.
     * @param callback A callback that will be called exactly once when the tile
     *            is received (which may be immediately).
     */
    public void request (K key, RequestCallback<K, V> callback) {
        // If we already have the tile, just return it.
        _lock.readLock().lock();
        try {
            // Check if it's already received.
            if (_received.containsKey(key)) {
                callback.onRequestFulfilled(key, _received.get(key));
                return;
            }

            // Check if there is no callback given, and it's already been requested.
            if (null == callback && (_requestKeys.contains(key) || _pendingKeys.contains(key))) {
                return;
            }
        } finally {
            _lock.readLock().unlock();
        }

        // Nope, we have to note the request for future fulfillment.
        _lock.writeLock().lock();
        try {
            if (null != callback) {
                if (!_callbacks.containsKey(key)) {
                    _callbacks.put(key, new LinkedList<RequestCallback<K, V>>());
                }
                _callbacks.get(key).add(callback);
            }
            if (!_pendingKeys.contains(key) && !_requestKeys.contains(key)) {
                _requestKeys.add(key);
            }
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Reserve the next batch of pending keys for retrieval - i.e., request a
     * list of requested keys which, by calling this method, the caller promises
     * to provide at some point in the future.
     * 
     * @param maxKeys The maximum number of keys to return. If less than one,
     *            all unpromised requested keys will be returned.
     * @return A list of requested, but unfulfilled, keys that have not been
     *         previously promised to any other callers.
     */
    public Collection<K> reserveKeys (int maxKeys) {
        // First, the quick, easy 90% check for no pending requests.
        _lock.readLock().lock();
        try {
            if (_requestKeys.isEmpty())
                return Collections.emptySet();
        } finally {
            _lock.readLock().unlock();
        }

        // There are pending requests; reserve them.
        _lock.writeLock().lock();
        try {
            List<K> keys = new ArrayList<>();
            if (maxKeys > 0) {
                for (int i=0; i<maxKeys && !_requestKeys.isEmpty(); ++i) {
                    K next = _requestKeys.removeFirst();
                    keys.add(next);
                    _pendingKeys.add(next);
                }
            } else {
                keys.addAll(_requestKeys);
                _pendingKeys.addAll(_requestKeys);
                _requestKeys.clear();
            }
            return keys;
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Provides a value for a key
     * 
     * @param key The key.
     * @param value The value.  May be null.
     */
    public void provide (K key, V value) {
        Map<K, V> resultMap = new HashMap<>();
        resultMap.put(key, value);
        provide(resultMap);
    }

    public void provide (Map<K, V> results) {
        _lock.writeLock().lock();
        try {
            for (Entry<K, V> entry: results.entrySet()) {
                K key = entry.getKey();
                V value = entry.getValue();

                if (_pendingKeys.contains(key)) {
                    _pendingKeys.remove(key);
                } else if (_requestKeys.contains(key)) {
                    _requestKeys.remove(key);
                }
                _received.put(key, value);
                if (_callbacks.containsKey(key)) {
                    Collection<RequestCallback<K, V>> callbacks = _callbacks.remove(key);
                    for (RequestCallback<K, V> callback: callbacks) {
                        callback.onRequestFulfilled(key, value);
                    }
                }
            }
            for (GlobalRequestCallback callback: _globalCallbacks) {
                callback.onRequestsFulfilled();
            }
        } finally {
            _lock.writeLock().unlock();
        }
    }


    public static interface RequestCallback<K, V> {
        /**
         * Called when a cache request is fulfilled
         * 
         * @param key The key by which the request was known
         * @param value The value found associated with that key
         * @return True if the value was processed, and can freely be deleted
         *         from the cache (and this callback won't be called again).
         *         False if it was not so processed (in which case this callback
         *         may be called again, if, for instance, the tile is received a
         *         second time) If false, some other callback may still process
         *         it, in which case it may still be deleted.
         */
        public boolean onRequestFulfilled (K key, V value);
    }
    public static interface GlobalRequestCallback {
        /**
         * Called when any cache request(s) have been fulfilled.
         */
        public void onRequestsFulfilled ();
    }
}

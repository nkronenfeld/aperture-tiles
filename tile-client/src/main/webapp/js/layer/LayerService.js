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


/**
 * This module handles communications with the server to get the list of 
 * layers the server can serve.
 */
define(function (require) {
    "use strict";

    var Util = require('../util/Util'),
        LayerService,
        parseMetaMinMaxJson,
        parseLevelsMinMax;

    /**
     * Parse a given meta data layers min and max json strings,
     * they are currently stored as malformed json strings, so
     * they require some massaging.
     */
    parseMetaMinMaxJson = function( meta ) {
        var min = {}, max = {};
        if ( meta && ( ( meta.max && meta.max.maximum ) || meta.maximum ) ) {
            max = Util.parseMalformedJson( meta.maximum || meta.max.maximum );
        }
        if ( meta && ( ( meta.min && meta.min.minimum ) || meta.minimum ) ) {
            min = Util.parseMalformedJson( meta.minimum || meta.min.minimum );
        }
        return {
            min: $.isArray( min ) ? min[0] : min,
            max: $.isArray( max ) ? max[0] : max
        };
    };

    /**
     * Meta data minimum and maximums are stored as malformed json
     * strings, but are usually access at a high frequency ( multiple times per tile ).
     * this parses them all and stores them as actual objects.
     */
    parseLevelsMinMax = function( layerInfo ) {
        var meta = layerInfo.meta, key;
        for ( key in meta ) {
            if ( meta.hasOwnProperty( key ) ) {
                meta[key].minMax = parseMetaMinMaxJson( meta[key] );
            }
        }
        return meta;
    };

    LayerService = {

        /**
         * Request layers from the server, sending them to the listed callback 
         * function when they are received.
         */
        requestLayers: function( callback ) {
            aperture.io.rest('/v1.0/layers',
                             'GET',
                             function (results, status) {
                                 var layerMap = {},
                                    i;
                                 if (status.success) {
                                     for ( i=0; i<results.layers.length; i++ ) {
                                         results.layers[i].meta.minMax = parseLevelsMinMax( results.layers[i].meta );
                                         layerMap[ results.layers[i].id ] = results.layers[i];
                                     }
                                 }
                                 callback( layerMap );
                             });

        },

        /**
         * Request a specific layer from the server, sending it to the listed callback
         * function when it is received.
         */
        requestLayer: function( layerId, callback ) {
            aperture.io.rest('/v1.0/layers/' + layerId,
                             'GET',
                             function (layer, status) {
                                 if (status.success) {
                                     layer.meta.minMax = parseLevelsMinMax( layer.meta );
                                 }
                                 callback( layer );
                             });
        },

        /**
         * Set up a configuration object on the server.
         */
        configureLayer: function( layerId, params, callback ) {

            aperture.io.rest('/v1.0/layers/' + layerId,
                             'POST',
                             function( response, statusInfo ) {
                                callback( response.sha );
                             },
                             {
                                 postData: params,
                                 contentType: 'application/json'
                             });
        }
    };

    return LayerService;
});

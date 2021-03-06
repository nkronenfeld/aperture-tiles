/**
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

/*global $, define*/

/**
 * @class LayerState
 *
 * Captures the visual state of a layer in the system, and provides a notification
 * mechanism to allow external code to react to changes to it.
 */
define(function (require) {
    "use strict";




    var LayerState = require('../LayerState'),
        BaseLayerState;



    BaseLayerState = LayerState.extend({
        ClassName: "BaseLayerState",

        /**
         * Initializes a LayerState object with default values.
         *
         * @param {string} id - The immutable ID of the layer.
         */
        init: function ( id ) {
            this._super( id );
            this.domain = 'base';
            this.baseLayerIndex = 0;
        },


        setBaseLayerIndex: function(index) {
            if (this.baseLayerIndex !== index ) {
                this.previousBaseLayerIndex = this.baseLayerIndex;
                this.baseLayerIndex = index;
                this.notify("baseLayerIndex", this.listeners);
            }
        },


        getBaseLayerIndex: function() {
            return this.baseLayerIndex;
        },


        getPreviousBaseLayerIndex: function() {
            return this.previousBaseLayerIndex;
        }

    });

    return BaseLayerState;
});

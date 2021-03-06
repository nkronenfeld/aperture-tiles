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
package com.oculusinfo.annotation.index.impl;

import com.oculusinfo.annotation.data.AnnotationData;
import com.oculusinfo.annotation.index.AnnotationIndexer;
import com.oculusinfo.binning.BinIndex;
import com.oculusinfo.binning.TileAndBinIndices;
import com.oculusinfo.binning.TileIndex;
import com.oculusinfo.binning.TilePyramid;

import java.util.LinkedList;
import java.util.List;

public class AnnotationIndexerImpl extends AnnotationIndexer {

    public AnnotationIndexerImpl() {
    	super();
    }
    
	@Override
	public List<TileAndBinIndices> getIndices( AnnotationData<?> data, TilePyramid pyramid ) {
		
		// only generate indices upwards
    	List<TileAndBinIndices> indices = new LinkedList<>();

        int minLevel = Math.min( data.getLevel(), data.getRange().getFirst() );
        int maxLevel = Math.max( data.getLevel(), data.getRange().getSecond() );

		for (int i=minLevel; i<=maxLevel; i++) {
			indices.add( getIndex( data, i, pyramid ) );
		}
		return indices;
    }

    @Override
    public TileAndBinIndices getIndex( AnnotationData<?> data, int level, TilePyramid pyramid ) {
    	
    	// fill in defaults if dimensions are missing
    	boolean xExists = data.getX() != -1;
    	boolean yExists = data.getY() != -1;
    	double x = ( xExists ) ? data.getX() : 0;
    	double y = ( yExists ) ? data.getY() : 0;
    	
    	// map from raw x and y to tile and bin
    	TileIndex tile = pyramid.rootToTile( x, y, level, NUM_BINS );
		BinIndex bin = pyramid.rootToBin( x, y, tile );

		// insert -1's for uni-variate annotations
		if ( !xExists ) {			
			tile = new TileIndex( tile.getLevel(), -1, tile.getY(), NUM_BINS, NUM_BINS );
			bin = new BinIndex( -1, bin.getY() );	  
		} else if ( !yExists ) {
			tile = new TileIndex( tile.getLevel(), tile.getX(), -1, NUM_BINS, NUM_BINS );
			bin = new BinIndex( bin.getX(), -1 );
		}
		
		/*
		// if indexing level of insertion, do not bin, set as [-1, -1]
		if ( data.getLevel() == level ) {
			bin = new BinIndex( -1, -1 );
		}
		*/
		
		return new TileAndBinIndices( tile, bin );
    }

    
}

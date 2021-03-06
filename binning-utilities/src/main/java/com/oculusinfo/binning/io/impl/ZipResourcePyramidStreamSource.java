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
package com.oculusinfo.binning.io.impl;

import com.oculusinfo.binning.TileIndex;
import com.oculusinfo.binning.io.PyramidIO;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class ZipResourcePyramidStreamSource implements PyramidStreamSource {

	private final Logger _logger = LoggerFactory.getLogger(getClass());

	private ZipFile      _tileSetArchive;
	private String       _tileExtension;


	
	public ZipResourcePyramidStreamSource (String zipFilePath, String tileExtension) {
		try {
			_tileSetArchive = new ZipFile(zipFilePath);
			_tileExtension = tileExtension;
		} catch (IOException e) {
			_logger.warn("Could not create zip file for " + zipFilePath, e);
		}
	}
	
	@Override
	public InputStream getTileStream(String basePath, TileIndex tile) throws IOException {
		String tileLocation = String.format("%s/"+PyramidIO.TILES_FOLDERNAME+"/%d/%d/%d." + _tileExtension, basePath, tile.getLevel(), tile.getX(), tile.getY());
		ZipArchiveEntry entry = _tileSetArchive.getEntry(tileLocation);
		return _tileSetArchive.getInputStream(entry);
	}

	@Override
	public InputStream getMetaDataStream(String basePath) throws IOException {
		String location = basePath+"/"+PyramidIO.METADATA_FILENAME;
		ZipArchiveEntry entry = _tileSetArchive.getEntry(location);
		return _tileSetArchive.getInputStream(entry);
	}
}

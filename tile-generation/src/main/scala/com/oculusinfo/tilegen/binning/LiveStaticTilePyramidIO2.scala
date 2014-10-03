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



package com.oculusinfo.tilegen.binning



import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.lang.{Iterable => JavaIterable}
import java.lang.{Integer => JavaInt}
import java.util.{List => JavaList}
import java.util.Properties
import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.mutable.MutableList
import scala.reflect.ClassTag
import scala.util.{Try, Success, Failure}
import org.apache.spark.Accumulable
import org.apache.spark.AccumulableParam
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import com.oculusinfo.binning.BinIndex
import com.oculusinfo.binning.TileData
import com.oculusinfo.binning.TileIndex
import com.oculusinfo.binning.TilePyramid
import com.oculusinfo.binning.io.PyramidIO
import com.oculusinfo.binning.io.serialization.TileSerializer
import com.oculusinfo.binning.metadata.PyramidMetaData
import com.oculusinfo.tilegen.datasets.Dataset
import com.oculusinfo.tilegen.datasets.DatasetFactory
import org.apache.log4j.Level




/**
 * This class reads and caches a data set for live queries of its tiles
 */
class LiveStaticTilePyramidIO2 (sc: SparkContext) extends PyramidIO {
	private val datasets = MutableMap[String, Dataset[_, _, _, _, _]]()
	private val metaData = MutableMap[String, PyramidMetaData]()

	def initializeForWrite (pyramidId: String): Unit = {
	}

	def writeTiles[T] (pyramidId: String,
	                   serializer: TileSerializer[T],
	                   data: JavaIterable[TileData[T]]): Unit =
		throw new IOException("Can't write raw data")

	def writeMetaData (pyramidId: String,
	                   metaData: String): Unit =
		throw new IOException("Can't write raw data")

	def initializeForRead (pyramidId: String,
	                       width: Int, height: Int,
	                       dataDescription: Properties): Unit = {
		if (!datasets.contains(pyramidId)) {
			datasets.synchronized {
				if (!datasets.contains(pyramidId)) {
					if (!dataDescription.stringPropertyNames.contains("oculus.binning.caching.processed"))
						dataDescription.setProperty("oculus.binning.caching.processed", "true")
					datasets(pyramidId) =
						DatasetFactory.createDataset(sc, dataDescription, Some(width), Some(height))
				}
			}
		}
	}

	def readTiles[BT] (pyramidId: String,
	                   serializer: TileSerializer[BT],
	                   javaTiles: JavaIterable[TileIndex]):
			JavaList[TileData[BT]] = {
		org.apache.log4j.Logger.getRootLogger().setLevel(Level.WARN)
		// Note that all tiles given _must_ have the same dimensions.
		if (!datasets.contains(pyramidId) || null == javaTiles || !javaTiles.iterator.hasNext) {
			null
		} else {
			val dataset = datasets(pyramidId).asInstanceOf[Dataset[_, _, _, _, BT]]
			val tiles = javaTiles.asScala.toArray

			val results = readTilesAndDataset(pyramidId, serializer, tiles, dataset)
			updateMetaData(pyramidId, results)
			results.asJava
		}
	}

	def readTilesAndDataset[IT: ClassTag, PT: ClassTag, DT: ClassTag, AT: ClassTag, BT] (
		pyramidId: String, serializer: TileSerializer[BT],
		tiles: Array[TileIndex], dataset: Dataset[IT, PT, DT, AT, BT]): 
			Seq[TileData[BT]] =
	{
        val analytic = dataset.getBinningAnalytic
        val xBins = tiles(0).getXBins
        val yBins = tiles(0).getYBins



        // Get a map from tile level to a list of tiles on that level, with associated
        // accumulables.
        val levels = tiles.map(_.getLevel).toSet
        val add: (PT, PT) => PT = analytic.aggregate
		val p1 = new TileAccumulableParam(xBins, yBins, add)
		val acc = sc.accumulable(MutableMap[BinIndex, PT]())(p1)
        val tileData = levels.map(level =>
            {
                val param = new TileAccumulableParam(xBins, yBins, add)
                (level,
                 tiles.filter(tile => level == tile.getLevel).map(tile =>
                     (tile, sc.accumulable(MutableMap[BinIndex, PT]())(param))
                 ).toMap
                )
            }
        ).toMap

        // Run over data set, looking for points in those tiles at those levels
        val indexScheme = dataset.getIndexScheme
        val pyramid = dataset.getTilePyramid
        val identity: RDD[(IT, PT, Option[DT])] => RDD[(IT, PT, Option[DT])] =
            rdd => rdd
        dataset.transformRDD(identity).foreach{case (index, value, analyticValue) =>
            {
                val (x, y) = indexScheme.toCartesian(index)

                tileData.foreach{case (level, tileInfos) =>
                    {
                        val tile = pyramid.rootToTile(x, y, level, xBins, yBins)
                        if (tileInfos.contains(tile)) {
                            val bin = pyramid.rootToBin(x, y, tile)
                            tileInfos(tile) += (bin, value)
                        }
                    }
                }
            }
        }

		// We've got aggregates of each tile's data; convert to tiles.
		tileData.flatMap(_._2).flatMap{case (index, data) =>
            {
	            if (data.value.isEmpty) {
		            Seq[TileData[BT]]()
	            } else {
		            val tile = new TileData[BT](index)

		            // Put the proper default in all bins
		            val defaultBinValue =
			            analytic.finish(analytic.defaultProcessedValue)
		            for (x <- 0 until xBins) {
			            for (y <- 0 until yBins) {
				            tile.setBin(x, y, defaultBinValue)
			            }
		            }

		            // Put the proper value into each bin
		            data.value.foreach{case (bin, value) =>
			            tile.setBin(bin.getX, bin.getY, analytic.finish(value))
		            }

		            Seq[TileData[BT]](tile)
	            }
            }
        }.toSeq
    }

	def updateMetaData[BT] (pyramidId: String, tiles: Iterable[TileData[BT]]) = {
		// Update metadata for these levels
		val datasetMetaData = getMetaData(pyramidId).get
		val dataset = datasets(pyramidId).asInstanceOf[Dataset[_, _, _, _, BT]]

		val newDatasetMetaData =
			new PyramidMetaData(datasetMetaData.getName(),
			                    datasetMetaData.getDescription(),
			                    datasetMetaData.getTileSizeX(),
			                    datasetMetaData.getTileSizeY(),
			                    datasetMetaData.getScheme(),
			                    datasetMetaData.getProjection(),
			                    datasetMetaData.getValidZoomLevels(),
			                    datasetMetaData.getBounds(),
			                    null, null)
		dataset.getTileAnalytics.map(_.applyTo(newDatasetMetaData))
		dataset.getDataAnalytics.map(_.applyTo(newDatasetMetaData))
		newDatasetMetaData.addValidZoomLevels(
			tiles.map(tile =>
				new JavaInt(tile.getDefinition().getLevel())
			).toSet.asJava
		)

		metaData(pyramidId) = newDatasetMetaData
	}



	def getTileStream[BT] (pyramidId: String, serializer: TileSerializer[BT],
	                       tile: TileIndex): InputStream = {
		val results: JavaList[TileData[BT]] =
			readTiles(pyramidId, serializer, List[TileIndex](tile).asJava)
		if (null == results || 0 == results.size || null == results.get(0)) {
			null
		} else {
			val bos = new ByteArrayOutputStream;
			serializer.serialize(results.get(0), bos);
			bos.flush
			bos.close
			new ByteArrayInputStream(bos.toByteArray)
		}
	}

	private def getMetaData (pyramidId: String): Option[PyramidMetaData] = {
		if (!metaData.contains(pyramidId) || null == metaData(pyramidId))
			if (datasets.contains(pyramidId))
				metaData(pyramidId) = datasets(pyramidId).createMetaData(pyramidId)
		metaData.get(pyramidId)
	}

	def readMetaData (pyramidId: String): String =
		getMetaData(pyramidId).map(_.toString).getOrElse(null)

	def removeTiles (id: String, tiles: JavaIterable[TileIndex]  ) : Unit =
		throw new IOException("removeTiles not currently supported for LiveStaticTilePyramidIO")
}


class TileAccumulableParam[T] (width: Int, height: Int, add: (T, T) => T)
		extends AccumulableParam[MutableMap[BinIndex, T], (BinIndex, T)]
{
	def addAccumulator (r: MutableMap[BinIndex, T], t: (BinIndex, T)):
			MutableMap[BinIndex, T] = {
		val (index, value) = t
		r(index) = r.get(index).map(valueR => add(valueR, value)).getOrElse(value)
		r
	}

	def addInPlace (r1: MutableMap[BinIndex, T], r2: MutableMap[BinIndex, T]):
			MutableMap[BinIndex, T] = {
		r2.foreach{case (index, value2) =>
			{
				r1(index) = r1.get(index).map(value1 => add(value1, value2)).getOrElse(value2)
			}
		}
		r1
	}
	def zero (initialValue: MutableMap[BinIndex, T]): MutableMap[BinIndex, T] =
		MutableMap[BinIndex, T]()
}

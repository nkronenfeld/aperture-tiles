package com.oculusinfo.tile.rest.tile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oculusinfo.binning.TileIndex;

public class TileBoundsTest {
    @Test
    public void testBigSquare () {
        Set<TileIndex> bigSet = new HashSet<>();
        for (int x=4; x<12; ++x) {
            for (int y=7; y<11; ++y) {
                bigSet.add(new TileIndex(6, x, y));
            }
        }
        List<TileBounds> bounds = TileBounds.combine(bigSet);

        Assert.assertEquals(1, bounds.size());
        Assert.assertEquals("<TileBlock[4-11, 7-10, 6]>", bounds.get(0).toString());
    }

    @Test
    public void testLevels () {
        Set<TileIndex> bigSet = new HashSet<>();
        for (int x=4; x<12; ++x) {
            for (int y=7; y<11; ++y) {
                bigSet.add(new TileIndex(6, x, y));
                bigSet.add(new TileIndex(7, x, y));
            }
        }
        List<TileBounds> bounds = TileBounds.combine(bigSet);

        Assert.assertEquals(2, bounds.size());
        TileBounds level6 = bounds.get(0);
        TileBounds level7 = bounds.get(1);
        if (level6.toString().endsWith("7]>")) {
            TileBounds tmp = level6;
            level6 = level7;
            level7 = tmp;
                    
        }
        Assert.assertEquals("<TileBlock[4-11, 7-10, 6]>", level6.toString());
        Assert.assertEquals("<TileBlock[4-11, 7-10, 7]>", level7.toString());
    }

    @Test
    public void testSquare () {
        Set<TileIndex> bigSet = new HashSet<>();
        bigSet.add(new TileIndex(6, 7, 10));
        bigSet.add(new TileIndex(6, 7, 11));
        bigSet.add(new TileIndex(6, 7, 12));
        bigSet.add(new TileIndex(6, 8, 12));
        bigSet.add(new TileIndex(6, 9, 12));
        bigSet.add(new TileIndex(6, 9, 11));
        bigSet.add(new TileIndex(6, 9, 10));
        bigSet.add(new TileIndex(6, 8, 10));
        List<TileBounds> bounds = TileBounds.combine(bigSet);

        Assert.assertEquals(4, bounds.size());
    }

    @Test
    public void testNoCombine () {
        Set<TileIndex> bigSet = new HashSet<>();
        bigSet.add(new TileIndex(6, 14, 20));
        bigSet.add(new TileIndex(6, 14, 22));
        bigSet.add(new TileIndex(6, 14, 24));
        bigSet.add(new TileIndex(6, 16, 24));
        bigSet.add(new TileIndex(6, 18, 24));
        bigSet.add(new TileIndex(6, 18, 22));
        bigSet.add(new TileIndex(6, 18, 20));
        bigSet.add(new TileIndex(6, 16, 20));
        List<TileBounds> bounds = TileBounds.combine(bigSet);

        Assert.assertEquals(8, bounds.size());
    }
}

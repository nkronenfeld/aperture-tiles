package com.oculusinfo.tile.rest.tile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.oculusinfo.binning.TileData;
import com.oculusinfo.binning.TileIndex;

public class TileBounds {
    private int _minX;
    private int _maxX;
    private int _minY;
    private int _maxY;
    private int _z;

    private TileBounds (TileIndex index) {
        _z = index.getLevel();
        _minX = index.getX();
        _maxX = index.getX();
        _minY = index.getY();
        _maxY = index.getY();
    }

    private TileBounds (int minX, int maxX, int minY, int maxY, int z) {
        _minX = minX;
        _maxX = maxX;
        _minY = minY;
        _maxY = maxY;
        _z = z;
    }

    private boolean canCombineX (TileBounds that) {
        if (this._z == that._z && this._minX == that._minX && this._maxX == that._maxX) {
            // equal in x and z; check y
            // must overlap or neighbor
            if (this._minY > that._minY) return that.canCombineX(this);
            // Now we can assume this._minY <= that._minY
            if (this._maxY >= that._minY-1) return true;
        }
        return false;
    }

    private boolean canCombineY (TileBounds that) {
        if (this._z == that._z && this._minY == that._minY && this._maxY == that._maxY) {
            // equal in y and z; check c
            // must overlap or neighbor
            if (this._minX > that._minX) return that.canCombineY(this);
            // Now we can assume this._minX <= that._minX
            if (this._maxX >= that._minX-1) return true;
        }
        return false;
    }

    public boolean canCombine (TileBounds that) {
        return canCombineX(that) || canCombineY(that);
    }

    private TileBounds combineX (TileBounds that) {
        // Assume canCombineX(that)
        return new TileBounds(_minX,
                              _maxX,
                              Math.min(this._minY, that._minY),
                              Math.max(this._maxY, that._maxY),
                              _z);
    }

    private TileBounds combineY (TileBounds that) {
        // Assume canCombineX(that)
        return new TileBounds(Math.min(this._minX, that._minX),
                              Math.max(this._maxX, that._maxX),
                              _minY,
                              _maxY,
                              _z);
    }

    @Override
    public String toString () {
        return String.format("<TileBlock[%d-%d, %d-%d, %d]>", _minX, _maxX, _minY, _maxY, _z);
    }

    public static List<TileBounds> combine (Collection<TileIndex> indices) {
        List<TileBounds> results = new ArrayList<>();
        for (TileIndex index: indices) results.add(new TileBounds(index));

        boolean reduced = true;
        while (reduced) {
            reduced = false;
            for (int n1=0; !reduced && n1<results.size()-1; ++n1) {
                TileBounds bounds1 = results.get(n1);

                for (int n2=n1+1; n2<results.size(); ++n2) {
                    TileBounds bounds2 = results.get(n2);
                    TileBounds newBounds = null;
                    if (bounds1.canCombineX(bounds2)) {
                        newBounds = bounds1.combineX(bounds2);
                    } else if (bounds1.canCombineY(bounds2)) {
                        newBounds = bounds1.combineY(bounds2);
                    } else {
                        continue;
                    }
                    results.remove(n2);
                    results.remove(n1);
                    results.add(0, newBounds);
                    reduced = true;
                    break;
                }
            }
        }
        return results;
    }

    public static <T> String combineTiles (Collection<TileData<T>> tiles) {
        List<TileIndex> indices = new ArrayList<>();
        for (TileData<T> tile: tiles) indices.add(tile.getDefinition());
        return combineIndices(indices);
    }
    public static String combineIndices (Collection<TileIndex> indices) {
        List<TileBounds> bounds = combine(indices);
        Collections.sort(bounds, new Comparator<TileBounds>() {
            @Override
            public int compare (TileBounds b1, TileBounds b2) {
                if (b1._z < b2._z) return -1;
                else if (b1._z > b2._z) return 1;
                else if (b1._minX < b2._minX) return -1;
                else if (b1._minX > b2._minX) return 1;
                else if (b1._maxX < b2._maxX) return -1;
                else if (b1._maxX > b2._maxX) return 1;
                else if (b1._minY < b2._minY) return -1;
                else if (b1._minY > b2._minY) return 1;
                else if (b1._maxY < b2._maxY) return -1;
                else if (b1._maxY > b2._maxY) return 1;
                else return 0;
            }
        });
        String result = "[";
        for (TileBounds b: bounds) {
            if (result.length() > 1) result = result + ", ";
            result = result + b.toString();
        }
        result += "]";
        return result;
    }
}

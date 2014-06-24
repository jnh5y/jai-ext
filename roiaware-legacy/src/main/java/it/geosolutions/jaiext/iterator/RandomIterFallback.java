/* JAI-Ext - OpenSource Java Advanced Image Extensions Library
*    http://www.geo-solutions.it/
*    Copyright 2014 GeoSolutions


* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at

* http://www.apache.org/licenses/LICENSE-2.0

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package it.geosolutions.jaiext.iterator;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RandomIter;


/**
 * @since EA2
 */
public class RandomIterFallback implements RandomIter
{

    protected RenderedImage im;
    protected Rectangle boundsRect;

    protected SampleModel sampleModel;

    protected int boundsX;
    protected int boundsY;

    public RandomIterFallback(RenderedImage im, Rectangle bounds)
    {
        this.im = im;

        Rectangle imBounds = new Rectangle(im.getMinX(), im.getMinY(),
                im.getWidth(), im.getHeight());
        this.boundsRect = imBounds.intersection(bounds);
        this.sampleModel = im.getSampleModel();


        this.boundsX = boundsRect.x;
        this.boundsY = boundsRect.y;
    }

    /**
     * Sets dataBuffer to the correct buffer for the pixel
     * (x, y) = (xLocal + boundsRect.x, yLocal + boundsRect.y).
     *
     * @param xLocal the X coordinate in the local coordinate system.
     * @param yLocal the Y coordinate in the local coordinate system.
     */
    private Raster makeCurrent(int xLocal, int yLocal)
    {
        final int tileWidth = im.getTileWidth();
        final int tileHeight = im.getTileHeight();
        final int tileGridXOffset = im.getTileGridXOffset();
        final int tileGridYOffset = im.getTileGridYOffset();
        final int tileX = PlanarImage.XToTileX(xLocal, tileGridXOffset, tileWidth);
        final int tileY = PlanarImage.YToTileY(yLocal, tileGridYOffset, tileHeight);

        return im.getTile(tileX, tileY);
    }

    public int getSample(int x, int y, int b)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getSample(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                b,
                tile.getDataBuffer());
    }

    public float getSampleFloat(int x, int y, int b)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getSampleFloat(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                b,
                tile.getDataBuffer());
    }

    public double getSampleDouble(int x, int y, int b)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getSampleDouble(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                b,
                tile.getDataBuffer());
    }

    public int[] getPixel(int x, int y, int[] iArray)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getPixel(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                iArray,
                tile.getDataBuffer());
    }

    public float[] getPixel(int x, int y, float[] fArray)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getPixel(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                fArray,
                tile.getDataBuffer());
    }

    public double[] getPixel(int x, int y, double[] dArray)
    {
        // get tile
        Raster tile = makeCurrent(x - boundsX, y - boundsY);

        // get value
        final int sampleModelTranslateX = tile.getSampleModelTranslateX();
        final int sampleModelTranslateY = tile.getSampleModelTranslateY();

        return sampleModel.getPixel(x - sampleModelTranslateX,
                y - sampleModelTranslateY,
                dArray,
                tile.getDataBuffer());
    }

    public void done()
    {
//        xTiles = null;
//        yTiles = null;
//        dataBuffer = null;
    }
}

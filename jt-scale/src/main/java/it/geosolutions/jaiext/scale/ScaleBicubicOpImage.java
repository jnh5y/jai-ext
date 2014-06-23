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
package it.geosolutions.jaiext.scale;

import it.geosolutions.jaiext.interpolators.InterpolationBicubic;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Map;
import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;

public class ScaleBicubicOpImage extends ScaleOpImage {

    /** Bicubic interpolator */
    protected InterpolationBicubic interpBN = null;

    /** Bicubic Horizontal coefficients for integer type */
    private int[] dataHi;

    /** Bicubic Vertical coefficients for integer type */
    private int[] dataVi;

    /** Bicubic Horizontal coefficients for float type */
    private float[] dataHf;

    /** Bicubic Vertical coefficients for float type */
    private float[] dataVf;

    /** Bicubic Horizontal coefficients for double type */
    private double[] dataHd;

    /** Bicubic Vertical coefficients for double type */
    private double[] dataVd;

    /** Byte lookuptable used if no data are present */
    private final byte[] byteLookupTable = new byte[256];

    public ScaleBicubicOpImage(RenderedImage source, ImageLayout layout, Map configuration,
            BorderExtender extender, Interpolation interp, float scaleX, float scaleY,
            float transX, float transY, boolean useRoiAccessor) {
        super(source, layout, configuration, true, extender, interp, scaleX, scaleY, transX,
                transY, useRoiAccessor);
        scaleOpInitialization(source, interp);
    }

    private void scaleOpInitialization(RenderedImage source, Interpolation interp) {
        // If the source has an IndexColorModel, override the default setting
        // in OpImage. The dest shall have exactly the same SampleModel and
        // ColorModel as the source.
        // Note, in this case, the source should have an integral data type.
        ColorModel srcColorModel = source.getColorModel();
        if (srcColorModel instanceof IndexColorModel) {
            sampleModel = source.getSampleModel()
                    .createCompatibleSampleModel(tileWidth, tileHeight);
            colorModel = srcColorModel;
        }

        SampleModel sm = source.getSampleModel();
        // Source image data Type
        int srcDataType = sm.getDataType();

        // selection of the inverse scale parameters both for the x and y axis
        if (invScaleXRational.num > invScaleXRational.denom) {
            invScaleXInt = invScaleXRational.num / invScaleXRational.denom;
            invScaleXFrac = invScaleXRational.num % invScaleXRational.denom;
        } else {
            invScaleXInt = 0;
            invScaleXFrac = invScaleXRational.num;
        }

        if (invScaleYRational.num > invScaleYRational.denom) {
            invScaleYInt = invScaleYRational.num / invScaleYRational.denom;
            invScaleYFrac = invScaleYRational.num % invScaleYRational.denom;
        } else {
            invScaleYInt = 0;
            invScaleYFrac = invScaleYRational.num;
        }

        // Interpolator settings
        interpolator = interp;

        if (interpolator instanceof InterpolationBicubic) {

            isBicubicNew = true;
            interpBN = (InterpolationBicubic) interpolator;
            this.interp = interpBN;

            switch (srcDataType) {
            case DataBuffer.TYPE_BYTE:
            case DataBuffer.TYPE_USHORT:
            case DataBuffer.TYPE_SHORT:
            case DataBuffer.TYPE_INT:
                dataHi = interpBN.getHorizontalTableData();
                dataVi = interpBN.getVerticalTableData();
                break;
            case DataBuffer.TYPE_FLOAT:
                dataHf = interpBN.getHorizontalTableDataFloat();
                dataVf = interpBN.getVerticalTableDataFloat();
                break;
            case DataBuffer.TYPE_DOUBLE:
                dataHd = interpBN.getHorizontalTableDataDouble();
                dataVd = interpBN.getVerticalTableDataDouble();
                break;
            default:
                throw new IllegalArgumentException("Wrong data Type");
            }

            interpBN.setROIdata(roiBounds, roiIter);
            noData = interpBN.getNoDataRange();
            precisionBits = interpBN.getPrecisionBits();

            if (noData != null) {
                hasNoData = true;
                destinationNoDataDouble = interpBN.getDestinationNoData();
            } else if (hasROI) {
                destinationNoDataDouble = interpBN.getDestinationNoData();
            }
        }
        // subsample bits used for the bilinear and bicubic interpolation
        subsampleBits = interp.getSubsampleBitsH();

        if (precisionBits > 0) {
            round = 1 << (precisionBits - 1);
        }

        // Number of subsample positions
        one = 1 << subsampleBits;

        // Get the width and height and padding of the Interpolation kernel.
        interp_width = interp.getWidth();
        interp_height = interp.getHeight();
        interp_left = interp.getLeftPadding();
        interp_top = interp.getTopPadding();

        // Selection of the destination No Data
        switch (srcDataType) {
        case DataBuffer.TYPE_BYTE:
            destinationNoDataByte = (byte) (((byte) destinationNoDataDouble) & 0xff);

            if (hasNoData) {

                for (int i = 0; i < byteLookupTable.length; i++) {
                    byte value = (byte) i;
                    if (noData.contains(value)) {
                        byteLookupTable[i] = destinationNoDataByte;
                    } else {
                        byteLookupTable[i] = value;
                    }
                }
            }

            break;
        case DataBuffer.TYPE_USHORT:
            destinationNoDataUShort = (short) (((short) destinationNoDataDouble) & 0xffff);
            break;
        case DataBuffer.TYPE_SHORT:
            destinationNoDataShort = (short) destinationNoDataDouble;
            break;
        case DataBuffer.TYPE_INT:
            destinationNoDataInt = (int) destinationNoDataDouble;
            break;
        case DataBuffer.TYPE_FLOAT:
            destinationNoDataFloat = (float) destinationNoDataDouble;
            break;
        case DataBuffer.TYPE_DOUBLE:
            break;
        default:
            throw new IllegalArgumentException("Wrong data Type");
        }

        // Definition of the possible cases that can be found
        // caseA = no ROI nor No Data
        // caseB = ROI present but No Data not present
        // caseC = No Data present but ROI not present
        // Last case not defined = both ROI and No Data are present
        caseA = !hasROI && !hasNoData;
        caseB = hasROI && !hasNoData;
        caseC = !hasROI && hasNoData;
    }

    @Override
    protected void computeRect(Raster[] sources, WritableRaster dest, Rectangle destRect) {
        computeRect(sources, dest, destRect, null);
    }

    @Override
    protected void computeRect(Raster[] sources, WritableRaster dest, Rectangle destRect,
            Raster[] rois) {
        // Retrieve format tags.
        RasterFormatTag[] formatTags = getFormatTags();
        // Only one source raster is used
        Raster source = sources[0];

        // Get the source rectangle
        Rectangle srcRect = source.getBounds();

        // SRC and destination accessors are used for simplifying calculations
        RasterAccessor srcAccessor = new RasterAccessor(source, srcRect, formatTags[0],
                getSourceImage(0).getColorModel());

        RasterAccessor dstAccessor = new RasterAccessor(dest, destRect, formatTags[1],
                getColorModel());

        // Destination rectangle dimensions
        int dwidth = destRect.width;
        int dheight = destRect.height;
        // From the rasterAccessor are calculated the pixelStride and the scanLineStride
        int srcPixelStride = srcAccessor.getPixelStride();
        int srcScanlineStride = srcAccessor.getScanlineStride();
        // Initialization of the x and y position array
        int[] xpos = new int[dwidth];
        int[] ypos = new int[dheight];

        // ROI support
        int[] yposRoi = null;
        // Scanline stride. It is used as integer because it can return null values
        int roiScanlineStride = 0;
        // Roi rasterAccessor initialization
        RasterAccessor roiAccessor = null;
        // Roi raster initialization
        Raster roi = null;

        // ROI calculation only if the roi raster is present
        if (useRoiAccessor) {
            // Selection of the roi raster
            roi = rois[0];
            // creation of the rasterAccessor
            roiAccessor = new RasterAccessor(roi, srcRect, RasterAccessor.findCompatibleTags(
                    new RenderedImage[] { srcROIImage }, srcROIImage)[0],
                    srcROIImage.getColorModel());
            // ROI scanlinestride
            roiScanlineStride = roiAccessor.getScanlineStride();
            // Initialization of the roi y position array
            yposRoi = new int[dheight];

        }

        // Initialization of the x and y fractional array
        int[] xfracValues = new int[dwidth];
        int[] yfracValues = new int[dheight];

        // destination data type
        dataType = dest.getSampleModel().getDataType();

        preComputePositionsInt(destRect, srcRect.x, srcRect.y, srcPixelStride, srcScanlineStride,
                xpos, ypos, xfracValues, yfracValues, roiScanlineStride, yposRoi);

        // This methods differs only for the presence of the roi or if the image is a binary one

        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            byteLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        case DataBuffer.TYPE_USHORT:
            ushortLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        case DataBuffer.TYPE_SHORT:
            shortLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        case DataBuffer.TYPE_INT:
            intLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        case DataBuffer.TYPE_FLOAT:
            floatLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        case DataBuffer.TYPE_DOUBLE:
            doubleLoop(srcAccessor, destRect, dstAccessor, xpos, ypos, xfracValues, yfracValues,
                    roiAccessor, yposRoi, roiScanlineStride);
            break;
        }

    }

    private void byteLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final byte[][] srcDataArrays = src.getByteDataArrays();

        final byte[][] dstDataArrays = dst.getByteDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final byte[] srcData = srcDataArrays[k];
                final byte[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        long sum = 0;

                        int s = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            long temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                int pixelValue = srcData[pos + (z - 1) * srcPixelStride + (h - 1)
                                        * srcScanlineStride] & 0xff;
                                // Update of the temporary sum
                                temp += (pixelValue * dataHi[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += ((temp + round) >> precisionBits) * dataVi[offsetY + h];
                        }
                        // Interpolation
                        s = (int) ((sum + round) >> precisionBits);

                        // Clamp
                        if (s > 255) {
                            s = 255;
                        } else if (s < 0) {
                            s = 0;
                        }

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = (byte) (s & 0xff);

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final byte[] srcData = srcDataArrays[k];
                        final byte[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                int[][] pixelKernel = new int[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataByte;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride] & 0xff;
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((byte) (roiDataArray[index] & 0xff) != 0 ? 1
                                                        : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataByte;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > 255) {
                                            s = 255;
                                        } else if (s < 0) {
                                            s = 0;
                                        }

                                        dstData[dstPixelOffset] = (byte) (s & 0xff);
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final byte[] srcData = srcDataArrays[k];
                        final byte[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                int[][] pixelKernel = new int[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by 1 on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride] & 0xff;
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0) & 0xff;
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataByte;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > 255) {
                                            s = 255;
                                        } else if (s < 0) {
                                            s = 0;
                                        }

                                        dstData[dstPixelOffset] = (byte) (s & 0xff);
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataByte;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    final long[][] pixelKernel = new long[4][4];
                    final long[] sumArray = new long[4];
                    // final int[][] weightArray = new int[4][4]
                    short weight = 0;
                    // final int[] weightArrayVertical = new int[4];
                    byte weightVert = 0;
                    final long[] emptyArray = new long[4];

                    // Row temporary sum initialization
                    long tempSum = 0;
                    long sum = 0;
                    byte temp = 0;
                    // final result initialization
                    int s = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final byte[] srcData = srcDataArrays[k];
                        final byte[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        final int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            final int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            final int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                final int posx = xpos[i];

                                final int pos = posx + posy;

                                // X offset initialization
                                final int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {

                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride] & 0xff;

                                        if (byteLookupTable[(int) pixelKernel[h][z]] != destinationNoDataByte) {
                                            // temp++;
                                            weight |= (1 << (4 * h + z));
                                            // weightArray[h][z] = 1;
                                        } else {
                                            // weightArray[h][z] = 0;
                                            weight &= (0xffff - (1 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    final long[] tempData = bicubicInpainting(pixelKernel[h], temp,
                                            emptyArray);

                                    tempSum = tempData[0] * dataHi[offsetX] + tempData[1]
                                            * dataHi[offsetX + 1] + tempData[2]
                                            * dataHi[offsetX + 2] + tempData[3]
                                            * dataHi[offsetX + 3];

                                    // if ((weightArray[h][0] + weightArray[h][1] + weightArray[h][2] + weightArray[h][3]) > 0) {
                                    if (temp > 0) {
                                        weightVert |= (1 << h);
                                        // weightArrayVertical[h] = 1;
                                    } else {
                                        weightVert &= (0x0F - (1 << h));
                                        // weightArrayVertical[h] = 0;
                                    }
                                    sumArray[h] = ((tempSum + round) >> precisionBits);

                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataByte;
                                } else {
                                    // temp = 0;
                                    weight = 0;
                                    final long[] tempData = bicubicInpainting(sumArray, weightVert,
                                            emptyArray);
                                    weightVert = 0;

                                    // Vertical sum update
                                    tempSum = tempData[0] * dataVi[offsetY] + tempData[1]
                                            * dataVi[offsetY + 1] + tempData[2]
                                            * dataVi[offsetY + 2] + tempData[3]
                                            * dataVi[offsetY + 3];

                                    // Interpolation
                                    s = (int) ((sum + round) >> precisionBits);
                                    sum = 0;
                                    // Clamp
                                    if (s > 255) {
                                        s = 255;
                                    } else if (s < 0) {
                                        s = 0;
                                    }

                                    dstData[dstPixelOffset] = (byte) (s & 0xff);
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        // final int[][] weightArray = new int[4][4];
                        // final int[] weightArrayVertical = new int[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        final long[] emptyArray = new long[4];
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final byte[] srcData = srcDataArrays[k];
                            final byte[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataByte;
                                    } else {
                                        // int tempND = 0;
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride] & 0xff;

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((byte) (roiDataArray[index] & 0xff) != 0 ? 1
                                                            : 0);
                                                }

                                                if (byteLookupTable[(int) pixelKernel[h][z]] != destinationNoDataByte) {
                                                    // tempND++;
                                                    weight |= (0x01 << (4 * h + z));
                                                    // weightArray[h][z] = 1;
                                                } else {
                                                    // weightArray[h][z] = 0;
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataByte;
                                        } else {
                                            long sum = 0;
                                            int s = 0;
                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                // if ((weightArray[h][0] + weightArray[h][1]
                                                // + weightArray[h][2] + weightArray[h][3]) > 0) {
                                                // weightArrayVertical[h] = 1;
                                                // } else {
                                                // weightArrayVertical[h] = 0;
                                                // }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                    // weightArrayVertical[h] = 1;
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                    // weightArrayVertical[h] = 0;
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            // weight reset
                                            weightVert = 0;
                                            weight = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > 255) {
                                                s = 255;
                                            } else if (s < 0) {
                                                s = 0;
                                            }

                                            dstData[dstPixelOffset] = (byte) (s & 0xff);
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // final int[][] weightArray = new int[4][4];
                        // final int[] weightArrayVertical = new int[4];
                        final long[] emptyArray = new long[4];
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final byte[] srcData = srcDataArrays[k];
                            final byte[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.

                                        // int tempND = 0;
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride] & 0xff;

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0) & 0xff;

                                                if (byteLookupTable[(int) pixelKernel[h][z]] != destinationNoDataByte) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataByte;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            // weight reset
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > 255) {
                                                s = 255;
                                            } else if (s < 0) {
                                                s = 0;
                                            }

                                            dstData[dstPixelOffset] = (byte) (s & 0xff);
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataByte;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    private void ushortLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final short[][] srcDataArrays = src.getShortDataArrays();

        final short[][] dstDataArrays = dst.getShortDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final short[] srcData = srcDataArrays[k];
                final short[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        long sum = 0;

                        int s = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            long temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                int pixelValue = srcData[pos + (z - 1) * srcPixelStride + (h - 1)
                                        * srcScanlineStride] & 0xffff;
                                // Update of the temporary sum
                                temp += (pixelValue * dataHi[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += ((temp + round) >> precisionBits) * dataVi[offsetY + h];
                        }
                        // Interpolation
                        s = (int) ((sum + round) >> precisionBits);

                        // Clamp
                        if (s > 65536) {
                            s = 65536;
                        } else if (s < 0) {
                            s = 0;
                        }

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = (short) (s & 0xffff);

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                int[][] pixelKernel = new int[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataUShort;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride] & 0xffff;
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((short) (roiDataArray[index] & 0xffff) != 0 ? 1
                                                        : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataUShort;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > 65536) {
                                            s = 65536;
                                        } else if (s < 0) {
                                            s = 0;
                                        }

                                        dstData[dstPixelOffset] = (short) (s & 0xffff);
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                int[][] pixelKernel = new int[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride] & 0xffff;
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0) & 0xffff;
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataUShort;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > 65536) {
                                            s = 65536;
                                        } else if (s < 0) {
                                            s = 0;
                                        }

                                        dstData[dstPixelOffset] = (short) (s & 0xffff);
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataUShort;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    long[][] pixelKernel = new long[4][4];
                    long[] sumArray = new long[4];
                    long[] emptyArray = new long[4];
                    short weight = 0;
                    byte weightVert = 0;
                    byte temp = 0;
                    // Row temporary sum initialization
                    long tempSum = 0;
                    long sum = 0;
                    // final result initialization
                    int s = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // X offset initialization
                                int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {
                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride] & 0xffff;

                                        if (!noData.contains((short) pixelKernel[h][z])) {
                                            weight |= (0x01 << (4 * h + z));
                                        } else {
                                            weight &= (0xffff - (0x01 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    long[] tempData = bicubicInpainting(pixelKernel[h], temp,
                                            emptyArray);

                                    tempSum = tempData[0] * dataHi[offsetX] + tempData[1]
                                            * dataHi[offsetX + 1] + tempData[2]
                                            * dataHi[offsetX + 2] + tempData[3]
                                            * dataHi[offsetX + 3];

                                    if (temp > 0) {
                                        weightVert |= (0x01 << h);
                                    } else {
                                        weightVert &= (0x0F - (0x01 << h));
                                    }
                                    sumArray[h] = ((tempSum + round) >> precisionBits);

                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataUShort;
                                } else {
                                    temp = 0;
                                    long[] tempData = bicubicInpainting(sumArray, weightVert,
                                            emptyArray);
                                    weight = 0;
                                    weightVert = 0;
                                    // Vertical sum update
                                    sum = tempData[0] * dataVi[offsetY] + tempData[1]
                                            * dataVi[offsetY + 1] + tempData[2]
                                            * dataVi[offsetY + 2] + tempData[3]
                                            * dataVi[offsetY + 3];

                                    // Interpolation
                                    s = (int) ((sum + round) >> precisionBits);
                                    sum = 0;
                                    // Clamp
                                    if (s > 65536) {
                                        s = 65536;
                                    } else if (s < 0) {
                                        s = 0;
                                    }

                                    dstData[dstPixelOffset] = (short) (s & 0xffff);
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final short[] srcData = srcDataArrays[k];
                            final short[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataUShort;
                                    } else {
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride] & 0xffff;

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((short) (roiDataArray[index] & 0xffff) != 0 ? 1
                                                            : 0);
                                                }

                                                if (!noData.contains((short) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataUShort;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > 65536) {
                                                s = 65536;
                                            } else if (s < 0) {
                                                s = 0;
                                            }

                                            dstData[dstPixelOffset] = (short) (s & 0xffff);
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final short[] srcData = srcDataArrays[k];
                            final short[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.

                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride] & 0xffff;

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0) & 0xffff;

                                                if (!noData.contains((short) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataUShort;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > 65536) {
                                                s = 65536;
                                            } else if (s < 0) {
                                                s = 0;
                                            }

                                            dstData[dstPixelOffset] = (short) (s & 0xffff);
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataUShort;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    private void shortLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final short[][] srcDataArrays = src.getShortDataArrays();

        final short[][] dstDataArrays = dst.getShortDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final short[] srcData = srcDataArrays[k];
                final short[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        long sum = 0;

                        int s = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            long temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                int pixelValue = srcData[pos + (z - 1) * srcPixelStride + (h - 1)
                                        * srcScanlineStride];
                                // Update of the temporary sum
                                temp += (pixelValue * dataHi[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += ((temp + round) >> precisionBits) * dataVi[offsetY + h];
                        }
                        // Interpolation
                        s = (int) ((sum + round) >> precisionBits);

                        // Clamp
                        if (s > Short.MAX_VALUE) {
                            s = Short.MAX_VALUE;
                        } else if (s < Short.MIN_VALUE) {
                            s = Short.MIN_VALUE;
                        }

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = (short) s;

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                int[][] pixelKernel = new int[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataShort;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((short) (roiDataArray[index]) != 0 ? 1 : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataShort;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > Short.MAX_VALUE) {
                                            s = Short.MAX_VALUE;
                                        } else if (s < Short.MIN_VALUE) {
                                            s = Short.MIN_VALUE;
                                        }

                                        dstData[dstPixelOffset] = (short) s;
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                int[][] pixelKernel = new int[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0);
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataShort;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        // Clamp
                                        if (s > Short.MAX_VALUE) {
                                            s = Short.MAX_VALUE;
                                        } else if (s < Short.MIN_VALUE) {
                                            s = Short.MIN_VALUE;
                                        }

                                        dstData[dstPixelOffset] = (short) s;
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataShort;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    long[][] pixelKernel = new long[4][4];
                    long[] sumArray = new long[4];
                    long[] emptyArray = new long[4];

                    short weight = 0;
                    byte weightVert = 0;
                    byte temp = 0;
                    // Row temporary sum initialization
                    long tempSum = 0;
                    long sum = 0;
                    // final result initialization
                    int s = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final short[] srcData = srcDataArrays[k];
                        final short[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;
                                // X offset initialization
                                int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {
                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride];

                                        if (!noData.contains((short) pixelKernel[h][z])) {
                                            weight |= (0x01 << (4 * h + z));
                                        } else {
                                            weight &= (0xffff - (0x01 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    long[] tempData = bicubicInpainting(pixelKernel[h], temp,
                                            emptyArray);

                                    tempSum = tempData[0] * dataHi[offsetX] + tempData[1]
                                            * dataHi[offsetX + 1] + tempData[2]
                                            * dataHi[offsetX + 2] + tempData[3]
                                            * dataHi[offsetX + 3];

                                    if (temp > 0) {
                                        weightVert |= (0x01 << h);
                                    } else {
                                        weightVert &= (0x0F - (0x01 << h));
                                    }
                                    sumArray[h] = ((tempSum + round) >> precisionBits);

                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataShort;
                                } else {

                                    long[] tempData = bicubicInpainting(sumArray, weightVert,
                                            emptyArray);
                                    weight = 0;
                                    weightVert = 0;
                                    // Vertical sum update
                                    sum = tempData[0] * dataVi[offsetY] + tempData[1]
                                            * dataVi[offsetY + 1] + tempData[2]
                                            * dataVi[offsetY + 2] + tempData[3]
                                            * dataVi[offsetY + 3];

                                    // Interpolation
                                    s = (int) ((sum + round) >> precisionBits);
                                    sum = 0;
                                    // Clamp
                                    if (s > Short.MAX_VALUE) {
                                        s = Short.MAX_VALUE;
                                    } else if (s < Short.MIN_VALUE) {
                                        s = Short.MIN_VALUE;
                                    }

                                    dstData[dstPixelOffset] = (short) s;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final short[] srcData = srcDataArrays[k];
                            final short[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataShort;
                                    } else {
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((short) (roiDataArray[index]) != 0 ? 1
                                                            : 0);
                                                }

                                                if (!noData.contains((short) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataShort;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > Short.MAX_VALUE) {
                                                s = Short.MAX_VALUE;
                                            } else if (s < Short.MIN_VALUE) {
                                                s = Short.MIN_VALUE;
                                            }

                                            dstData[dstPixelOffset] = (short) s;
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];
                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final short[] srcData = srcDataArrays[k];
                            final short[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0);

                                                if (!noData.contains((short) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataShort;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            // Clamp
                                            if (s > Short.MAX_VALUE) {
                                                s = Short.MAX_VALUE;
                                            } else if (s < Short.MIN_VALUE) {
                                                s = Short.MIN_VALUE;
                                            }

                                            dstData[dstPixelOffset] = (short) s;
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataShort;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    private void intLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final int[][] srcDataArrays = src.getIntDataArrays();

        final int[][] dstDataArrays = dst.getIntDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final int[] srcData = srcDataArrays[k];
                final int[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        long sum = 0;

                        int s = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            long temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                int pixelValue = srcData[pos + (z - 1) * srcPixelStride + (h - 1)
                                        * srcScanlineStride];
                                // Update of the temporary sum
                                temp += (pixelValue * dataHi[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += ((temp + round) >> precisionBits) * dataVi[offsetY + h];
                        }
                        // Interpolation
                        s = (int) ((sum + round) >> precisionBits);

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = s;

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final int[] srcData = srcDataArrays[k];
                        final int[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                int[][] pixelKernel = new int[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataInt;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((roiDataArray[index]) != 0 ? 1 : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataInt;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        dstData[dstPixelOffset] = s;
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final int[] srcData = srcDataArrays[k];
                        final int[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                int[][] pixelKernel = new int[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0);
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataInt;
                                    } else {
                                        long sum = 0;
                                        int s = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            long tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHi[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += ((tempSum + round) >> precisionBits)
                                                    * dataVi[offsetY + h];
                                        }
                                        // Interpolation
                                        s = (int) ((sum + round) >> precisionBits);

                                        dstData[dstPixelOffset] = s;
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataInt;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    long[][] pixelKernel = new long[4][4];
                    long[] sumArray = new long[4];
                    long[] emptyArray = new long[4];

                    short weight = 0;
                    byte weightVert = 0;
                    byte temp = 0;

                    // Row temporary sum initialization
                    long tempSum = 0;
                    long sum = 0;
                    // final result initialization
                    int s = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final int[] srcData = srcDataArrays[k];
                        final int[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // X offset initialization
                                int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {
                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride];

                                        if (!noData.contains((int) pixelKernel[h][z])) {
                                            weight |= (0x01 << (4 * h + z));
                                        } else {
                                            weight &= (0xffff - (0x01 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    long[] tempData = bicubicInpainting(pixelKernel[h], temp,
                                            emptyArray);

                                    tempSum = tempData[0] * dataHi[offsetX] + tempData[1]
                                            * dataHi[offsetX + 1] + tempData[2]
                                            * dataHi[offsetX + 2] + tempData[3]
                                            * dataHi[offsetX + 3];

                                    if (temp > 0) {
                                        weightVert |= (0x01 << h);
                                    } else {
                                        weightVert &= (0x0F - (0x01 << h));
                                    }
                                    sumArray[h] = ((tempSum + round) >> precisionBits);
                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataShort;
                                } else {

                                    long[] tempData = bicubicInpainting(sumArray, weightVert,
                                            emptyArray);
                                    weight = 0;
                                    weightVert = 0;
                                    // Vertical sum update
                                    sum = tempData[0] * dataVi[offsetY] + tempData[1]
                                            * dataVi[offsetY + 1] + tempData[2]
                                            * dataVi[offsetY + 2] + tempData[3]
                                            * dataVi[offsetY + 3];

                                    // Interpolation
                                    s = (int) ((sum + round) >> precisionBits);
                                    sum = 0;
                                    dstData[dstPixelOffset] = s;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final int[] srcData = srcDataArrays[k];
                            final int[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataInt;
                                    } else {
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((roiDataArray[index]) != 0 ? 1 : 0);
                                                }

                                                if (!noData.contains((int) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataInt;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            dstData[dstPixelOffset] = s;
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final long[][] pixelKernel = new long[4][4];
                        final long[] sumArray = new long[4];
                        final long[] emptyArray = new long[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final int[] srcData = srcDataArrays[k];
                            final int[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0);

                                                if (!noData.contains((int) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataInt;
                                        } else {

                                            long sum = 0;
                                            int s = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                long tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                long[] tempData = bicubicInpainting(pixelKernel[h],
                                                        temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHi[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = ((tempSum + round) >> precisionBits);
                                            }

                                            long[] tempData = bicubicInpainting(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVi[offsetY + h];
                                            }

                                            // Interpolation
                                            s = (int) ((sum + round) >> precisionBits);

                                            dstData[dstPixelOffset] = s;
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataInt;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    private void floatLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final float[][] srcDataArrays = src.getFloatDataArrays();

        final float[][] dstDataArrays = dst.getFloatDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final float[] srcData = srcDataArrays[k];
                final float[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        double sum = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            double temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                float pixelValue = srcData[pos + (z - 1) * srcPixelStride + (h - 1)
                                        * srcScanlineStride];
                                // Update of the temporary sum
                                temp += (pixelValue * dataHf[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += temp * dataVf[offsetY + h];
                        }

                        // Clamp
                        if (sum > Float.MAX_VALUE) {
                            sum = Float.MAX_VALUE;
                        } else if (sum < -Float.MAX_VALUE) {
                            sum = -Float.MAX_VALUE;
                        }

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = (float) sum;

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final float[] srcData = srcDataArrays[k];
                        final float[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                float[][] pixelKernel = new float[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataFloat;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((roiDataArray[index]) != 0 ? 1 : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataFloat;
                                    } else {
                                        double sum = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            double tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHf[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += tempSum * dataVf[offsetY + h];
                                        }

                                        // Clamp
                                        if (sum > Float.MAX_VALUE) {
                                            sum = Float.MAX_VALUE;
                                        } else if (sum < -Float.MAX_VALUE) {
                                            sum = -Float.MAX_VALUE;
                                        }

                                        // The interpolated value is saved in the destination array
                                        dstData[dstPixelOffset] = (float) sum;
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final float[] srcData = srcDataArrays[k];
                        final float[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                float[][] pixelKernel = new float[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0);
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataFloat;
                                    } else {
                                        double sum = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            double tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHf[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += tempSum * dataVf[offsetY + h];
                                        }

                                        // Clamp
                                        if (sum > Float.MAX_VALUE) {
                                            sum = Float.MAX_VALUE;
                                        } else if (sum < -Float.MAX_VALUE) {
                                            sum = -Float.MAX_VALUE;
                                        }

                                        // The interpolated value is saved in the destination array
                                        dstData[dstPixelOffset] = (float) sum;
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataFloat;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    double[][] pixelKernel = new double[4][4];
                    double[] sumArray = new double[4];
                    double[] emptyArray = new double[4];

                    short weight = 0;
                    byte weightVert = 0;
                    byte temp = 0;

                    // Row temporary sum initialization
                    double tempSum = 0;
                    double sum = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final float[] srcData = srcDataArrays[k];
                        final float[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // X offset initialization
                                int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {
                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride];

                                        if (!noData.contains((float) pixelKernel[h][z])) {
                                            weight |= (0x01 << (4 * h + z));
                                        } else {
                                            weight &= (0xffff - (0x01 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    double[] tempData = bicubicInpaintingDouble(pixelKernel[h],
                                            temp, emptyArray);

                                    tempSum = tempData[0] * dataHf[offsetX] + tempData[1]
                                            * dataHf[offsetX + 1] + tempData[2]
                                            * dataHf[offsetX + 2] + tempData[3]
                                            * dataHf[offsetX + 3];

                                    if (temp > 0) {
                                        weightVert |= (0x01 << h);
                                    } else {
                                        weightVert &= (0x0F - (0x01 << h));
                                    }
                                    sumArray[h] = tempSum;

                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataFloat;
                                } else {

                                    double[] tempData = bicubicInpaintingDouble(sumArray,
                                            weightVert, emptyArray);
                                    weight = 0;
                                    weightVert = 0;
                                    // Vertical sum update
                                    sum = tempData[0] * dataVf[offsetY] + tempData[1]
                                            * dataVf[offsetY + 1] + tempData[2]
                                            * dataVf[offsetY + 2] + tempData[3]
                                            * dataVf[offsetY + 3];

                                    // Clamp
                                    if (sum > Float.MAX_VALUE) {
                                        sum = Float.MAX_VALUE;
                                    } else if (sum < -Float.MAX_VALUE) {
                                        sum = -Float.MAX_VALUE;
                                    }

                                    // The interpolated value is saved in the destination array
                                    dstData[dstPixelOffset] = (float) sum;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final double[][] pixelKernel = new double[4][4];
                        final double[] sumArray = new double[4];
                        final double[] emptyArray = new double[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final float[] srcData = srcDataArrays[k];
                            final float[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataFloat;
                                    } else {
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((roiDataArray[index]) != 0 ? 1 : 0);
                                                }

                                                if (!noData.contains((float) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataFloat;
                                        } else {

                                            double sum = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                double tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                double[] tempData = bicubicInpaintingDouble(
                                                        pixelKernel[h], temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHf[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = tempSum;
                                            }

                                            double[] tempData = bicubicInpaintingDouble(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVf[offsetY + h];
                                            }

                                            // Clamp
                                            if (sum > Float.MAX_VALUE) {
                                                sum = Float.MAX_VALUE;
                                            } else if (sum < -Float.MAX_VALUE) {
                                                sum = -Float.MAX_VALUE;
                                            }

                                            // The interpolated value is saved in the destination array
                                            dstData[dstPixelOffset] = (float) sum;
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final double[][] pixelKernel = new double[4][4];
                        final double[] sumArray = new double[4];
                        final double[] emptyArray = new double[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final float[] srcData = srcDataArrays[k];
                            final float[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0);

                                                if (!noData.contains((float) pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataFloat;
                                        } else {

                                            double sum = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                double tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                double[] tempData = bicubicInpaintingDouble(
                                                        pixelKernel[h], temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHf[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = tempSum;
                                            }

                                            double[] tempData = bicubicInpaintingDouble(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVf[offsetY + h];
                                            }

                                            // Clamp
                                            if (sum > Float.MAX_VALUE) {
                                                sum = Float.MAX_VALUE;
                                            } else if (sum < -Float.MAX_VALUE) {
                                                sum = -Float.MAX_VALUE;
                                            }

                                            // The interpolated value is saved in the destination array
                                            dstData[dstPixelOffset] = (float) sum;
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataFloat;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    private void doubleLoop(RasterAccessor src, Rectangle dstRect, RasterAccessor dst, int[] xpos,
            int[] ypos, int[] xfrac, int[] yfrac, RasterAccessor roi, int[] yposRoi,
            int roiScanlineStride) {

        // BandOffsets
        final int srcScanlineStride = src.getScanlineStride();
        final int srcPixelStride = src.getPixelStride();
        final int bandOffsets[] = src.getBandOffsets();
        // Destination rectangle dimensions
        final int dwidth = dstRect.width;
        final int dheight = dstRect.height;
        // Destination image band numbers
        final int dnumBands = dst.getNumBands();
        // Destination bandOffsets, PixelStride and ScanLineStride
        final int dstBandOffsets[] = dst.getBandOffsets();
        final int dstPixelStride = dst.getPixelStride();
        final int dstScanlineStride = dst.getScanlineStride();

        // Destination and source data arrays (for all bands)
        final double[][] srcDataArrays = src.getDoubleDataArrays();

        final double[][] dstDataArrays = dst.getDoubleDataArrays();

        final byte[] roiDataArray;
        final int roiDataLength;
        if (useRoiAccessor) {
            roiDataArray = roi.getByteDataArray(0);
            roiDataLength = roiDataArray.length;
        } else {
            roiDataArray = null;
            roiDataLength = 0;
        }

        if (caseA) {
            // for all bands
            for (int k = 0; k < dnumBands; k++) {

                final double[] srcData = srcDataArrays[k];
                final double[] dstData = dstDataArrays[k];
                // Line and band Offset initialization
                int dstlineOffset = dstBandOffsets[k];
                int bandOffset = bandOffsets[k];
                // cycle on the y values
                for (int j = 0; j < dheight; j++) {
                    // pixel offset initialization
                    int dstPixelOffset = dstlineOffset;
                    // y position selection
                    int posy = ypos[j] + bandOffset;
                    // Y offset initialization
                    int offsetY = 4 * yfrac[j];

                    // cycle on the x values
                    for (int i = 0; i < dwidth; i++) {
                        // x position selection
                        int posx = xpos[i];
                        int pos = posx + posy;

                        double sum = 0;
                        // X offset initialization
                        int offsetX = 4 * xfrac[i];
                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                        for (int h = 0; h < 4; h++) {
                            // Row temporary sum initialization
                            double temp = 0;
                            for (int z = 0; z < 4; z++) {
                                // Selection of one pixel
                                double pixelValue = srcData[pos + (z - 1) * srcPixelStride
                                        + (h - 1) * srcScanlineStride];
                                // Update of the temporary sum
                                temp += (pixelValue * dataHd[offsetX + z]);
                            }
                            // Vertical sum update
                            sum += temp * dataVd[offsetY + h];
                        }

                        // The interpolated value is saved in the destination array
                        dstData[dstPixelOffset] = sum;

                        // destination pixel offset update
                        dstPixelOffset += dstPixelStride;
                    }
                    // destination line offset update
                    dstlineOffset += dstScanlineStride;
                }
            }
        } else {
            if (caseB) {
                if (useRoiAccessor) {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final double[] srcData = srcDataArrays[k];
                        final double[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                double[][] pixelKernel = new double[4][4];

                                final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataDouble;
                                } else {
                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            int index = baseIndex - 1 + z + (h - 1)
                                                    * (roiScanlineStride);
                                            if (index < roiDataLength) {
                                                // Update of the weight sum
                                                temp += ((roiDataArray[index]) != 0 ? 1 : 0);
                                            }
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataDouble;
                                    } else {
                                        double sum = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            double tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHd[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += tempSum * dataVd[offsetY + h];
                                        }

                                        // The interpolated value is saved in the destination array
                                        dstData[dstPixelOffset] = sum;
                                    }
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {
                        final double[] srcData = srcDataArrays[k];
                        final double[] dstData = dstDataArrays[k];
                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // PixelPositions
                                int x0 = src.getX() + posx / srcPixelStride;
                                int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                double[][] pixelKernel = new double[4][4];

                                // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                // Otherwise it takes the related value.
                                if (roiBounds.contains(x0, y0)) {

                                    int temp = 0;
                                    // X offset initialization
                                    int offsetX = 4 * xfrac[i];
                                    // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                    // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                    // and by roiscanlinestride on the y axis.
                                    for (int h = 0; h < 4; h++) {
                                        for (int z = 0; z < 4; z++) {
                                            // Selection of one pixel
                                            pixelKernel[h][z] = srcData[pos + (z - 1)
                                                    * srcPixelStride + (h - 1) * srcScanlineStride];
                                            temp += roiIter.getSample(x0 + h - 1, y0 + z - 1, 0);
                                        }
                                    }
                                    // Control if the 16 pixel are outside the ROI
                                    if (temp == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataDouble;
                                    } else {
                                        double sum = 0;

                                        for (int h = 0; h < 4; h++) {
                                            // Row temporary sum initialization
                                            double tempSum = 0;
                                            for (int z = 0; z < 4; z++) {
                                                // Update of the temporary sum
                                                tempSum += (pixelKernel[h][z] * dataHd[offsetX + z]);
                                            }
                                            // Vertical sum update
                                            sum += tempSum * dataVd[offsetY + h];
                                        }

                                        // The interpolated value is saved in the destination array
                                        dstData[dstPixelOffset] = sum;
                                    }
                                } else {
                                    // The destination no data value is saved in the destination array
                                    dstData[dstPixelOffset] = destinationNoDataDouble;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }

                }
            } else {
                if (caseC) {
                    double[][] pixelKernel = new double[4][4];
                    double[] sumArray = new double[4];
                    double[] emptyArray = new double[4];

                    short weight = 0;
                    byte weightVert = 0;
                    byte temp = 0;

                    // Row temporary sum initialization
                    double tempSum = 0;
                    double sum = 0;

                    // for all bands
                    for (int k = 0; k < dnumBands; k++) {

                        final double[] srcData = srcDataArrays[k];
                        final double[] dstData = dstDataArrays[k];

                        // Line and band Offset initialization
                        int dstlineOffset = dstBandOffsets[k];
                        int bandOffset = bandOffsets[k];
                        // cycle on the y values
                        for (int j = 0; j < dheight; j++) {
                            // pixel offset initialization
                            int dstPixelOffset = dstlineOffset;
                            // y position selection
                            int posy = ypos[j] + bandOffset;
                            // Y offset initialization
                            int offsetY = 4 * yfrac[j];
                            // cycle on the x values
                            for (int i = 0; i < dwidth; i++) {
                                // x position selection
                                int posx = xpos[i];

                                int pos = posx + posy;

                                // X offset initialization
                                int offsetX = 4 * xfrac[i];
                                // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                // and check if the value is a No Data.
                                for (int h = 0; h < 4; h++) {
                                    for (int z = 0; z < 4; z++) {
                                        // Selection of one pixel
                                        pixelKernel[h][z] = srcData[pos + (z - 1) * srcPixelStride
                                                + (h - 1) * srcScanlineStride];

                                        if (!noData.contains(pixelKernel[h][z])) {
                                            weight |= (0x01 << (4 * h + z));
                                        } else {
                                            weight &= (0xffff - (0x01 << 4 * h + z));
                                        }
                                    }
                                    temp = (byte) ((weight >> 4 * h) & 0x0F);
                                    double[] tempData = bicubicInpaintingDouble(pixelKernel[h],
                                            temp, emptyArray);

                                    tempSum = tempData[0] * dataHd[offsetX] + tempData[1]
                                            * dataHd[offsetX + 1] + tempData[2]
                                            * dataHd[offsetX + 2] + tempData[3]
                                            * dataHd[offsetX + 3];

                                    if (temp > 0) {
                                        weightVert |= (0x01 << h);
                                    } else {
                                        weightVert &= (0x0F - (0x01 << h));
                                    }
                                    sumArray[h] = tempSum;

                                }
                                // Control if the 16 pixel are all No Data
                                if (weight == 0) {
                                    dstData[dstPixelOffset] = destinationNoDataDouble;
                                } else {

                                    double[] tempData = bicubicInpaintingDouble(sumArray,
                                            weightVert, emptyArray);
                                    weight = 0;
                                    weightVert = 0;
                                    // Vertical sum update
                                    sum = tempData[0] * dataVd[offsetY] + tempData[1]
                                            * dataVd[offsetY + 1] + tempData[2]
                                            * dataVd[offsetY + 2] + tempData[3]
                                            * dataVd[offsetY + 3];

                                    // The interpolated value is saved in the destination array
                                    dstData[dstPixelOffset] = sum;
                                }
                                // destination pixel offset update
                                dstPixelOffset += dstPixelStride;
                            }
                            // destination line offset update
                            dstlineOffset += dstScanlineStride;
                        }
                    }
                } else {
                    if (useRoiAccessor) {
                        final double[][] pixelKernel = new double[4][4];
                        final double[] sumArray = new double[4];
                        final double[] emptyArray = new double[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final double[] srcData = srcDataArrays[k];
                            final double[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    int pos = posx + posy;

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.

                                    final int baseIndex = (posx / dnumBands) + (yposRoi[j]);

                                    // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                    // Otherwise it takes the related value.
                                    if (baseIndex > roiDataLength || roiDataArray[baseIndex] == 0) {
                                        dstData[dstPixelOffset] = destinationNoDataDouble;
                                    } else {
                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                int index = baseIndex - 1 + z + (h - 1)
                                                        * (roiScanlineStride);
                                                if (index < roiDataLength) {
                                                    // Update of the weight sum
                                                    tempROI += ((roiDataArray[index]) != 0 ? 1 : 0);
                                                }

                                                if (!noData.contains(pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }
                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataDouble;
                                        } else {

                                            double sum = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                double tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                double[] tempData = bicubicInpaintingDouble(
                                                        pixelKernel[h], temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHd[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = tempSum;
                                            }

                                            double[] tempData = bicubicInpaintingDouble(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVd[offsetY + h];
                                            }
                                            // The interpolated value is saved in the destination array
                                            dstData[dstPixelOffset] = sum;
                                        }

                                    }
                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    } else {
                        final double[][] pixelKernel = new double[4][4];
                        final double[] sumArray = new double[4];
                        final double[] emptyArray = new double[4];

                        short weight = 0;
                        byte weightVert = 0;
                        byte temp = 0;
                        // for all bands
                        for (int k = 0; k < dnumBands; k++) {

                            final double[] srcData = srcDataArrays[k];
                            final double[] dstData = dstDataArrays[k];
                            // Line and band Offset initialization
                            int dstlineOffset = dstBandOffsets[k];
                            int bandOffset = bandOffsets[k];
                            // cycle on the y values
                            for (int j = 0; j < dheight; j++) {
                                // pixel offset initialization
                                int dstPixelOffset = dstlineOffset;
                                // y position selection
                                int posy = ypos[j] + bandOffset;
                                // Y offset initialization
                                int offsetY = 4 * yfrac[j];
                                // cycle on the x values
                                for (int i = 0; i < dwidth; i++) {
                                    // x position selection
                                    int posx = xpos[i];

                                    // PixelPositions
                                    int x0 = src.getX() + posx / srcPixelStride;
                                    int y0 = src.getY() + (posy - bandOffset) / srcScanlineStride;

                                    if (roiBounds.contains(x0, y0)) {

                                        int pos = posx + posy;

                                        // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
                                        // Otherwise it takes the related value.

                                        int tempROI = 0;
                                        // X offset initialization
                                        int offsetX = 4 * xfrac[i];
                                        // Cycle through all the 16 kernel pixel and calculation of the interpolated value
                                        // and cycle for filling all the ROI index by shifting of 1 on the x axis
                                        // and by roiscanlinestride on the y axis.
                                        for (int h = 0; h < 4; h++) {
                                            for (int z = 0; z < 4; z++) {
                                                // Selection of one pixel
                                                pixelKernel[h][z] = srcData[pos + (z - 1)
                                                        * srcPixelStride + (h - 1)
                                                        * srcScanlineStride];

                                                tempROI += roiIter.getSample(x0 + h - 1,
                                                        y0 + z - 1, 0);

                                                if (!noData.contains(pixelKernel[h][z])) {
                                                    weight |= (0x01 << (4 * h + z));
                                                } else {
                                                    weight &= (0xffff - (0x01 << 4 * h + z));
                                                }
                                            }
                                        }

                                        // Control if the 16 pixel are outside the ROI
                                        if (weight == 0 || tempROI == 0) {
                                            dstData[dstPixelOffset] = destinationNoDataDouble;
                                        } else {

                                            double sum = 0;

                                            for (int h = 0; h < 4; h++) {
                                                // Row temporary sum initialization
                                                double tempSum = 0;
                                                temp = (byte) ((weight >> 4 * h) & 0x0F);
                                                double[] tempData = bicubicInpaintingDouble(
                                                        pixelKernel[h], temp, emptyArray);
                                                for (int z = 0; z < 4; z++) {
                                                    // Update of the temporary sum
                                                    tempSum += (tempData[z] * dataHd[offsetX + z]);
                                                }
                                                if (temp > 0) {
                                                    weightVert |= (0x01 << h);
                                                } else {
                                                    weightVert &= (0x0F - (0x01 << h));
                                                }
                                                sumArray[h] = tempSum;
                                            }

                                            double[] tempData = bicubicInpaintingDouble(sumArray,
                                                    weightVert, emptyArray);
                                            weight = 0;
                                            weightVert = 0;
                                            // Vertical sum update
                                            for (int h = 0; h < 4; h++) {
                                                // Update of the temporary sum
                                                sum += tempData[h] * dataVd[offsetY + h];
                                            }

                                            // The interpolated value is saved in the destination array
                                            dstData[dstPixelOffset] = sum;
                                        }

                                    } else {
                                        dstData[dstPixelOffset] = destinationNoDataDouble;
                                    }

                                    // destination pixel offset update
                                    dstPixelOffset += dstPixelStride;
                                }
                                // destination line offset update
                                dstlineOffset += dstScanlineStride;
                            }
                        }
                    }
                }
            }
        }
    }

    // This method is used for filling the no data values inside the interpolation kernel with the values of the adjacent pixels
    private long[] bicubicInpainting(long[] array, short weightSum, long[] emptyArray) {
        // Absence of No Data, the pixels are returned.
        if (weightSum == 15) {
            return array;
        }

        long s_ = array[0];
        long s0 = array[1];
        long s1 = array[2];
        long s2 = array[3];

        emptyArray[0] = 0;
        emptyArray[1] = 0;
        emptyArray[2] = 0;
        emptyArray[3] = 0;

        switch (weightSum) {
        case 0:
            // 0 0 0 0
            break;
        case 1:
            // 0 0 0 x
            emptyArray[0] = s_;
            emptyArray[1] = s_;
            emptyArray[2] = s_;
            emptyArray[3] = s_;
            break;
        case 2:
            // 0 0 x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s0;
            emptyArray[3] = s0;
            break;
        case 3:
            // 0 0 x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = s0;
            emptyArray[3] = s0;
            break;
        case 4:
            // 0 x 0 0
            emptyArray[0] = s1;
            emptyArray[1] = s1;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 5:
            // 0 x 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s1) / 2;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 6:
            // 0 x x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 7:
            // 0 x x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 8:
            // x 0 0 0
            emptyArray[0] = s2;
            emptyArray[1] = s2;
            emptyArray[2] = s2;
            emptyArray[3] = s2;
            break;
        case 9:
            // x 0 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s2) / 2;
            emptyArray[2] = (s_ + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 10:
            // x 0 x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = (s0 + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 11:
            // x 0 x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = (s0 + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 12:
            // x x 0 0
            emptyArray[0] = s1;
            emptyArray[1] = s1;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        case 13:
            // x x 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s1) / 2;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        case 14:
            // x x x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        default:
            throw new IllegalArgumentException("Array cannot be composed from more than 4 elements");
        }

        return emptyArray;
    }

    // This method is used for filling the no data values inside the interpolation kernel with the values of the adjacent pixels
    private double[] bicubicInpaintingDouble(double[] array, short weightSum, double[] emptyArray) {
        // Absence of No Data, the pixels are returned.
        if (weightSum == 15) {
            return array;
        }

        double s_ = array[0];
        double s0 = array[1];
        double s1 = array[2];
        double s2 = array[3];

        emptyArray[0] = 0;
        emptyArray[1] = 0;
        emptyArray[2] = 0;
        emptyArray[3] = 0;

        switch (weightSum) {
        case 0:
            // 0 0 0 0
            break;
        case 1:
            // 0 0 0 x
            emptyArray[0] = s_;
            emptyArray[1] = s_;
            emptyArray[2] = s_;
            emptyArray[3] = s_;
            break;
        case 2:
            // 0 0 x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s0;
            emptyArray[3] = s0;
            break;
        case 3:
            // 0 0 x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = s0;
            emptyArray[3] = s0;
            break;
        case 4:
            // 0 x 0 0
            emptyArray[0] = s1;
            emptyArray[1] = s1;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 5:
            // 0 x 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s1) / 2;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 6:
            // 0 x x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 7:
            // 0 x x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s1;
            break;
        case 8:
            // x 0 0 0
            emptyArray[0] = s2;
            emptyArray[1] = s2;
            emptyArray[2] = s2;
            emptyArray[3] = s2;
            break;
        case 9:
            // x 0 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s2) / 2;
            emptyArray[2] = (s_ + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 10:
            // x 0 x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = (s0 + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 11:
            // x 0 x x
            emptyArray[0] = s_;
            emptyArray[1] = s0;
            emptyArray[2] = (s0 + s2) / 2;
            emptyArray[3] = s2;
            break;
        case 12:
            // x x 0 0
            emptyArray[0] = s1;
            emptyArray[1] = s1;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        case 13:
            // x x 0 x
            emptyArray[0] = s_;
            emptyArray[1] = (s_ + s1) / 2;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        case 14:
            // x x x 0
            emptyArray[0] = s0;
            emptyArray[1] = s0;
            emptyArray[2] = s1;
            emptyArray[3] = s2;
            break;
        default:
            throw new IllegalArgumentException("Array cannot be composed from more than 4 elements");
        }

        return emptyArray;
    }
}

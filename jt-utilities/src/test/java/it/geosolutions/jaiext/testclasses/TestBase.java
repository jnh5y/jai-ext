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
package it.geosolutions.jaiext.testclasses;


import java.awt.Rectangle;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;

import javax.media.jai.ROIShape;
import javax.media.jai.TiledImage;


/**
 * This class is an abstract class used for creating test images used by the affine and scale operation test-classes. The 
 * only two methods defined are roiCreation() and createTestImage(). The first is used for creating a new ROI object with 
 * height and width respectively half of the image height and width. The second is used for creating a test image of the 
 * selected data type. The image can be filled with data inside by setting the JAI.Ext.ImageFill parameter to true from 
 * the console, but this slows the test-computations. The image is by default a cross surrounded by lots of pixel with 
 * value 0. For binary images a big rectangle is added into the left half of the image; for not-binary images a simple 
 * square is added in the upper left of the image. The square is  useful for the rotate operation(inside the jt-affine 
 * project) because it shows if the image has been correctly rotated.   
 */
public abstract class TestBase {

    /** Default value for image width */
    public static int DEFAULT_WIDTH = 256;

    /** Default value for image height */
    public static int DEFAULT_HEIGHT = 256;

    /**
     * Default value for subsample bits
     * */
    public static final int DEFAULT_SUBSAMPLE_BITS = 8;

    /**
     * Default value for precision bits
     * */
    public static final int DEFAULT_PRECISION_BITS = 8;

    public static boolean INTERACTIVE = Boolean.getBoolean("JAI.Ext.Interactive");
    
    public static boolean IMAGE_FILLER = Boolean.getBoolean("JAI.Ext.ImageFill");
    
    public static Integer INVERSE_SCALE = Integer.getInteger("JAI.Ext.InverseScale",0);

    public static Integer TEST_SELECTOR = Integer.getInteger("JAI.Ext.TestSelector",0);

    protected double destinationNoData;

    protected float transX = 0;

    protected float transY = 0;

    protected float scaleX = 0.5f;

    protected float scaleY = 0.5f;

    public enum InterpolationType {
        NEAREST_INTERP(0), BILINEAR_INTERP(1), BICUBIC_INTERP(2),GENERAL_INTERP(3);
        
        private int type;
        
        InterpolationType(int type){
            this.type=type;
        }
        
        public int getType() {
            return type;
        }
    }

    public enum ScaleType {
        MAGNIFY(0), REDUCTION(1);
        
        private int type;
        
        ScaleType(int type){
            this.type=type;
        }
        
        public int getType() {
            return type;
        }
    }
    
    
    public enum TestSelection {
        NO_ROI_ONLY_DATA(0)
        , ROI_ACCESSOR_ONLY_DATA(1)
        , ROI_ONLY_DATA(2)
        , ROI_ACCESSOR_NO_DATA(3)
        ,NO_ROI_NO_DATA(4)
        ,ROI_NO_DATA(5)
        ,BINARY_ROI_ACCESSOR_NO_DATA(6);

        private int type;

        TestSelection(int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }

    public enum TransformationType {
        ROTATE_OP(0), TRANSLATE_OP(2), SCALE_OP(1), ALL(3);

        private int value;

        TransformationType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }  
    
    protected ROIShape roiCreation() {
        int roiHeight = DEFAULT_HEIGHT *3/ 4;
        int roiWidth = DEFAULT_WIDTH *3/ 4;

        Rectangle roiBound = new Rectangle(0, 0, roiWidth, roiHeight);

        ROIShape roi = new ROIShape(roiBound);
        return roi;
    }

    
    /** Simple method for image creation */
    public static RenderedImage createTestImage(int dataType, int width, int height, Number noDataValue,
            boolean isBinary) {
        
        return createTestImage(dataType, width,height, noDataValue, isBinary, 3);
    }
    
    /** Simple method for image creation */
    public static RenderedImage createTestImage(int dataType, int width, int height, Number noDataValue,
            boolean isBinary, int bands) {
        // This values could be used for fill all the image
        byte valueB = 64;
        short valueUS = Short.MAX_VALUE / 4;
        short valueS = -50;
        int valueI = 100;
        float valueF = (255 / 2) * 5;
        double valueD = (255 / 1) * 4;

        boolean fillImage = IMAGE_FILLER;
        
        // parameter block initialization
        int tileW = width / 8;
        int tileH = height / 8;
        int imageDim = width * height;

        final SampleModel sm;

        Byte crossValueByte = null;
        Short crossValueUShort = null;
        Short crossValueShort = null;
        Integer crossValueInteger = null;
        Float crossValueFloat = null;
        Double crossValueDouble = null;

        int numBands = bands;

        if (isBinary) {
            // Binary images Sample Model
            sm = new MultiPixelPackedSampleModel(dataType, width, height, 1);
            numBands = 1;
        } else {
            
            if(numBands == 3){
                sm = new ComponentSampleModel(dataType, width, height, 3, width, new int[] { 0,
                        imageDim, imageDim * 2 }); 
            }else{
                sm = new ComponentSampleModel(dataType, width, height, 1, width, new int[] {0}); 
            }
            
                           
        }

        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            crossValueByte = (Byte) noDataValue;
            break;
        case DataBuffer.TYPE_USHORT:
            crossValueUShort = (Short) noDataValue;
            break;
        case DataBuffer.TYPE_SHORT:
            crossValueShort = (Short) noDataValue;
            break;
        case DataBuffer.TYPE_INT:
            crossValueInteger = (Integer) noDataValue;
            break;
        case DataBuffer.TYPE_FLOAT:
            crossValueFloat = (Float) noDataValue;
            break;
        case DataBuffer.TYPE_DOUBLE:
            crossValueDouble = (Double) noDataValue;
            break;
        default:
            throw new IllegalArgumentException("Wrong data type");
        }

        // Create the constant operation.
        TiledImage used = new TiledImage(sm, tileW, tileH);

        int imgBinX=width/4;
        int imgBinY=height/4;
        
        int imgBinWidth=imgBinX + width/4;
        int imgBinHeight=imgBinY + height/4;
        
        for (int b = 0; b < numBands; b++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < height; k++) {
                    //Addition of a cross on the image
                    if (j == k || j == width - k - 1) {
                        switch (dataType) {
                        case DataBuffer.TYPE_BYTE:
                            used.setSample(j, k, b, crossValueByte);
                            break;
                        case DataBuffer.TYPE_USHORT:
                            used.setSample(j, k, b, crossValueUShort);
                            break;
                        case DataBuffer.TYPE_SHORT:
                            used.setSample(j, k, b, crossValueShort);
                            break;
                        case DataBuffer.TYPE_INT:
                            used.setSample(j, k, b, crossValueInteger);
                            break;
                        case DataBuffer.TYPE_FLOAT:
                            used.setSample(j, k, b, crossValueFloat);
                            break;
                        case DataBuffer.TYPE_DOUBLE:
                            used.setSample(j, k, b, crossValueDouble);
                            break;
                        default:
                            throw new IllegalArgumentException("Wrong data type");
                        }
                      //If selected, the image could be filled
                    } else if (!isBinary && fillImage) {
                    	// a little square of no data on the upper left is inserted
                        if( (j>=20) && (j<50) && (k>=20) && (k<50)){
                            switch (dataType) {
                            case DataBuffer.TYPE_BYTE:
                                used.setSample(j, k, b, 0);
                                break;
                            case DataBuffer.TYPE_USHORT:
                                used.setSample(j, k, b, 0);
                                break;
                            case DataBuffer.TYPE_SHORT:
                                used.setSample(j, k, b, 0);
                                break;
                            case DataBuffer.TYPE_INT:
                                used.setSample(j, k, b, 0);
                                break;
                            case DataBuffer.TYPE_FLOAT:
                                used.setSample(j, k, b, 0);
                                break;
                            case DataBuffer.TYPE_DOUBLE:
                                used.setSample(j, k, b,0);
                                break;
                            default:
                                throw new IllegalArgumentException("Wrong data type");
                            }
                            
                            if( (j>=30) && (j<40) && (k>=20) && (k<30)){
                                switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    used.setSample(j, k, b, crossValueByte);
                                    break;
                                case DataBuffer.TYPE_USHORT:
                                    used.setSample(j, k, b, crossValueUShort);
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                    used.setSample(j, k, b, crossValueShort);
                                    break;
                                case DataBuffer.TYPE_INT:
                                    used.setSample(j, k, b, crossValueInteger);
                                    break;
                                case DataBuffer.TYPE_FLOAT:
                                    used.setSample(j, k, b, crossValueFloat);
                                    break;
                                case DataBuffer.TYPE_DOUBLE:
                                    used.setSample(j, k, b, crossValueDouble);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Wrong data type");
                                }
                            }
                            
                            
                        }else{
                            switch (dataType) {
                            case DataBuffer.TYPE_BYTE:
                                used.setSample(j, k, b, valueB + b);
                                break;
                            case DataBuffer.TYPE_USHORT:
                                used.setSample(j, k, b, valueUS + b);
                                break;
                            case DataBuffer.TYPE_SHORT:
                                used.setSample(j, k, b, valueS + b);
                                break;
                            case DataBuffer.TYPE_INT:
                                used.setSample(j, k, b, valueI + b);
                                break;
                            case DataBuffer.TYPE_FLOAT:
                                float data = valueF + b / 3.0f;
                                used.setSample(j, k, b, data);
                                break;
                            case DataBuffer.TYPE_DOUBLE:
                                double dataD = valueD + b / 3.0d;
                                used.setSample(j, k, b, dataD);
                                break;
                            default:
                                throw new IllegalArgumentException("Wrong data type");
                            }
                        }
                      //If is binary a rectangle is added
                    }else if (isBinary && (j>imgBinX && j<imgBinWidth) && (j>imgBinY && j<imgBinHeight)) {
                            switch (dataType) {
                            case DataBuffer.TYPE_BYTE:
                                used.setSample(j, k, b, crossValueByte);
                                break;
                            case DataBuffer.TYPE_USHORT:
                                used.setSample(j, k, b, crossValueUShort);
                                break;
                            case DataBuffer.TYPE_SHORT:
                                used.setSample(j, k, b, crossValueShort);
                                break;
                            case DataBuffer.TYPE_INT:
                                used.setSample(j, k, b, crossValueInteger);
                                break;
                            default:
                                throw new IllegalArgumentException("Wrong data type");
                            }
                            // Else, a little square of no data on the upper left is inserted
                        }else{
                            if( (j>=2) && (j<10) && (k>=2) && (k<10)){
                                switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    used.setSample(j, k, b, crossValueByte);
                                    break;
                                case DataBuffer.TYPE_USHORT:
                                    used.setSample(j, k, b, crossValueUShort);
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                    used.setSample(j, k, b, crossValueShort);
                                    break;
                                case DataBuffer.TYPE_INT:
                                    used.setSample(j, k, b, crossValueInteger);
                                    break;
                                case DataBuffer.TYPE_FLOAT:
                                    used.setSample(j, k, b, crossValueFloat);
                                    break;
                                case DataBuffer.TYPE_DOUBLE:
                                    used.setSample(j, k, b, crossValueDouble);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Wrong data type");
                                }
                            }
                            
                            if( (j>=150) && (j<170) && (k>=90) && (k<110)){
                                switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    used.setSample(j, k, b, crossValueByte);
                                    break;
                                case DataBuffer.TYPE_USHORT:
                                    used.setSample(j, k, b, crossValueUShort);
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                    used.setSample(j, k, b, crossValueShort);
                                    break;
                                case DataBuffer.TYPE_INT:
                                    used.setSample(j, k, b, crossValueInteger);
                                    break;
                                case DataBuffer.TYPE_FLOAT:
                                    used.setSample(j, k, b, crossValueFloat);
                                    break;
                                case DataBuffer.TYPE_DOUBLE:
                                    used.setSample(j, k, b, crossValueDouble);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Wrong data type");
                                }
                            }
                            
                        }                    
                }
            }
        }
        return used;
    }

}

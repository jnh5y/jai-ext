/* JAI-Ext - OpenSource Java Advanced Image Extensions Library
 *    http://www.geo-solutions.it/
 *    Copyright 2018 GeoSolutions
 *    
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

/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2016, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package it.geosolutions.jaiext.classbreaks;

import java.awt.image.RenderedImage;

import org.eclipse.imagen.OperationDescriptorImpl;
import org.eclipse.imagen.ROI;
import org.eclipse.imagen.registry.RenderedRegistryMode;

/** Operation descriptor for the ClassBreaks operation. */
public class ClassBreaksDescriptor extends OperationDescriptorImpl {

    public static final String CLASSIFICATION_PROPERTY = "Classification";

    public static final String NAME = "ClassBreaks";

    static final int NUM_CLASSES_ARG = 0;
    static final int METHOD_ARG = 1;
    static final int EXTREMA_ARG = 2;
    static final int ROI_ARG = 3;
    static final int BAND_ARG = 4;
    static final int X_PERIOD_ARG = 5;
    static final int Y_PERIOD_ARG = 6;
    static final int NODATA_ARG = 7;
    static final int HISTOGRAM_ARG = 8;
    static final int HISTOGRAM_BINS = 9;

    static String[] paramNames =
            new String[] {
                "numClasses",
                "method",
                "extrema",
                "roi",
                "band",
                "xPeriod",
                "yPeriod",
                "noData",
                "histogram",
                "histogramBins"
            };

    static final Class<?>[] paramClasses = {
        Integer.class,
        ClassificationMethod.class,
        Double[][].class,
        ROI.class,
        Integer[].class,
        Integer.class,
        Integer.class,
        Double.class,
        Boolean.class,
        Integer.class
    };

    static final Object[] paramDefaults = {
        10,
        ClassificationMethod.EQUAL_INTERVAL,
        null,
        (ROI) null,
        new Integer[] {Integer.valueOf(0)},
        1,
        1,
        null,
        false,
        256
    };

    public ClassBreaksDescriptor() {
        super(
                new String[][] {
                    {"GlobalName", NAME},
                    {"LocalName", NAME},
                    {"Vendor", "it.geosolutions.jaiext"},
                    {
                        "Description",
                        "Classifies image values using equal interval method and calculates "
                                + "statistics for each class"
                    },
                    {"DocURL", ""},
                    {"Version", "1.0"},
                    {
                        String.format("arg%dDesc", NUM_CLASSES_ARG),
                        String.format("%s - number of classes or bins", paramNames[NUM_CLASSES_ARG])
                    },
                    {
                        String.format("arg%dDesc", METHOD_ARG),
                        String.format("%s - classification method", paramNames[METHOD_ARG])
                    },
                    {
                        String.format("arg%dDesc", EXTREMA_ARG),
                        String.format("%s - range of values to include", paramNames[EXTREMA_ARG])
                    },
                    {
                        String.format("arg%dDesc", ROI_ARG),
                        String.format(
                                "%s (default %s) - region-of-interest constrainting the values to be counted",
                                paramNames[ROI_ARG], paramDefaults[ROI_ARG])
                    },
                    {
                        String.format("arg%dDesc", BAND_ARG),
                        String.format(
                                "%s (default %s) - bands of the image to process",
                                paramNames[BAND_ARG], paramDefaults[BAND_ARG])
                    },
                    {
                        String.format("arg%dDesc", X_PERIOD_ARG),
                        String.format(
                                "%s (default %s) - horizontal sampling rate",
                                paramNames[X_PERIOD_ARG], paramDefaults[X_PERIOD_ARG])
                    },
                    {
                        String.format("arg%dDesc", Y_PERIOD_ARG),
                        String.format(
                                "%s (default %s) - vertical sampling rate",
                                paramNames[Y_PERIOD_ARG], paramDefaults[Y_PERIOD_ARG])
                    },
                    {
                        String.format("arg%dDesc", NODATA_ARG),
                        String.format(
                                "%s (default %s) - value to treat as NODATA",
                                paramNames[NODATA_ARG], paramDefaults[NODATA_ARG])
                    },
                    {
                        String.format("arg%dDesc", HISTOGRAM_ARG),
                        String.format(
                                "%s (default %s) - if true, a histogram based computation will be used",
                                paramNames[HISTOGRAM_ARG], paramDefaults[HISTOGRAM_ARG])
                    },
                    {
                        String.format("arg%dDesc", HISTOGRAM_BINS),
                        String.format(
                                "%s (default %s) - if true, number of histogram bins to be used",
                                paramNames[HISTOGRAM_BINS], paramDefaults[HISTOGRAM_BINS])
                    },
                },
                new String[] {RenderedRegistryMode.MODE_NAME},
                new String[] {"source0"},
                new Class<?>[][] {{RenderedImage.class}},
                paramNames,
                paramClasses,
                paramDefaults,
                null // valid values (none defined)
                );
    }
}

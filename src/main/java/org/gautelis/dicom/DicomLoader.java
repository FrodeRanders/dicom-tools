/*
 * Copyright (C) 2016-2021 Frode Randers
 * All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gautelis.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.gautelis.dicom.model.DicomDocument;
import org.gautelis.dicom.model.DicomElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Predicate;

/**
 * Loads DICOM files
 */
public class DicomLoader {
    private static final Logger log = LoggerFactory.getLogger(DicomLoader.class);

    protected DicomDocument dicomDocument = null;

    public interface FileLoader {
        DicomDocument load(final Attributes dataset, final File file, final DicomElement parent) throws IOException, InconsistencyException;
    }

    public interface StreamLoader {
        DicomDocument load(final Attributes dataset, final String name, final DicomElement parent, final InputStream inputStream) throws IOException, InconsistencyException;
    }


    public static final FileLoader defaultFileLoader = (dataset, file, parent) -> {
        String name = file.getName();
        String path = file.getPath();
        DicomDocument dicomDocument = new DicomDocument(new DicomElement(name, dataset, parent), name, path);
        return dicomDocument;
    };

    public static final StreamLoader defaultStreamLoader = (dataset, name, parent, inputStream) -> {
        DicomDocument dicomDocument = new DicomDocument(new DicomElement(name, dataset, parent), name, /* no file, so no path */ null);
        return dicomDocument;
    };

    public DicomLoader() {
    }

    /**
     * Load a DICOM file from disk.
     *
     * @param loader the loader that will be used to load the file
     * @param file the file itself
     * @param parent the parent "element" into which the object on file is loaded, if applicable
     * @throws IOException In case of IO error.
     * @throws InconsistencyException In case of inconsistencies.
     */
    public void load(final FileLoader loader, final File file, final DicomElement parent) throws IOException, InconsistencyException {
        try (DicomInputStream dicomInputStream = new DicomInputStream(Files.newInputStream(file.toPath()))) {
            Attributes ds = dicomInputStream.readDataset();
            dicomDocument = loader.load(ds, file, parent);
        }
    }

    /**
     * Load a DICOM file from a stream.
     *
     * @param loader the loader that will be used to load the stream
     * @param name the name of the stream
     * @param inputStream the stream itself
     * @param parent the parent "element" into which the object in stream is loaded, if applicable
     * @throws IOException In case of IO error.
     * @throws InconsistencyException In case of inconsistencies.
     */
    public void load(final StreamLoader loader, final String name, final InputStream inputStream, final DicomElement parent) throws IOException, InconsistencyException {
        try (DicomInputStream dicomInputStream = new DicomInputStream(inputStream)) {
            Attributes ds = dicomInputStream.readDataset();
            dicomDocument = loader.load(ds, name, parent, /* no file, so no path */ null);
        }
    }

    /**
     * Load a DICOM file from file on disk.
     *
     * @param file the file itself
     * @throws IOException In case of IO error.
     * @throws InconsistencyException In case of inconsistencies.
     */
    public void load(final File file) throws IOException, InconsistencyException {
        load(defaultFileLoader, file, /* no parent */ null);
    }

    /**
     * Load a DICOM file from disk.
     *
     * @param loader the loader that will be used to load the file
     * @param file the file itself
     * @throws IOException In case of IO error.
     * @throws InconsistencyException In case of inconsistencies.
     */
    public void load(final FileLoader loader, final File file) throws IOException, InconsistencyException {
        load(loader, file, /* no parent */ null);
    }

    /**
     * Load a DICOM file from a stream.
     *
     * @param loader the loader that will be used to load the stream
     * @param name the name of the stream
     * @param inputStream the stream itself
     * @throws IOException In case of IO error.
     * @throws InconsistencyException In case of inconsistencies.
     */
    public void load(final StreamLoader loader, final String name, final InputStream inputStream) throws IOException, InconsistencyException {
        load(loader, name, inputStream, /* no parent */ null);
    }


    public DicomDocument getDicomDocument() {
        return dicomDocument;
    }
}



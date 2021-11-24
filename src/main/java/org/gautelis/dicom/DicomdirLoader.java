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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.gautelis.dicom.model.DicomDocument;
import org.gautelis.dicom.model.DicomElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads DICOMDIR files
 */
public class DicomdirLoader extends DicomLoader {
    private static final Logger log = LoggerFactory.getLogger(DicomdirLoader.class);

    private final Collection<DicomDocument> loadedFiles = new ArrayList<>();
    private final boolean loadReferencedFiles;

    /**
     * Assigns key/value to data _and_ verifies that data is consistent,
     * if the key has been assigned a value earlier.
     * <p/>
     *
     * @param data
     * @param key
     * @param value
     * @throws InconsistencyException
     */
    private static void assign(Map<String, String> data, String key, String value) throws InconsistencyException {
        if (null == value) {
            return;
        }

        if (data.containsKey(key)) {
            if (!value.equals(data.get(key))) {
                String info = "Inconsistent value: key=\"" + key + "\", existing-value=\"" + data.get(key) + "\" new-value=\"" + value + "\"";
                throw new InconsistencyException(info);
            }
        } else {
            data.put(key, value);
        }
    }

    public final FileLoader dicomdirFileLoader = (dataset, file, parent) -> {
        DicomElement topObject = new DicomElement(file.getName(), dataset, parent);
        DicomDocument dicomdirFile = new DicomDocument(topObject, file.getName(), file.getPath());
        loadContent(dicomdirFile, dataset, file.getParentFile().getPath());
        return dicomdirFile;
    };

    public final StreamLoader dicomdirStreamLoader = (dataset, name, parent, inputStream) -> {
        DicomElement topObject = new DicomElement(name, dataset, parent);
        DicomDocument dicomdirFile = new DicomDocument(topObject, name, /* no file, so no path */ null);
        loadContent(dicomdirFile, dataset, /* no path */ null);
        return dicomdirFile;
    };


    private void loadContent(final DicomDocument dicomdirFile, final Attributes dataset, final String parentPath) throws IOException, InconsistencyException {

        Map<String, String> data = new HashMap<>();
        loadedFiles.add(dicomdirFile);

        // (0004,1220) DirectoryRecordSequence
        Sequence sequence = dataset.getSequence(TagUtils.toTag(0x0004, 0x1220));
        if (null != sequence) {
            for (Attributes recordInSequence : sequence) {
                Attributes record = recordInSequence;
                String recordType = DicomElement.directoryRecordType(record);

                switch (recordType) {
                    case "PATIENT":
                        assign(data, "PatientID", DicomElement.patientID(record));
                        break;

                    case "STUDY":
                        assign(data, "StudyInstanceUID", DicomElement.studyInstanceUID(record));
                        break;

                    case "SR DOCUMENT": {
                        // In case information is replicated
                        assign(data, "PatientID", DicomElement.patientID(record));
                        assign(data, "StudyInstanceUID", DicomElement.studyInstanceUID(record));

                        /*
                        String sopInstanceUid = DicomObject.sopInstanceUID(record);
                        String modality = DicomObject.modality(record);
                        String physicianName = DicomObject.performingPhysicianName(record);
                        */

                        File referencedFile = (null != parentPath ? new File(parentPath) : new File(".")); // Start relative to DICOMDIR
                        String[] referencedFileId = record.getStrings(TagUtils.toTag(0x0004, 0x1500));
                        if (null != referencedFileId) {
                            for (String part : referencedFileId) {
                                referencedFile = new File(referencedFile, part);
                            }
                        }

                        if (referencedFile.exists() && referencedFile.canRead()) {
                            String info = "Referencing file: " + referencedFile.getPath();
                            log.info(info);

                            if (loadReferencedFiles) {
                                try (DicomInputStream dicomInputStream = new DicomInputStream(new FileInputStream(referencedFile))) {
                                    Attributes ds = dicomInputStream.readDataset(-1, -1);
                                    loadedFiles.add(DicomLoader.defaultFileLoader.load(ds, referencedFile, dicomdirFile.getRootElement()));
                                }
                            }
                        } else if (loadReferencedFiles) {
                            String info = "Referenced file does not exist: " + referencedFile.getPath();
                            log.warn(info);
                        }
                    }
                    break;

                    case "IMAGE": {
                        // In case information is replicated
                        assign(data, "PatientID", DicomElement.patientID(record));
                        assign(data, "StudyInstanceUID", DicomElement.studyInstanceUID(record));

                        /*
                        String sopInstanceUid = DicomObject.sopInstanceUID(record);
                        String modality = DicomObject.modality(record);
                        String physicianName = DicomObject.performingPhysicianName(record);
                        */

                        File referencedFile = (null != parentPath ? new File(parentPath) : new File(".")); // Start relative to DICOMDIR
                        String[] referencedFileId = record.getStrings(TagUtils.toTag(0x0004, 0x1500));
                        if (null != referencedFileId) {
                            for (String part : referencedFileId) {
                                referencedFile = new File(referencedFile, part);
                            }
                        }

                        if (referencedFile.exists() && referencedFile.canRead()) {
                            String info = "Referencing file: " + referencedFile.getPath();
                            log.info(info);

                            if (loadReferencedFiles) {
                                try (DicomInputStream dicomInputStream = new DicomInputStream(new FileInputStream(referencedFile))) {
                                    Attributes ds = dicomInputStream.readDataset(-1, -1);
                                    DicomElement owner = dicomdirFile.getRootElement();

                                    // Load referenced document
                                    DicomDocument referencedDoc = DicomLoader.defaultFileLoader.load(ds, referencedFile, owner);
                                    loadedFiles.add(referencedDoc);

                                    // Add as child of
                                    owner.getChildren().add(referencedDoc.getRootElement());

                                }
                            }
                        } else if (loadReferencedFiles) {
                            String info = "Referenced file does not exist: " + referencedFile.getPath();
                            log.warn(info);
                        }
                    }
                    break;

                    case "SERIES":
                        String seriesInstanceUid = DicomElement.seriesInstanceUID(record);
                        String seriesDescription = DicomElement.seriesDescription(record);
                        // fall through

                    default:
                        // In case information is replicated
                        assign(data, "PatientID", DicomElement.patientID(record));
                        assign(data, "StudyInstanceUID", DicomElement.studyInstanceUID(record));
                        break;
                }
            }
        }
    }

    public DicomdirLoader(boolean loadReferencedFiles) {
        this.loadReferencedFiles = loadReferencedFiles;
    }

    public void load(final File file) throws IOException, InconsistencyException {
        load(dicomdirFileLoader, file, /* no parent */ null);
    }

    public void load(final String name, final InputStream inputStream) throws IOException, InconsistencyException {
        load(dicomdirStreamLoader, name, inputStream, /* no parent */ null);
    }

    /**
     * Get all DICOM files associated with DICOMDIR (including the DICOMDIR file itself)
     *
     * @return The list of classes.
     */
    public Collection<DicomDocument> getReferencedFiles() {
        return loadedFiles;
    }
}

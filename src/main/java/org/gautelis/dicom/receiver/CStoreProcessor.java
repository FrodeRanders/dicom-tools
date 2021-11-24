/*
 * Copyright (C) 2021 Frode Randers
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
package org.gautelis.dicom.receiver;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.UIDUtils;
import org.gautelis.dicom.Configuration;
import org.gautelis.dicom.behaviours.Provider;
import org.gautelis.vopn.io.Closer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 */
public class CStoreProcessor extends BasicCStoreSCP implements Provider {
    private static final Logger log = LoggerFactory.getLogger(CStoreProcessor.class);

    private final String[] acceptedSopClasses;

    private final File storageDir;

    private final AttributesFormat filePathFormat;
    private final DicomDirWriter dicomDirWriter;
    private final RecordFactory recordFactory = new RecordFactory();
    private final Random randomSource = new Random();

    /**
     * Example of various types of images
     *   UID.CTImageStorage,
     *   UID.MRImageStorage,
     *   UID.UltrasoundImageStorage,
     *   UID.ComputedRadiographyImageStorage
     *
     * @param dicomConfig
     * @param acceptedSopClasses
     * @throws IOException
     */
    public CStoreProcessor(
            Configuration dicomConfig,
            String... acceptedSopClasses
    ) throws IOException {
        super(acceptedSopClasses);

        this.acceptedSopClasses = acceptedSopClasses;

        //
        String directory = dicomConfig.storageDirectory();
        if (null == directory || directory.length() == 0) {
            directory = new File(new File(System.getenv("user.dir")), "STORAGE").getAbsolutePath();
        }
        storageDir = new File(directory);

        if (storageDir.mkdirs()) {
            log.info("M-WRITE " + storageDir);
        }

        /*
         * 0x00100020  Tag.PatientID *
         * 0x00080008  Tag.ImageType
         * 0x00080020  Tag.StudyDate
         * 0x00080030  Tag.StudyTime
         * 0x0020000D  Tag.StudyInstanceUID *
         * 0x0020000E  Tag.SeriesInstanceUID *
         * 0x00080018  Tag.SOPInstanceUID *
         *
         * {00080020,date,yyyy/MM/dd}/
         */
        String pattern = "{00100020}/{0020000D}/{0020000E}/{00080018}.dcm";
        filePathFormat = new AttributesFormat(pattern);

        //
        File dicomDir = new File(storageDir, "DICOMDIR");
        if (!dicomDir.exists()) {
            String uid = UIDUtils.createUID();
            DicomDirWriter.createEmptyDirectory(
                    dicomDir, uid,
                    /* File-set ID */ null,
                    /* File-set Descriptor File */ null,
                    "ISO_IR 100");
        }

        dicomDirWriter = DicomDirWriter.open(dicomDir);
        log.info("Prepared DICOMDIR writer");
    }

    public String[] providesSOPClasses() {
        return acceptedSopClasses;
    }

    public void shutdown() {
        if (null != dicomDirWriter) {
            Closer.close(dicomDirWriter);
        }
    }

    @Override
    protected void store(
            Association as, PresentationContext pc, Attributes rq,
            PDVInputStream data, Attributes rsp
    ) throws IOException {

        try {
            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String tsuid = pc.getTransferSyntax();

            File file = new File(storageDir, iuid);
            if (file.exists() && file.length() > 0L) {
                String info = "File already exists: " + file.getAbsolutePath() + " [skipping]";
                log.warn(info);
                data.skipAll();
            } else {
                try {
                    Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
                    storeTo(as, fmi, data, file);

                    Attributes attrs = parse(file);

                    File dest = getDestinationFile(attrs); // using pattern
                    renameTo(as, file, dest);
                    file = dest;

                    if (addDicomDirRecords(attrs, fmi, file)) {
                        log.info("{}: M-UPDATE {}", as, dicomDirWriter.getFile());
                    } else {
                        log.info("{}: ignoring duplicate object", as);
                        deleteFile(file);
                    }
                } catch (Exception e) {
                    String info = "Failed to store file: " + file.getName();
                    info += ": " + e.getMessage();
                    log.warn(info);

                    deleteFile(file);
                    throw new DicomServiceException(Status.ProcessingFailure, e);
                }
            }
        } catch (Throwable t) {
            String info = "Failed to receive data: " + t.getMessage();
            log.warn(info, t);
        }
    }

    /**
     * Delete specified file
     *
     * @param file
     */
    private static void deleteFile(File file) {
        if (!file.delete()) {
            log.warn("Could not delete temp file: {}", file);
        }
    }

    private static void storeTo(Association as, Attributes fmi, PDVInputStream data, File file)
            throws IOException {

        log.info("{}: M-WRITE {}", as, file);
        if (!file.getParentFile().mkdirs()) {
            throw new IOException("Failed to create directory: " + file.getParentFile().getAbsolutePath());
        }

        try (DicomOutputStream out = new DicomOutputStream(file)) {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        }
    }

    private File getDestinationFile(Attributes attrs) {
        File file = new File(storageDir, filePathFormat.format(attrs));
        while (file.exists()) {
            int someRandomNumber = randomSource.nextInt();
            file = new File(file.getParentFile(), String.format("%010X", someRandomNumber));
        }
        return file;
    }

    private static void renameTo(Association as, File from, File dest) throws IOException {
        log.info("{}: M-RENAME from {} to {} [ignored]", as, from, dest);

        if (!dest.getParentFile().mkdirs()) {
            throw new IOException("Failed to create directory: " + dest.getParentFile().getAbsolutePath());
        }

        if (!from.renameTo(dest)) {
            throw new IOException("Failed to rename " + from + " to " + dest);
        }
    }

    private static Attributes parse(File file) throws IOException {
        try (DicomInputStream in = new DicomInputStream(file)) {
            in.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            return in.readDataset(/* length */ -1, /* stop tag */ Tag.PixelData); // -1 | Tag.PixelData
            /*
             * Above is deprecated, should be replaced with something like
             *
            Predicate<DicomInputStream> tagGEQPixelData =
                    dis -> Integer.compareUnsigned(dis.tag(), Tag.PixelData) >= 0;
            return in.readDataset(-1, tagGEQPixelData);
            */
        }
    }

    protected boolean addDicomDirRecords(
            Attributes ds, Attributes fmi, File f
    ) throws IOException {

        DicomDirWriter ddWriter = dicomDirWriter;
        RecordFactory recFact = recordFactory;
        String pid = ds.getString(Tag.PatientID, null);
        String styuid = ds.getString(Tag.StudyInstanceUID, null);
        String seruid = ds.getString(Tag.SeriesInstanceUID, null);
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);

        if (null == pid) {
            ds.setString(Tag.PatientID, VR.LO, pid = styuid);
        }

        Attributes patRec = ddWriter.findPatientRecord(pid);
        if (null == patRec) {
            patRec = recFact.createRecord(RecordType.PATIENT, null, ds, null, null);
            ddWriter.addRootDirectoryRecord(patRec);
        }

        Attributes studyRec = ddWriter.findStudyRecord(patRec, styuid);
        if (null == studyRec) {
            studyRec = recFact.createRecord(RecordType.STUDY, null, ds, null, null);
            ddWriter.addLowerDirectoryRecord(patRec, studyRec);
        }

        Attributes seriesRec = ddWriter.findSeriesRecord(studyRec, seruid);
        if (null == seriesRec) {
            seriesRec = recFact.createRecord(RecordType.SERIES, null, ds, null, null);
            ddWriter.addLowerDirectoryRecord(studyRec, seriesRec);
        }

        Attributes instRec = ddWriter.findLowerInstanceRecord(seriesRec, false, iuid);
        if (null != instRec) {
            return false;
        }

        instRec = recFact.createRecord(ds, fmi, ddWriter.toFileIDs(f));
        ddWriter.addLowerDirectoryRecord(seriesRec, instRec);
        ddWriter.commit();
        return true;
    }
}
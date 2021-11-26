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
package org.gautelis.dicom.mammography;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.gautelis.dicom.Configuration;
import org.gautelis.dicom.DicomLoader;
import org.gautelis.dicom.InconsistencyException;
import org.gautelis.dicom.behaviours.Provider;
import org.gautelis.dicom.model.DicomDocument;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.xpath.XPath;
import org.jaxen.JaxenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * My, oh my, do I hope Eccentrica Gallumbits is not part of the data set.
 */
public class BreastDensityAnalyzer extends BasicCStoreSCP implements Provider {
    private static final Logger log = LoggerFactory.getLogger(BreastDensityAnalyzer.class);

    private final Configuration dicomConfig;

    private final static String[] acceptedSOPClasses = {
            // Only basic structured reports accepted
            UID.BasicTextSRStorage
    };

    private final File storageDir;

    private final XPath leftBreastDensityExpr;
    private final XPath rightBreastDensityExpression;
    private final XPath radiologistExpr;
    private final XPath operatorExpr;
    private final XPath reportDescriptionExpr;
    private final XPath physNameExpr;
    private final XPath lateralityExpr;

    public BreastDensityAnalyzer(
            Configuration dicomConfig
    ) throws JaxenException {
        super(acceptedSOPClasses);

        this.dicomConfig = dicomConfig;

        //
        String directory = dicomConfig.storageDirectory();
        if (null == directory || directory.length() == 0) {
            directory = new File(new File(System.getenv("user.dir")), "STORAGE").getAbsolutePath();
        }
        storageDir = new File(directory);

        if (storageDir.mkdirs()) {
            log.info("M-WRITE " + storageDir);
        }

        //
        leftBreastDensityExpr = XPathExpression.leftBreastDensity();
        rightBreastDensityExpression = XPathExpression.rightBreastDensity();
        radiologistExpr = XPathExpression.relativeRadiologist();
        operatorExpr = XPathExpression.operator();
        reportDescriptionExpr = XPathExpression.reportDescription();
        physNameExpr = XPathExpression.performingPhysicianName();

        lateralityExpr = XPathExpression.relativeLaterality();
    }

    public String[] getSOPClasses() {
        return acceptedSOPClasses;
    }

    public void shutdown() {
    }

    @Override
    protected void store(
            Association as, PresentationContext pc, Attributes rq,
            PDVInputStream data, Attributes rsp
    ) {

        try {
            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String tsuid = pc.getTransferSyntax();

            switch (cuid) {
                case UID.BasicTextSRStorage: /* 1.2.840.10008.5.1.4.1.1.88.11 */ {
                    DicomDocument.Type type = DicomDocument.Type.find(cuid);
                    log.debug("Analyzing SR: {}", type.getDescription());

                    File file = new File(storageDir, iuid);
                    try {
                        try (DicomOutputStream out = new DicomOutputStream(file)) {
                            Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
                            out.writeFileMetaInformation(fmi);
                            data.copyTo(out);
                        }
                        final Collection<BreastDensityDatum> dati = new ArrayList<>();

                        /////////// DEPENDS ON WHAT YOU WANT TO DO //////////////
                        process(file, dicomElement -> {
                            try {
                                dati.add(new BreastDensityDatum(dicomElement,
                                        leftBreastDensityExpr, rightBreastDensityExpression,
                                        radiologistExpr, operatorExpr, physNameExpr,
                                        reportDescriptionExpr)
                                );
                            }
                            catch (JaxenException | UnknownReportException e) {
                                // Do something worthwile
                            }
                        });
                        dati.stream().forEach(datum -> {
                            if (datum.hasBreastData()) {
                                // spool datum to storage -- or something similar
                                log.debug(datum.toString());
                            } else {
                                log.info("No relevant data found: study={} series={} instance={}",
                                        datum.studyInstanceUID, datum.seriesInstanceUID, datum.sopInstanceUID
                                );
                            }
                        });
                        ///////////////////////////////////////////////////////////

                    } catch (Exception e) {
                        throw new DicomServiceException(Status.ProcessingFailure, e);

                    } finally {
                        deleteFile(file);
                    }
                }
                break;

                case UID.EnhancedSRStorage:
                case UID.ComprehensiveSRStorage:
                case UID.MammographyCADSRStorage: {
                    String info = "This SR was not expected: ";
                    DicomDocument.Type type = DicomDocument.Type.find(cuid);
                    info += type.getDescription();
                    log.warn(info);

                    data.skipAll();
                }
                break;

                default: {
                    String info = "Ignoring non SR: ";
                    DicomDocument.Type type = DicomDocument.Type.find(cuid);
                    info += type.getDescription();
                    log.info(info);

                    data.skipAll();
                }
                break;
            }
        } catch (Throwable t) {
            String info = "Failed to receive data: " + t.getMessage();
            log.warn(info, t);
        }
    }

    public void process(File file, Consumer<DicomElement> consumer) {
        try {
            log.debug("Processing {}", file.getName());

            DicomLoader loader = new DicomLoader();
            loader.load(file);

            DicomDocument dicomDocument = loader.getDicomDocument();
            DicomElement rootElement = dicomDocument.getRootElement();

            process(rootElement, consumer);

        } catch (InconsistencyException ie) {
            String info = "Inconsistent DICOM file: " + ie.getMessage();
            log.warn(info);

        } catch (IOException ioe) {
            String info = "Could not load file: " + file.getAbsolutePath();
            log.warn(info);

        } catch (Throwable t) {
            String info = "Failed miserably: " + t.getMessage();
            log.warn(info, t);
        }
    }

    public void process(DicomElement rootElement, Consumer<DicomElement> consumer) {
        consumer.accept(rootElement);
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
}
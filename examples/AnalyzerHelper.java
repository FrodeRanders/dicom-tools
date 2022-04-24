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

import mammography.BreastDensityAnalyzer;

import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.gautelis.dicom.Configuration;
import org.gautelis.dicom.behaviours.FinderBehaviour;
import org.gautelis.dicom.behaviours.ProviderBehaviour;
import org.gautelis.dicom.behaviours.RetrieverBehaviour;
import org.gautelis.dicom.behaviours.VerificationBehaviour;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AnalyzerHelper implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AnalyzerHelper.class);

    private final Locale locale;

    //
    private final Configuration dicomConfig;
    private final DicomScuNode scuNode;
    private final DicomScpNode scpNode;

    private final VerificationBehaviour verifier;
    private final FinderBehaviour finder;
    private final RetrieverBehaviour retriever;
    private final ProviderBehaviour<BreastDensityAnalyzer> breastDensityAnalyzer;

    public AnalyzerHelper(
            final Properties dicomProperties,
            final Locale locale, PrintWriter out
    ) throws Exception {
        this.locale = locale;

        //
        dicomConfig = ConfigurationTool.bindProperties(Configuration.class, dicomProperties);
        scpNode = new DicomScpNode(dicomConfig);
        scuNode = new DicomScuNode(dicomConfig);

        // The breast density processor retrieves structured reports (SR) from the PACS,
        // which with this specific PACS means that we issue a MOVE operation and await
        // the PACS C-STORE the SR back to us. Thus we have to operate as an SCP,
        // which is handled by DicomScpNode.
        String[] acceptedAETs = dicomConfig.acceptedCallingAETitles().split(",");
        breastDensityAnalyzer = new ProviderBehaviour<>(scpNode, acceptedAETs, new BreastDensityAnalyzer(dicomConfig));

        // The 'finder' and the 'retriever' acts as SCUs, which is handled by DicomScuNode.
        finder = new FinderBehaviour(scuNode);
        retriever = new RetrieverBehaviour(scuNode);

        // The verifier handles PINGs, which we may both issue and receive in capacities as
        // both SCU (i.e. as a client) and SCP (i.e. as a server).
        verifier = new VerificationBehaviour(scuNode, scpNode);
    }

    public void close() {
        breastDensityAnalyzer.close();
        scuNode.close();
        scpNode.close();
    }

    public boolean ping() {
        return verifier.verify();
    }


    /**
     * Given accession number, retrieve patient information _and_ the study
     * instance UID from PACS.
     */
    public void findStudies(
            final String accessionNumber,
            final String modality,
            final Date startDate, final Date endDate,
            final Consumer<String> studyConsumer
    ) {
        finder.find(
            /*----------------------------------
             * Setup keys for searching.
             *
             * Note!
             *
             *    Setting a value to NULL in a query
             *    indicates to the SCP that you want
             *    to fetch that value.
             *
             *---------------------------------*/
            (keys) -> {
                // Indicate character set
                {
                    int tag = Tag.SpecificCharacterSet;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "ISO_IR 100");
                }

                // Study level query
                {
                    int tag = Tag.QueryRetrieveLevel;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "STUDY");
                }

                // Accession number
                {
                    int tag = Tag.AccessionNumber;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, accessionNumber);
                }

                // Study date range
                {
                    int tag = Tag.StudyDate;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    String rangeString = "";
                    if (null != startDate) {
                        String _startDate = org.gautelis.vopn.lang.Date.date2String(startDate, locale).replace("-", "");
                        rangeString += _startDate;
                    }
                    if (null != startDate || null != endDate) {
                        rangeString += "-";
                    }
                    if (null != endDate) {
                        String _endDate = org.gautelis.vopn.lang.Date.date2String(endDate, locale).replace("-", "");
                        rangeString += _endDate;
                    }
                    if (rangeString.length() > 0) {
                        keys.setString(tag, vr, rangeString);
                    }
                }

                // Filter on modality in study
                {
                    int tag = Tag.ModalitiesInStudy; // optionally supported
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    }
                }

                // We are interested in study instance UID
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return this value -- the goal of this method!
                }
            },

            /*----------------------------------
             * Setup handling of search results
             *---------------------------------*/
            (attributes) -> {

                if (attributes.isEmpty())
                    return;

                if (log.isTraceEnabled()) {
                    DicomElement element = new DicomElement("study", attributes);
                    String text = element.asText(/* recurse? */ true);

                    log.trace("=========================================================================");
                    log.trace(text);
                    log.trace("=========================================================================");
                }

                //
                String studyInstanceUID = attributes.getString(Tag.StudyInstanceUID);
                studyConsumer.accept(studyInstanceUID);
            }
        );
    }


    /**
     * Given study instance UID (and the modality we are interested in),
     * retrieve the relevant series associated with that study from PACS.
     */
    public void findSeries(
            final String studyInstanceUID,
            final String modality,
            final BiConsumer<String, String> seriesConsumer
    ) {
        finder.find(
            /*----------------------------------
             * Setup keys for searching.
             *
             * Note!
             *
             *    Setting a value to NULL in a query
             *    indicates to the SCP that you want
             *    to fetch that value.
             *
             *---------------------------------*/
            (keys) -> {
                // Indicate character set
                {
                    int tag = Tag.SpecificCharacterSet;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "ISO_IR 100");
                }

                // Series level query
                {
                    int tag = Tag.QueryRetrieveLevel;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "SERIES");
                }

                // Study instance UID = ...
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, studyInstanceUID);
                }

                // Filter on modality
                {
                    int tag = Tag.Modality;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    }
                }

                // Series instance UID?
                {
                    int tag = Tag.SeriesInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return this value -- the goal of this method!
                }
            },

            /*----------------------------------
             * Setup handling of search results
             *---------------------------------*/
            (attributes) -> {

                if (attributes.isEmpty())
                    return;

                if (log.isTraceEnabled()) {
                    DicomElement element = new DicomElement("series", attributes);
                    String text = element.asText(/* recurse? */ true);

                    log.info("=========================================================================");
                    log.info(text);
                    log.info("=========================================================================");
                }

                String seriesInstanceUID = attributes.getString(Tag.SeriesInstanceUID);
                String availability = attributes.getString(Tag.InstanceAvailability);

                seriesConsumer.accept(seriesInstanceUID, availability);
            }
        );
    }

    /**
     * Retrieve series from PACS
     */
    public Optional<Integer> /* status */ retrieveSeries(
            String studyInstanceUID,
            String seriesInstanceUID,
            String modality
    ) {
        return retriever.retrieve(
            /*----------------------------------
             * Setup keys for searching
             *---------------------------------*/
            (keys) -> {
                // Indicate character set
                {
                    int tag = Tag.SpecificCharacterSet;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "ISO_IR 100");
                }

                // IMAGE level query
                {
                    int tag = Tag.QueryRetrieveLevel;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "SERIES");
                }

                // Study instance UID
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, studyInstanceUID);
                }

                // Series instance UID
                {
                    int tag = Tag.SeriesInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != seriesInstanceUID && seriesInstanceUID.length() > 0) {
                        keys.setString(tag, vr, seriesInstanceUID);
                    }
                }

                // Modality
                {
                    int tag = Tag.Modality;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    }
                }
            },
            dicomConfig.localScpApplicationEntity()
        );
    }
}

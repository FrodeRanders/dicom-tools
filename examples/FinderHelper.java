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

import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.gautelis.dicom.Configuration;
import org.gautelis.dicom.behaviours.FinderBehaviour;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.net.DicomScuNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FinderHelper implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(FinderHelper.class);

    //
    private final Configuration dicomConfig;
    private final Locale locale;

    private final DicomScuNode scuNode;
    private final FinderBehaviour finder;

    public FinderHelper(
            final Configuration dicomConfig,
            Locale locale
    ) {
        //
        this.dicomConfig = dicomConfig;
        this.locale = locale;

        //
        scuNode = new DicomScuNode(dicomConfig);

        // The 'finder' acts as SCUs, which is handled by DicomScuNode.
        finder = new FinderBehaviour(scuNode);
    }

    public void close() {
        scuNode.close();
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
             * Setup keys for searching
             *
             * Note!
             *
             *    Setting a value to NULL in a query
             *    indicates to the SCP that you want
             *    to fetch that value.
             *
             *    These values are not part of the
             *    conditions of the query!
             *
             *    E.g. 'modality' below is either part
             *    of the conditionals -- if you provide
             *    a value for it -- or you ask the
             *    SCP to return the corresponding
             *    value.
             *
             *    Thus, in this example, we request more
             *    information than we actually use
             *    (or need)!
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
                    } else {
                        keys.setNull(tag, vr); // ask SCP to return value
                    }
                }

                // Filter on modality in study
                {
                    int tag = Tag.ModalitiesInStudy; // optionally supported
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    } else {
                        keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
                    }
                }

               // We are interested in study instance UID
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return this value -- the goal of this method!
                }

                // For logging purposes
                {
                    int tag = Tag.NumberOfStudyRelatedSeries;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
                }

                {
                    int tag = Tag.NumberOfStudyRelatedInstances;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
                }

                {
                    int tag = Tag.StudyDescription;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
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
             *    These values are not part of the
             *    conditions of the query!
             *
             *    E.g. 'modality' below is either part
             *    of the conditionals -- if you provide
             *    a value for it -- or you ask the
             *    SCP to return the corresponding
             *    value.
             *
             *    Thus, in this example, we request more
             *    information than we actually use
             *    (or need)!
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
                    } else {
                        keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
                    }
                }

                // We are interested in series instance UID
                {
                    int tag = Tag.SeriesInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return this value -- the goal of this method!
                }

                // For logging purposes
                {
                    int tag = Tag.SeriesDescription;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
                }

                {
                    int tag = Tag.NumberOfSeriesRelatedInstances;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr); // ask SCP to return value, just so we may inspect it
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
}

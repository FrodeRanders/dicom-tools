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
package examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gautelis.dicom.Configuration;
import org.gautelis.vopn.lang.ConfigurationTool;

import java.util.Date;
import java.util.Locale;
import java.util.Properties;

public class Application {
    private static final Logger log = LogManager.getLogger(Application.class);

    private static final String ACCESSION_NUMBER_FORMAT = "%010d";

    public static void main(String[] args) {

        try (FinderHelper helper = new FinderHelper(getConfig(), Locale.US)) {

            // Start with accession number
            final String accessionNumber = padNumber(ACCESSION_NUMBER_FORMAT, "42");

            final String modality = "OT";
            Date startDate = null;
            Date endDate = null;

            helper.findStudies(accessionNumber, modality, startDate, endDate, studyInstanceUID -> {
                helper.findSeries(studyInstanceUID, modality, (seriesInstanceUID, availability) -> {
                    //////////////////////////////////////////////////////////////////////////////////////
                    // May run multiple times!
                    System.out.printf("Accession number: %s  modality: %s%n", accessionNumber, modality);
                    System.out.printf("  StudyInstanceUID: %s%n", studyInstanceUID);
                    System.out.printf("  SeriesInstanceUID: %s  availability: %s%n", seriesInstanceUID, availability);
                    //////////////////////////////////////////////////////////////////////////////////////
                });
            });
        }
        catch (Throwable t) {
            System.err.printf("Could not operate: %s%n", t.getMessage());
        }
    }

    private static String padNumber(String format, String number) {
        try {
            return String.format(format, Long.parseLong(number));

        } catch (NumberFormatException nfe) {
            String info = String.format("Invalid accession number: %s", number);
            throw new RuntimeException(info, nfe);
        }
    }

    private static Configuration getConfig() {
        final Properties props = new Properties();

        // SCU / client-side configuration
        props.setProperty("local-scu-application-entity", "MY_SCU");
        props.setProperty("local-scu-modality-type", "OT");

        // SCP / client-side configuration. This configuration must match the
        // remote SCP configuration below -- otherwise the SCU will not be able
        // to connect back to the (local) SCP.
        props.setProperty("local-scp-application-entity", "MY_SCP");
        props.setProperty("local-scp-host", "localhost");
        props.setProperty("local-scp-port", "4101");

        props.setProperty("storage-directory", "./STORAGE");

        // The local SCP accepts connections from these application entities
        props.setProperty("accepted-calling-aets", "MY_SCU,REMOTE_SCP,SOME_PACS");

        // Remote SCP (which is actually same as local SCP :) as seen from the SCU
        props.setProperty("remote-application-entity", "MY_SCP");
        props.setProperty("remote-host", "localhost");
        props.setProperty("remote-port", "4101");

        return ConfigurationTool.bindProperties(Configuration.class, props);
    }
}

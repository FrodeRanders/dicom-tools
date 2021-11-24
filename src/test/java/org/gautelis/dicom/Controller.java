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
package org.gautelis.dicom;

import org.dcm4che3.data.UID;
import org.gautelis.dicom.behaviours.ProviderBehaviour;
import org.gautelis.dicom.behaviours.VerificationBehaviour;
import org.gautelis.dicom.receiver.CStoreProcessor;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    //
    private final Configuration dicomConfig;

    private final DicomScuNode scuNode;
    private final DicomScpNode scpNode;

    private final VerificationBehaviour verifier;
    private final ProviderBehaviour<CStoreProcessor> storer;

    public Controller(
            final Properties dicomProperties
    ) throws Exception {

        dicomConfig = ConfigurationTool.bindProperties(Configuration.class, dicomProperties);

        // The breast density processor retrieves structured reports (SR) from the PACS,
        // which with this specific PACS means that we issue a MOVE operation and await
        // the PACS C-STORE the SR back to us. Thus we have to operate as an SCP
        // (i.e. a server), which is handled by DicomScpNode.
        scpNode = new DicomScpNode(dicomConfig);

        String[] acceptedSopClasses = {
                // Basic structured reports accepted
                UID.BasicTextSRStorage,

                // Various types of images
                UID.CTImageStorage,
                UID.MRImageStorage,
                UID.UltrasoundImageStorage,
                UID.ComputedRadiographyImageStorage
        };

        storer = new ProviderBehaviour<>(
                scpNode, dicomConfig.acceptedCallingAETitles().split(","),
                new CStoreProcessor(dicomConfig, acceptedSopClasses)
        );

        // The 'finder', the 'retriever' acts as SCUs (i.e. as clients), which
        // is handled by DicomScuNode.
        scuNode = new DicomScuNode(dicomConfig);

        // The verifier handles PINGs, which we may both issue and receive in capacities as both
        // SCU (i.e. as a client) and SCP (i.e. as a server).
        verifier = new VerificationBehaviour(scuNode, scpNode);
    }

    public void shutdown() {
        storer.shutdown();
        scpNode.shutdown();
        scuNode.shutdown();
    }

    public boolean ping() {
        return verifier.verify();
    }
}

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

import org.gautelis.dicom.behaviours.VerificationBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;

import java.util.Properties;

public class Controller implements AutoCloseable {

    private final DicomScuNode scuNode;
    private final DicomScpNode scpNode;

    private final VerificationBehaviour verifier;

    public Controller(
            final Properties dicomProperties
    ) {
        //
        Configuration dicomConfig = ConfigurationTool.bindProperties(Configuration.class, dicomProperties);

        // The 'verifier' acts as both a SCU handled by DicomScuNode and
        // as a SCP handled by DicomScpNod, so we need both.
        scuNode = new DicomScuNode(dicomConfig);
        scpNode = new DicomScpNode(dicomConfig);

        // The verifier handles PINGs, which we may both issue and receive in capacities as
        // both client (SCU) and server (SCP).
        verifier = new VerificationBehaviour(scuNode, scpNode);
    }

    public void close() {
        scpNode.close();
        scuNode.close();
    }

    public boolean ping() {
        return verifier.verify();
    }
}

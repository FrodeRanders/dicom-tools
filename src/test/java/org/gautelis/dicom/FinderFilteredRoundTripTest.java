/*
 * Copyright (C) 2026 Frode Randers
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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.TransferCapability;
import org.gautelis.dicom.behaviours.FinderBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class FinderFilteredRoundTripTest {

    @Test
    public void testFindFiltersStudyInstanceUID() throws Exception {
        Properties dicomProps = new Properties();
        dicomProps.setProperty("local-scu-application-entity", "LOCAL_SCU");
        dicomProps.setProperty("local-scu-modality", "OT");
        dicomProps.setProperty("local-scp-application-entity", "LOCAL_SCP");
        dicomProps.setProperty("local-scp-host", "localhost");
        dicomProps.setProperty("local-scp-port", "5126");
        dicomProps.setProperty("accepted-calling-aets", "LOCAL_SCU");
        dicomProps.setProperty("remote-application-entity", "LOCAL_SCP");
        dicomProps.setProperty("remote-host", "localhost");
        dicomProps.setProperty("remote-port", "5126");

        Configuration dicomConfig = ConfigurationTool.bindProperties(Configuration.class, dicomProps);

        List<Attributes> matches = new ArrayList<>();
        matches.add(TestDicomQuerySupport.match("STUDY-1", "PATIENT-1"));
        matches.add(TestDicomQuerySupport.match("STUDY-2", "PATIENT-2"));

        DicomScpNode scpNode = new DicomScpNode(dicomConfig);
        scpNode.withApplicationEntity(ae -> ae.addTransferCapability(
                new TransferCapability(
                        null,
                        UID.StudyRootQueryRetrieveInformationModelFind,
                        TransferCapability.Role.SCP,
                        DicomScpNode.TRANSFER_SYNTAX_CHAIN
                )
        ));
        scpNode.withServiceRegistry(registry ->
                registry.addDicomService(new TestDicomQuerySupport.StaticCFindSCP(
                        matches,
                        (candidate, keys) -> {
                            String studyInstanceUID = keys.getString(Tag.StudyInstanceUID, null);
                            return studyInstanceUID == null
                                    || studyInstanceUID.equals(candidate.getString(Tag.StudyInstanceUID));
                        }
                ))
        );

        DicomScuNode scuNode = new DicomScuNode(dicomConfig);
        FinderBehaviour finder = new FinderBehaviour(scuNode);

        List<Attributes> results = new ArrayList<>();
        try {
            finder.find(keys -> {
                keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
                keys.setString(Tag.StudyInstanceUID, VR.UI, "STUDY-2");
                keys.setNull(Tag.PatientID, VR.LO);
            }, results::add);
        } finally {
            scpNode.close();
            scuNode.close();
        }

        assertEquals(1, results.size());
        assertEquals("STUDY-2", results.get(0).getString(Tag.StudyInstanceUID));
        assertEquals("PATIENT-2", results.get(0).getString(Tag.PatientID));
    }

}

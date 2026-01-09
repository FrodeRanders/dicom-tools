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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

import java.io.ByteArrayInputStream;

public class DicomdirLoaderConsistencyTest {

    @Test(expected = InconsistencyException.class)
    public void testInconsistentPatientId() throws Exception {
        Attributes dataset = new Attributes();
        Sequence sequence = dataset.newSequence(Tag.DirectoryRecordSequence, 2);

        Attributes first = new Attributes();
        first.setString(Tag.DirectoryRecordType, VR.CS, "PATIENT");
        first.setString(Tag.PatientID, VR.LO, "PATIENT-1");
        sequence.add(first);

        Attributes second = new Attributes();
        second.setString(Tag.DirectoryRecordType, VR.CS, "PATIENT");
        second.setString(Tag.PatientID, VR.LO, "PATIENT-2");
        sequence.add(second);

        DicomdirLoader loader = new DicomdirLoader(false);
        loader.dicomdirStreamLoader.load(dataset, "DICOMDIR", null, new ByteArrayInputStream(new byte[0]));
    }
}

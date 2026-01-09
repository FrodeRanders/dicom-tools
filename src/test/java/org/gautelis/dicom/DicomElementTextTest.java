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
import org.dcm4che3.data.VR;
import org.gautelis.dicom.model.DicomElement;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DicomElementTextTest {

    @Test
    public void testAsTextIncludesRootAndAttributes() {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.1.3.10");
        attributes.setString(Tag.PatientID, VR.LO, "PATIENT-1");

        DicomElement element = new DicomElement("ROOT", attributes, null);
        String text = element.asText(false);

        assertTrue(text.contains("[ROOT : Media Storage Directory]"));
        assertTrue(text.contains("PatientID :: PATIENT-1"));
    }
}

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
import org.gautelis.dicom.model.DicomAttribute;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.xpath.DicomNavigator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class DicomNavigatorTest {

    @Test
    public void testElementSiblingTraversal() {
        Attributes parentAttributes = new Attributes();
        Sequence sequence = parentAttributes.newSequence(Tag.ContentSequence, 2);

        Attributes child1 = new Attributes();
        child1.setString(Tag.PatientID, VR.LO, "P1");
        Attributes child2 = new Attributes();
        child2.setString(Tag.PatientID, VR.LO, "P2");
        sequence.add(child1);
        sequence.add(child2);

        DicomElement parent = new DicomElement("ROOT", parentAttributes, null);
        DicomElement firstChild = parent.getChildren().get(0);
        DicomElement secondChild = parent.getChildren().get(1);

        DicomNavigator navigator = new DicomNavigator();
        List<?> following = toList(navigator.getFollowingSiblingAxisIterator(firstChild));
        List<?> preceding = toList(navigator.getPrecedingSiblingAxisIterator(secondChild));

        assertEquals(1, following.size());
        assertEquals(1, preceding.size());
        assertSame(secondChild, following.get(0));
        assertSame(firstChild, preceding.get(0));
    }

    @Test
    public void testAttributeSiblingTraversal() {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.PatientID, VR.LO, "P1");
        attributes.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3");

        DicomElement element = new DicomElement("TEST", attributes, null);
        DicomAttribute patientId = findAttribute(element.getDicomElements(), "PatientID");
        DicomAttribute studyUid = findAttribute(element.getDicomElements(), "StudyInstanceUID");

        DicomNavigator navigator = new DicomNavigator();
        List<?> following = toList(navigator.getFollowingSiblingAxisIterator(patientId));
        List<?> preceding = toList(navigator.getPrecedingSiblingAxisIterator(studyUid));

        assertEquals(1, following.size());
        assertEquals(1, preceding.size());
        assertSame(studyUid, following.get(0));
        assertSame(patientId, preceding.get(0));
    }

    private static List<?> toList(Iterator<?> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static DicomAttribute findAttribute(List<DicomAttribute> attributes, String name) {
        for (DicomAttribute attribute : attributes) {
            if (name.equals(attribute.getName())) {
                return attribute;
            }
        }
        throw new AssertionError("Missing attribute: " + name);
    }
}

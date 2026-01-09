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
import org.gautelis.dicom.xpath.XPath;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XPathIntegrationTest {

    @Test
    public void testSelectElementsAndAttributes() throws Exception {
        Attributes rootAttributes = new Attributes();
        Sequence sequence = rootAttributes.newSequence(Tag.ContentSequence, 2);

        Attributes child1 = new Attributes();
        child1.setString(Tag.CodeValue, VR.SH, "CODE-1");
        Attributes child2 = new Attributes();
        child2.setString(Tag.CodeValue, VR.SH, "CODE-2");
        sequence.add(child1);
        sequence.add(child2);

        DicomElement root = new DicomElement("ROOT", rootAttributes, null);

        XPath elementsQuery = new XPath("//ContentSequence");
        List<?> elements = elementsQuery.selectNodes(root);
        assertEquals(2, elements.size());

        XPath attributesQuery = new XPath("//ContentSequence/@CodeValue");
        List<?> attributes = attributesQuery.selectNodes(root);
        assertEquals(2, attributes.size());
        assertTrue(((DicomAttribute) attributes.get(0)).getValue().startsWith("CODE-"));
        assertTrue(((DicomAttribute) attributes.get(1)).getValue().startsWith("CODE-"));
    }
}

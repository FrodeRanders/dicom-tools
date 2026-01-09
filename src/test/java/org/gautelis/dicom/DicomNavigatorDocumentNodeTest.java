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
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.xpath.DicomNavigator;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class DicomNavigatorDocumentNodeTest {

    @Test
    public void testGetDocumentNodeFromNestedElement() {
        Attributes rootAttributes = new Attributes();
        Sequence sequence = rootAttributes.newSequence(Tag.ContentSequence, 1);
        sequence.add(new Attributes());

        DicomElement root = new DicomElement("ROOT", rootAttributes, null);
        DicomElement child = root.getChildren().get(0);

        DicomNavigator navigator = new DicomNavigator();
        Object documentNode = navigator.getDocumentNode(child);

        assertSame(root, documentNode);
    }
}

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

import org.gautelis.dicom.model.DicomDocument;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DicomDocumentTypeTest {

    @Test
    public void testTypeLookup() {
        assertEquals(
                DicomDocument.Type.Media_Storage_Directory_Storage,
                DicomDocument.Type.find("1.2.840.10008.1.3.10")
        );
        assertEquals(DicomDocument.Type.Unknown, DicomDocument.Type.find("1.2.3.4.5"));
    }
}

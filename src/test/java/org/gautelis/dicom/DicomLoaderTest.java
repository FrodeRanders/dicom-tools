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
import org.gautelis.dicom.model.DicomElement;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DicomLoaderTest {

    @Test
    public void testLoadFromFile() throws Exception {
        File file = new File("src/test/resources/org/gautelis/dicom/DICOM/IM_0001");
        assertTrue("Missing test DICOM file: " + file.getPath(), file.exists());

        DicomLoader loader = new DicomLoader();
        loader.load(file);

        DicomDocument document = loader.getDicomDocument();
        assertNotNull(document);
        assertEquals(file.getName(), document.getName());
        assertEquals(file.getPath(), document.getPath());

        DicomElement root = document.getRootElement();
        assertNotNull(root);
        assertNotNull(root.getSopClassUID());
        assertFalse(root.getDicomElements().isEmpty());
    }
}

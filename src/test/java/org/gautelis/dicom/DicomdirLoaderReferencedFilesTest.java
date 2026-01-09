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

import java.io.File;
import java.util.Collection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DicomdirLoaderReferencedFilesTest {

    @Test
    public void testLoadsReferencedFiles() throws Exception {
        File file = new File("src/test/resources/org/gautelis/dicom/DICOMDIR");
        assertTrue("Missing test DICOMDIR: " + file.getPath(), file.exists());

        DicomdirLoader loader = new DicomdirLoader(true);
        loader.load(file);

        Collection<DicomDocument> documents = loader.getReferencedFiles();
        assertNotNull(documents);
        assertTrue("Expected referenced files to load", documents.size() > 1);
    }
}

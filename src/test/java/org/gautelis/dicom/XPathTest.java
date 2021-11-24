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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gautelis.dicom.model.DicomAttribute;
import org.gautelis.dicom.model.DicomDocument;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.xpath.XPath;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 */
public class XPathTest
{
    private static final Logger log = LogManager.getLogger(XPathTest.class);

    @Test
    public void testXPath() {
        System.out.println("---");
        String name = "DICOMDIR";

        try (InputStream is = getClass().getResourceAsStream(name)) {

            // Load from resources using a stream
            DicomdirLoader loader = new DicomdirLoader(/* load referenced files? */ false);
            loader.load(name, is);

            // Load form resources using relative path -- assuming tests are invoked
            // using mvn test in the project root.
            //
            //File file = new File("./src/test/resources/org/gautelis/dicom/DICOMDIR");
            //loader.load(file);

            DicomDocument dicomDocument = loader.getDicomDocument();
            DicomElement dicomElement = dicomDocument.getRootElement();

            // Get root element (DicomElement) -- typically a DICOMDIR
            String expr = "/";
            System.out.println("Using XPath expression: " + expr);
            XPath xpath = new XPath(expr);
            Object node = xpath.selectSingleNode(dicomElement);
            if (null != node) {
                System.out.println("Found DicomElement:\n" + ((DicomElement)node).asText(/* recurse? */ false, DicomElement.INDENT));
                assertEquals(dicomElement, node);
            }

            // Get all elements (DicomElement) named 'ConceptNameCodeSequence'
            expr = "//ConceptNameCodeSequence";
            System.out.println("Using XPath expression: " + expr);
            xpath = new XPath(expr);
            List<?> nodes = xpath.selectNodes(dicomElement);
            for (Object _node : nodes) {
                System.out.println("Found DicomElement:\n" + ((DicomElement)_node).asText(/* recurse? */ false, DicomElement.INDENT));
                assertEquals("ConceptNameCodeSequence", ((DicomElement) _node).getName());
            }

            // Get all attributes (DicomAttribute) named 'CodeValue' in element named 'ConceptNameCodeSequence'
            expr = "//ConceptNameCodeSequence/@CodeValue";
            System.out.println("Using XPath expression: " + expr);
            xpath = new XPath(expr);
            nodes = xpath.selectNodes(dicomElement);
            for (Object _node : nodes) {
                System.out.println("Found DicomAttribute:\n" + ((DicomAttribute)_node).asText(DicomElement.INDENT));
                assertEquals("CodeValue", ((DicomAttribute) _node).getName());
            }

            // Get all elements (DicomElement) named 'ConceptNameCodeSequence' having an attribute named 'CodeValue' with a value of '45_01004001'
            expr = "//ConceptNameCodeSequence[@CodeValue='45_01004001']";
            System.out.println("Using XPath expression: " + expr);
            xpath = new XPath(expr);
            nodes = xpath.selectNodes(dicomElement);
            for (Object _node : nodes) {
                System.out.println("Found DicomElement:\n" + ((DicomElement)_node).asText(/* recurse? */ false, DicomElement.INDENT));
                assertEquals("ConceptNameCodeSequence", ((DicomElement) _node).getName());
            }

            // Get all attributes (DicomAttribute) named 'CodeValue' in elements named 'ConceptNameCodeSequence' having a value of '45_01004001'
            expr = "//ConceptNameCodeSequence[@CodeValue='45_01004001']/@CodeValue";
            System.out.println("Using XPath expression: " + expr);
            xpath = new XPath(expr);
            nodes = xpath.selectNodes(dicomElement);
            for (Object _node : nodes) {
                System.out.println("Found DicomAttribute:\n" + ((DicomAttribute)_node).asText(DicomElement.INDENT));
                assertEquals("CodeValue", ((DicomAttribute) _node).getName());
                assertEquals("45_01004001", ((DicomAttribute) _node).getValue());
            }

            // Get all attributes (DicomAttribute) named 'CodeValue' in elements named 'ConceptNameCodeSequence' having a value of '45_01004001'.
            // We also demand that the element (DicomElement) have an attribute (DicomAttribute) named 'CodingSchemeDesignator' with a
            // value of '99_PHILIPS'
            //
            expr = "//ConceptNameCodeSequence[@CodeValue='45_01004001' and @CodingSchemeDesignator='99_PHILIPS']/@CodeValue";
            System.out.println("Using XPath expression: " + expr);
            xpath = new XPath(expr);
            nodes = xpath.selectNodes(dicomElement);
            for (Object _node : nodes) {
                System.out.println("Found DicomAttribute:\n" + ((DicomAttribute)_node).asText(DicomElement.INDENT));
                assertEquals("CodeValue", ((DicomAttribute) _node).getName());
                assertEquals("45_01004001", ((DicomAttribute) _node).getValue());
            }
        } catch (Throwable t) {
            String info = "Failed: " + t.getMessage();
            log.warn(info, t);

            fail(info);
        }
    }
}

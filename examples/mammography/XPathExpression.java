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
package mammography;

import org.gautelis.dicom.xpath.XPath;
import org.jaxen.JaxenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class XPathExpression {
    private static final Logger log = LoggerFactory.getLogger(XPathExpression.class);

    private XPathExpression() {}

    private static XPath breastDensity(String scheme, String code) throws JaxenException {
        /*   ---------------------------
             ---- Structured report ----
             ---------------------------

        [(0040,A730) ContentSequence]
            (0040,A010) RelationshipType :: CONTAINS
            (0040,A040) ValueType :: CODE

            [(0040,A043) ConceptNameCodeSequence]
                (0008,0100) CodeValue :: F-01710
                (0008,0102) CodingSchemeDesignator :: SRT
                (0008,0103) CodingSchemeVersion :: 1.0
                (0008,0104) CodeMeaning :: Breast Composition

            [(0040,A168) ConceptCodeSequence]
                (0008,0100) CodeValue :: F-01713
                (0008,0102) CodingSchemeDesignator :: SRT
                (0008,0103) CodingSchemeVersion :: 1.0
                (0008,0104) CodeMeaning :: ACR3

            [(0040,A730) ContentSequence]
                (0040,A010) RelationshipType :: HAS CONCEPT MOD
                (0040,A040) ValueType :: CODE

                [(0040,A043) ConceptNameCodeSequence]
                    (0008,0100) CodeValue :: G-C171
                    (0008,0102) CodingSchemeDesignator :: SRT
                    (0008,0103) CodingSchemeVersion :: 1.0
                    (0008,0104) CodeMeaning :: Laterality

                [(0040,A168) ConceptCodeSequence]
                    (0008,0100) CodeValue :: T-04030
                    (0008,0102) CodingSchemeDesignator :: SNM3
                    (0008,0103) CodingSchemeVersion :: 1.0
                    (0008,0104) CodeMeaning :: vänster
        */
        String expr = String.format(
                "//ConceptCodeSequence[(../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) and (../ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='%s' and @CodeValue='%s'])]/@CodeValue",
                scheme, code);
        return new XPath(expr);
    }

    public static XPath leftBreastDensity() throws JaxenException {
        return breastDensity("SNM3", "T-04030");
    }

    public static XPath rightBreastDensity() throws JaxenException {
        return breastDensity("SNM3", "T-04020");
    }

    public static XPath relativeLaterality() throws JaxenException {
        return new XPath("../../../../ContentSequence[(ConceptNameCodeSequence[@CodingSchemeDesignator='99IDI' and @CodeValue='RADIOLOG_CODE'])]/@TextValue");
    }

    public static XPath relativeRadiologist() throws JaxenException {
        return new XPath("../../../../ContentSequence[(ConceptNameCodeSequence[@CodingSchemeDesignator='99IDI' and @CodeValue='RADIOLOG_CODE'])]/@TextValue");
    }

    public static XPath operator() throws JaxenException {
        return new XPath("/ContentSequence[(ConceptNameCodeSequence[@CodingSchemeDesignator='99IDI' and @CodeValue='OPERATOR_ID'])]/@TextValue");
    }

    public static XPath reportDescription() throws JaxenException {
        return new XPath("/ContentSequence[(ConceptNameCodeSequence[@CodingSchemeDesignator='99IDI' and @CodeValue='REPRT_DESCR'])]/@TextValue");
    }

    public static XPath performingPhysicianName() throws JaxenException {
        return new XPath("/@PerformingPhysicianName");
    }
}

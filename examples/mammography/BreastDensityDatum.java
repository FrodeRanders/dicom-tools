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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.gautelis.dicom.model.DicomAttribute;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.xpath.XPath;
import org.jaxen.JaxenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;


/**
 * Data regarding readings for a single patient
 */
public class BreastDensityDatum {
    private static final Logger log = LoggerFactory.getLogger(BreastDensityDatum.class);

    private static int inSessionUniqueCounter = 0;

    // This white list has to be treated differently. In this case, we only want to consider breast density
    // readings so we establish this white list accordingly.
    private static Collection<String> WHITELIST = Arrays.asList("F\u00F6rsta Granskning", "Andra Granskning", "Reading", "Reading 1", "Reading 2");

    private final String patientID;
    public final String patientBirthDate;
    public final String patientGender;
    public final int ageAtTimeOfStudy;
    public final String accessionNumber;
    public final String studyInstanceUID;
    public final String studyDate;
    public final String seriesInstanceUID;
    public final String sopInstanceUID;
    private Integer leftBreastDensity = null;
    private Integer rightBreastDensity = null;
    public String radiologist = null;
    public String operator = null;
    public String reportDescription = null;

    public final boolean hasRelevantData;

    //
    public BreastDensityDatum(
            DicomElement rootElement,
            XPath leftBreastDensityExpr, XPath rightBreastDensityExpression,
            XPath radiologistExpr, XPath operatorExpr, XPath physNameExpr,
            XPath reportDescriptionExpr
    ) throws JaxenException, UnknownReportException {
        Attributes attr = rootElement.getAttributes();

        patientID = attr.getString(Tag.PatientID, "");
        patientBirthDate = attr.getString(Tag.PatientBirthDate, "");
        patientGender = attr.getString(Tag.PatientSex, "");

        accessionNumber = attr.getString(Tag.AccessionNumber, "");
        studyInstanceUID = attr.getString(Tag.StudyInstanceUID, "");
        studyDate = attr.getString(Tag.StudyDate, "");

        ageAtTimeOfStudy = age(patientBirthDate, studyDate, DateTimeFormatter.BASIC_ISO_DATE);

        seriesInstanceUID = attr.getString(Tag.SeriesInstanceUID, "");

        sopInstanceUID = attr.getString(Tag.SOPInstanceUID, "");

        String completionFlag = attr.getString(Tag.CompletionFlag, "");
        boolean isComplete = "COMPLETE".equalsIgnoreCase(completionFlag);

        String verificationFlag = attr.getString(Tag.VerificationFlag, "");
        boolean isVerified = "VERIFIED".equalsIgnoreCase(verificationFlag);

        String preliminiaryFlag = attr.getString(Tag.PreliminaryFlag, "");
        boolean isFinal = "FINAL".equalsIgnoreCase(preliminiaryFlag);

        //-------------------------------------------------
        // Report description (from a swedish setting)
        // a) "Anamnes"
        // b) "Kliniska indikationer"
        // c) "Första Granskning"
        // d) "Andra Granskning"
        // e) "Konsensusgranskning"
        //
        // From study, also
        // .) "Reading"
        // .) "Reading 1"
        // .) "Reading 2"
        //-------------------------------------------------
        Object reportDescriptionNode = reportDescriptionExpr.selectSingleNode(rootElement);
        if (null != reportDescriptionNode) {
            reportDescription = ((DicomAttribute)reportDescriptionNode).getValue();
        }

        if (null == reportDescription || reportDescription.length() == 0) {
            // This is not a breast density reading
            String info = "Ignoring unknown report: AccessionNumber=" + accessionNumber;
            info += " StudyInstanceUID=" + studyInstanceUID;
            info += " SeriesInstanceUID=" + seriesInstanceUID;
            info += " SOPInstanceUID=" + sopInstanceUID;
            throw new UnknownReportException(info);
        }

        //
        hasRelevantData = WHITELIST.contains(reportDescription);
        if (!hasRelevantData) {
            String info = "Ignoring report: " + reportDescription;
            log.info(info);
        }
        else {
            log.debug("Analyzing report: " + reportDescription);
            
            String performingPhysicianName = null;
            Object physNameNode = physNameExpr.selectSingleNode(rootElement);
            if (null != physNameNode) {
                performingPhysicianName = ((DicomAttribute) physNameNode).getValue();
            }

            // Operator
            Object operatorNode = operatorExpr.selectSingleNode(rootElement);
            if (null != operatorNode) {
                operator = ((DicomAttribute) operatorNode).getValue();
            }

            // Density - left breast (patient may not have left breast!)
            List nodes = leftBreastDensityExpr.selectNodes(rootElement);
            for (Object node : nodes) {
                leftBreastDensity = breastDensityCode(((DicomAttribute) node).getValue());

                Object radiologistNode = radiologistExpr.selectSingleNode(node);
                if (null != radiologistNode) {
                    radiologist = ((DicomAttribute) radiologistNode).getValue();
                }
                else {
                    radiologist = operator;
                }
            }

            // Density - right (patient may not have right breast!)
            nodes = rightBreastDensityExpression.selectNodes(rootElement);
            for (Object node : nodes) {
                rightBreastDensity = breastDensityCode(((DicomAttribute) node).getValue());

                Object radiologistNode = radiologistExpr.selectSingleNode(node);
                if (null != radiologistNode) {
                    String _radiologist = ((DicomAttribute) radiologistNode).getValue();
                    if (null != radiologist && !radiologist.equals(_radiologist)) {
                        String info = "Different radiologists in same examination? ";
                        info += radiologist + "&" + _radiologist;
                        info += ": sticking with " + radiologist;
                        log.warn(info);
                    } else {
                        radiologist = _radiologist;
                    }
                }
            }

            //
            if (null == radiologist || radiologist.length() == 0) {
                radiologist = "unknown";
            }

            if (log.isTraceEnabled()) {
                String info = "PatientID=\"" + patientID;
                info += "\", PatientBirthDate=\"" + patientBirthDate;
                info += "\", PatientSex=\"" + patientGender;
                info += "\", AccessionNumber=\"" + accessionNumber;
                info += "\", StudyInstanceUID=\"" + studyInstanceUID;
                info += "\", SeriesInstanceUID=\"" + seriesInstanceUID;
                info += "\", SOPInstanceUID=\"" + sopInstanceUID;
                info += "\", leftBreastDensity=\"" + (null != leftBreastDensity ? leftBreastDensity : "<unknown>");
                info += "\", rightBreastDensity=\"" + (null != rightBreastDensity ? rightBreastDensity : "<unknown>");
                info += "\", radiologist=\"" + (null != radiologist ? radiologist : "<unknown>");
                info += "\", operator=\"" + (null != operator ? operator : "<unknown>");
                if (null != performingPhysicianName && performingPhysicianName.length() > 0) {
                    info += "\", performingPhysician=\"" + performingPhysicianName;
                }
                info += "\", reportDescription=\"" + (null != reportDescription ? reportDescription : "<unknown>");
                info += "\", is-complete=\"" + isComplete;
                info += "\", is-verified=\"" + isVerified;
                info += "\", is-final=\"" + isFinal;
                info += "\"";
                log.trace("### " + info);
            }
        }
    }

    public boolean hasBreastData() {
        return
            hasRelevantData &&
            (null != leftBreastDensity || null != rightBreastDensity) &&
            (null != radiologist && radiologist.length() > 0);
    }

    public boolean hasLeftBreast() {
        return null != leftBreastDensity;
    }

    public Integer getLeftBreastDensity() {
        return leftBreastDensity;
    }

    public boolean hasRightBreast() {
        return null != rightBreastDensity;
    }

    public Integer getRightBreastDensity() {
        return rightBreastDensity;
    }

    public int getBreastDensity() {
        int currentMax = 0;
        if (null != leftBreastDensity) {
            currentMax = leftBreastDensity;
        }

        int maxBreastDensity = currentMax;
        if (null != rightBreastDensity) {
            if (rightBreastDensity > currentMax) {
                maxBreastDensity = rightBreastDensity;
            }
        }
        return maxBreastDensity;
    }

    /**
     * CID 6001 Overall Breast Composition (from BI-RADS).
     * <p>
     *
     * @param srtDensityCode
     * @return
     */
    public static int breastDensityCode(String srtDensityCode) {
        switch (srtDensityCode) {
            case "F-01711": // Almost entirely fat
                return 1; // ACR1

            case "F-01712": // Scattered fibroglandular densities
                return 2; // ACR2

            case "F-01713": // Heterogeneously dense
                return 3; // ACR3

            case "F-01714": // Extremely dense
                return 4; // ACR4

            default:
                break;
        }
        return 0;
    }

    /**
     * CID 6027 Assessment (from BI-RADS)
     */
    public String assessmentDescription(String assessment) {
        switch (assessment) {
            case "II.AC.a":
                return "0 - Need additional imaging evaluation";

            case "II.AC.b.1":
                return "1 – Negative";

            case "II.AC.b.2":
                return "2 – Benign Finding";

            case "II.AC.b.3":
                return "3 - Probably benign – short interval follow-up (1-11 months)";

            case "II.AC.b.4":
                return "4 - Suspicious abnormality, biopsy should be considered";

            case "MA.II.A.5.4A":
                return "4A – Low suspicion";

            case "MA.II.A.5.4B":
                return "4B – Intermediate suspicion";

            case "MA.II.A.5.4C":
                return "4C – Moderate suspicion";

            case "II.AC.b.5":
                return "5 - Highly suggestive of malignancy, take appropriate action";

            case "MA.II.A.5.6":
                return "6 - Known biopsy proven malignancy";

            default:
                break;
        }

        return "<unknown>";
    }


    /**
     * Calculate age (in years) at study time.
     * <p/>
     * DateTimeFormatter.BASIC_ISO_DATE  (19500304)
     * DateTimeFormatter.ISO_LOCAL_DATE  (1950-03-04)
     * <p/>
     * @param birthDate
     * @param studyDate
     * @return
     */
    private int age(String birthDate, String studyDate, DateTimeFormatter df) {
        if (null == birthDate || birthDate.length() != 8 || null == studyDate || studyDate.length() != 8) {
            // Case that may occur when reading anonymized reports
            return 0;
        }

        try {
            LocalDate startDate = LocalDate.parse(birthDate, df);
            LocalDate endDate = LocalDate.parse(studyDate, df);

            Period diff = Period.between(startDate, endDate);
            long days = diff.getYears() * 365L + diff.getMonths() * 30L + diff.getDays();
            return (int) Math.floorDiv(days, 365);
        }
        catch (DateTimeParseException dtpe) {
            // Since this is calculated from patient ID and this value may be
            // anonymized (to something not being a date), we will accept this
            // and assign age 0
            return 0;
        }
    }

    /**
     * Check that the dati are related (same patient, study, etc)
     */
    public static boolean refersToSameStudy(Collection<BreastDensityDatum> dati) {
        HashSet<String> patientId = new HashSet<>();
        HashSet<String> birthDate = new HashSet<>();
        HashSet<String> studyInstanceUid = new HashSet<>();

        for (BreastDensityDatum datum : dati) {
            patientId.add(datum.patientID);
            birthDate.add(datum.patientBirthDate);
            studyInstanceUid.add(datum.studyInstanceUID);
        }

        return patientId.size() == 1 && birthDate.size() == 1 && studyInstanceUid.size() == 1;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Datum:");
        buf.append(" patient-id=\"").append(patientID).append("\"");
        buf.append(" patient-birth-date=\"").append(patientBirthDate).append("\"");
        buf.append(" patient-gender=\"").append(patientGender).append("\"");
        buf.append(" accession-number=\"").append(accessionNumber).append("\"");
        buf.append(" study-instance-uid=\"").append(studyInstanceUID).append("\"");
        buf.append(" study-date=\"").append(studyDate).append("\"");
        buf.append(" age-at-time-of-study=\"").append(ageAtTimeOfStudy).append("\"");
        buf.append(" series-instance-uid=\"").append(seriesInstanceUID).append("\"");
        buf.append(" sop-instance-uid=\"").append(sopInstanceUID).append("\"");
        buf.append(" left-breast-density=\"");
        if (hasLeftBreast()) {
            buf.append(leftBreastDensity);
        }
        buf.append("\"");
        buf.append(" right-breast-density=\"");
        if (hasRightBreast()) {
            buf.append(rightBreastDensity);
        }
        buf.append("\"");
        buf.append(" radiologist=\"");
        if (null != radiologist && radiologist.length() > 0) {
            buf.append(radiologist);
        }
        buf.append("\"");
        buf.append(" operator=\"");
        if (null != operator && operator.length() > 0) {
            buf.append(operator);
        }
        buf.append("\"");
        buf.append(" report-description=\"");
        if (null != reportDescription && reportDescription.length() > 0) {
            buf.append(reportDescription);
        }
        buf.append("\"");
        return buf.toString();
    }
}
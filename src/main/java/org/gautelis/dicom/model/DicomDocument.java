/*
 * Copyright (C) 2016-2021 Frode Randers
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
package org.gautelis.dicom.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by froran on 2016-01-28.
 */
public class DicomDocument {
    private static final Logger log = LoggerFactory.getLogger(DicomDocument.class);

    /*
     * @see http://www.dicomlibrary.com/dicom/sop/
     */
    public enum Type{
        // SOP UID	SOP name
        Verification_SOP_Class("1.2.840.10008.1.1", "Verification", false),
        Storage_Commitment_Push_Model_SOP_Class("1.2.840.10008.1.20.1", "Storage Commitment Push Model", false),
        Storage_Commitment_Pull_Model_SOP_Class("1.2.840.10008.1.20.2", "Storage Commitment Pull Model", true),
        Media_Storage_Directory_Storage("1.2.840.10008.1.3.10", "Media Storage Directory", false),
        Procedural_Event_Logging_SOP_Class("1.2.840.10008.1.40", "Procedural Event Logging", false),
        Basic_Study_Content_Notification_SOP_Class("1.2.840.10008.1.9", "Basic Study Content Notification", true),

        // SOP UID	SOP name
        Detached_Patient_Management_SOP_Class("1.2.840.10008.3.1.2.1.1", "Detached Patient Management", true),
        Detached_Patient_Management_Meta_SOP_Class("1.2.840.10008.3.1.2.1.4", "Detached Patient Management Meta", true),
        Detached_Visit_Management_SOP_Class("1.2.840.10008.3.1.2.2.1", "Detached Visit Management", true),
        Detached_Study_Management_SOP_Class("1.2.840.10008.3.1.2.3.1", "Detached Study Management", true),
        Study_Componenet_Management_SOP_Class("1.2.840.10008.3.1.2.3.2", "Study Componenet Management", true),
        Modality_Performed_Procedure_Step_SOP_Class("1.2.840.10008.3.1.2.3.3", "Modality Performed Procedure Step", false),
        Modality_Performed_Procedure_Step_Retrieve_SOP_Class("1.2.840.10008.3.1.2.3.4", "Modality Performed Procedure Step Retrieve", false),
        Modality_Performed_Procedure_Step_Notification_SOP_Class("1.2.840.10008.3.1.2.3.5", "Modality Performed Procedure Step Notification", false),
        Detached_Results_Management_SOP_Class("1.2.840.10008.3.1.2.5.1", "Detached Results Management", true),
        Detached_Results_Management_Meta_SOP_Class("1.2.840.10008.3.1.2.5.4", "Detached Results Management Meta", true),
        Detached_Study_Management_Meta_SOP_Class("1.2.840.10008.3.1.2.5.5", "Detached Study Management Meta", true),
        Detached_Interpretation_Management_SOP_Class("1.2.840.10008.3.1.2.6.1", "Detached Interpretation Management", true),

        // SOP UID	SOP name
        Storage_Service_Class("1.2.840.10008.4.2", "Storage Service Class", false),

        // SOP UID	SOP name
        Basic_Film_Session_SOP_Class("1.2.840.10008.5.1.1.1", "Basic Film Session", false),
        Print_Job_SOP_Class("1.2.840.10008.5.1.1.14", "Print Job", false),
        Basic_Annotation_Box_SOP_Class("1.2.840.10008.5.1.1.15", "Basic Annotation Box", false),
        Printer_SOP_Class("1.2.840.10008.5.1.1.16", "Printer", false),
        Printer_Configuration_Retrieval_SOP_Class("1.2.840.10008.5.1.1.16.376", "Printer Configuration Retrieval", false),
        Basic_Color_Print_Management_Meta_SOP_Class("1.2.840.10008.5.1.1.18", "Basic Color Print Management Meta", false),
        Referenced_Color_Print_Management_Meta_SOP_Class("1.2.840.10008.5.1.1.18.1", "Referenced Color Print Management Meta", true),
        Basic_Film_Box_SOP_Class("1.2.840.10008.5.1.1.2", "Basic Film Box", false),
        VOI_LUT_Box_SOP_Class("1.2.840.10008.5.1.1.22", "VOI LUT Box", false),
        Presentation_LUT_SOP_Class("1.2.840.10008.5.1.1.23", "Presentation LUT", false),
        Image_Overlay_Box_SOP_Class("1.2.840.10008.5.1.1.24", "Image Overlay Box", true),
        Basic_Print_Image_Overlay_Box_SOP_Class("1.2.840.10008.5.1.1.24.1", "Basic Print Image Overlay Box", true),
        Print_Queue_Management_SOP_Class("1.2.840.10008.5.1.1.26", "Print Queue Management", true),
        Stored_Print_Storage_SOP_Class("1.2.840.10008.5.1.1.27", "Stored Print", true),
        Hardcopy_Grayscale_Image_Storage_SOP_Class("1.2.840.10008.5.1.1.29", "Hardcopy Grayscale Image", true),
        Hardcopy_Color_Image_Storage_SOP_Class("1.2.840.10008.5.1.1.30", "Hardcopy Color Image", true),
        Pull_Print_Request_SOP_Class("1.2.840.10008.5.1.1.31", "Pull Print Request", true),
        Pull_Stored_Print_Management_Meta_SOP_Class("1.2.840.10008.5.1.1.32", "Pull Stored Print Management Meta", true),
        Media_Creation_Management_SOP_Class_UID("1.2.840.10008.5.1.1.33", "Media Creation Management", false),
        Basic_Grayscale_Image_Box_SOP_Class("1.2.840.10008.5.1.1.4", "Basic Grayscale Image Box", false),
        Basic_Color_Image_Box_SOP_Class("1.2.840.10008.5.1.1.4.1", "Basic Color Image Box", false),
        Referenced_Image_Box_SOP_Class("1.2.840.10008.5.1.1.4.2", "Referenced Image Box", true),
        Basic_Grayscale_Print_Management_Meta_SOP_Class("1.2.840.10008.5.1.1.9", "Basic Grayscale Print Management Meta", false),
        Referenced_Grayscale_Print_Management_Meta_SOP_Class("1.2.840.10008.5.1.1.9.1", "Referenced Grayscale Print Management Meta", true),

        // SOP UID	SOP name
        Image_Storage("1.2.840.10008.5.1.4.1.1.1", "Image", false),
        Digital_X_Ray_Image_Storage_for_Presentation("1.2.840.10008.5.1.4.1.1.1.1", "Digital X-Ray Image – for Presentation", false),
        Digital_X_Ray_Image_Storage_for_Processing("1.2.840.10008.5.1.4.1.1.1.1.1", "Digital X-Ray Image – for Processing", false),
        Digital_Mammography_X_Ray_Image_Storage_for_Presentation("1.2.840.10008.5.1.4.1.1.1.2", "Digital Mammography X-Ray Image (for Presentation)", false),
        Digital_Mammography_X_Ray_Image_Storage_for_Processing("1.2.840.10008.5.1.4.1.1.1.2.1", "Digital Mammography X-Ray Image (for Processing)", false),
        Digital_Intra_oral_X_Ray_Image_Storage_for_Presentation("1.2.840.10008.5.1.4.1.1.1.3", "Digital Intra – oral X-Ray Image (for Presentation)", false),
        Digital_Intra_oral_X_Ray_Image_Storage_for_Processing("1.2.840.10008.5.1.4.1.1.1.3.1", "Digital Intra – oral X-Ray Image (for Processing)", false),
        Standalone_Modality_LUT_Storage("1.2.840.10008.5.1.4.1.1.10", "Standalone Modality LUT", true),
        Encapsulated_PDF_Storage("1.2.840.10008.5.1.4.1.1.104.1", "Encapsulated PDF", false),
        Standalone_VOI_LUT_Storage("1.2.840.10008.5.1.4.1.1.11", "Standalone VOI LUT", true),
        Grayscale_Softcopy_Presentation_State_Storage_SOP_Class("1.2.840.10008.5.1.4.1.1.11.1", "Grayscale Softcopy Presentation State", false),
        Color_Softcopy_Presentation_State_Storage_SOP_Class("1.2.840.10008.5.1.4.1.1.11.2", "Color Softcopy Presentation State", false),
        Pseudocolor_Softcopy_Presentation_Stage_Storage_SOP_Class("1.2.840.10008.5.1.4.1.1.11.3", "Pseudocolor Softcopy Presentation State", false),
        Blending_Softcopy_Presentation_State_Storage_SOP_Class("1.2.840.10008.5.1.4.1.1.11.4", "Blending Softcopy Presentation State", false),
        X_Ray_Angiographic_Image_Storage("1.2.840.10008.5.1.4.1.1.12.1", "X-Ray Angiographic Image", false),
        Enhanced_XA_Image_Storage("1.2.840.10008.5.1.4.1.1.12.1.1", "Enhanced XA Image", false),
        X_Ray_Radiofluoroscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.12.2", "X-Ray Radiofluoroscopic Image", false),
        Enhanced_XRF_Image_Storage("1.2.840.10008.5.1.4.1.1.12.2.1", "Enhanced XRF Image", false),
        X_Ray_Angiographic_Bi_plane_Image_Storage("1.2.840.10008.5.1.4.1.1.12.3", "X-Ray Angiographic Bi-plane Image", true),
        Positron_Emission_Tomography_Curve_Storage("1.2.840.10008.5.1.4.1.1.128", "Positron Emission Tomography Curve", true),
        Standalone_Positron_Emission_Tomography_Curve_Storage("1.2.840.10008.5.1.4.1.1.129", "Standalone Positron Emission Tomography Curve", true),
        CT_Image_Storage("1.2.840.10008.5.1.4.1.1.2", "CT Image", false),
        Enhanced_CT_Image_Storage("1.2.840.10008.5.1.4.1.1.2.1", "Enhanced CT Image", false),
        NM_Image_torage("1.2.840.10008.5.1.4.1.1.20", "NM Image", false),
        Ultrasound_Multiframe_Image_Storage("1.2.840.10008.5.1.4.1.1.3", "Ultrasound Multiframe Image", true),
        Ultrasound_Multiframe_Image_Storage_2("1.2.840.10008.5.1.4.1.1.3.1", "Ultrasound Multiframe Image", false),
        MR_Image_Storage("1.2.840.10008.5.1.4.1.1.4", "MR Image", false),
        Enhanced_MR_Image_Storage("1.2.840.10008.5.1.4.1.1.4.1", "Enhanced MR Image", false),
        MR_Spectroscopy_Storage("1.2.840.10008.5.1.4.1.1.4.2", "MR Spectroscopy", false),

        // SOP UID	SOP name
        Radiation_Therapy_Image_Storage("1.2.840.10008.5.1.4.1.1.481.1", "Radiation Therapy Image", false),
        Radiation_Therapy_Dose_Storage("1.2.840.10008.5.1.4.1.1.481.2", "Radiation Therapy Dose", false),
        Radiation_Therapy_Structure_Set_Storage("1.2.840.10008.5.1.4.1.1.481.3", "Radiation Therapy Structure Set", false),
        Radiation_Therapy_Beams_Treatment_Record_Storage("1.2.840.10008.5.1.4.1.1.481.4", "Radiation Therapy Beams Treatment Record", false),
        Radiation_Therapy_Plan_Storage("1.2.840.10008.5.1.4.1.1.481.5", "Radiation Therapy Plan", false),
        Radiation_Therapy_Brachy_Treatment_Record_Storage("1.2.840.10008.5.1.4.1.1.481.6", "Radiation Therapy Brachy Treatment Record", false),
        Radiation_Therapy_Treatment_Summary_Record_Storage("1.2.840.10008.5.1.4.1.1.481.7", "Radiation Therapy Treatment Summary Record", false),
        Radiation_Therapy_Ion_Plan_Storage("1.2.840.10008.5.1.4.1.1.481.8", "Radiation Therapy Ion Plan", false),
        Radiation_Therapy_Ion_Beams_Treatment_Record_Storage("1.2.840.10008.5.1.4.1.1.481.9", "Radiation Therapy Ion Beams Treatment Record", false),

        // SOP UID	SOP name
        NM_Image_Storage("1.2.840.10008.5.1.4.1.1.5", "NM Image", true),

        // SOP UID	SOP name
        Ultrasound_Image_Storage("1.2.840.10008.5.1.4.1.1.6", "Ultrasound Image", true),
        Ultrasound_Image_Storage_2("1.2.840.10008.5.1.4.1.1.6.1", "Ultrasound Image", false),
        Raw_Data_Storage("1.2.840.10008.5.1.4.1.1.66", "Raw Data", false),
        Spatial_Registration_Storage("1.2.840.10008.5.1.4.1.1.66.1", "Spatial Registration", false),
        Spatial_Fiducials_Storage("1.2.840.10008.5.1.4.1.1.66.2", "Spatial Fiducials", false),
        Deformable_Spatial_Registration_Storage("1.2.840.10008.5.1.4.1.1.66.3", "Deformable Spatial Registration", false),
        Segmentation_Storage("1.2.840.10008.5.1.4.1.1.66.4", "Segmentation", false),
        Real_World_Value_Mapping_Storage("1.2.840.10008.5.1.4.1.1.67", "Real World Value Mapping", false),

        // SOP UID	SOP name
        Secondary_Capture_Image_Storage("1.2.840.10008.5.1.4.1.1.7", "Secondary Capture Image", false),
        Multiframe_Single_Bit_Secondary_Capture_Image_Storage("1.2.840.10008.5.1.4.1.1.7.1", "Multiframe Single Bit Secondary Capture Image", false),
        Multiframe_Grayscale_Byte_Secondary_Capture_Image_Storage("1.2.840.10008.5.1.4.1.1.7.2", "Multiframe Grayscale Byte Secondary Capture Image", false),
        Multiframe_Grayscale_Word_Secondary_Capture_Image_Storage("1.2.840.10008.5.1.4.1.1.7.3", "Multiframe Grayscale Word Secondary Capture Image", false),
        Multiframe_True_Color_Secondary_Capture_Image_Storage("1.2.840.10008.5.1.4.1.1.7.4", "Multiframe True Color Secondary Capture Image", false),
        Visible_Light_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1", "Visible Light Image", true),
        Visible_Light_endoscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.1", "Visible Light endoscopic Image", false),
        Video_Endoscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.1.1", "Video Endoscopic Image", false),
        Visible_Light_Microscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.2", "Visible Light Microscopic Image", false),
        Video_Microscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.2.1", "Video Microscopic Image", false),
        Visible_Light_Slide_Coordinates_Microscopic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.3", "Visible Light Slide-Coordinates Microscopic Image", false),
        Visible_Light_Photographic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.4", "Visible Light Photographic Image", false),
        Video_Photographic_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.4.1", "Video Photographic Image", false),
        Ophthalmic_Photography_8_Bit_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.5.1", "Ophthalmic Photography 8-Bit Image", false),
        Ophthalmic_Photography_16_Bit_Image_Storage("1.2.840.10008.5.1.4.1.1.77.1.5.2", "Ophthalmic Photography 16-Bit Image", false),
        Stereometric_Relationship_Storage("1.2.840.10008.5.1.4.1.1.77.1.5.3", "Stereometric Relationship", false),
        Visible_Light_Multiframe_Image_Storage("1.2.840.10008.5.1.4.1.1.77.2", "Visible Light Multiframe Image", true),

        // SOP UID	SOP name
        Standalone_Overlay_Storage("1.2.840.10008.5.1.4.1.1.8", "Standalone Overlay", true),
        Basic_Text_SR("1.2.840.10008.5.1.4.1.1.88.11", "Basic Text SR", false),
        Enhanced_SR("1.2.840.10008.5.1.4.1.1.88.22", "Enhanced SR", false),
        Comprehensive_SR("1.2.840.10008.5.1.4.1.1.88.33", "Comprehensive SR", false),
        Procedure_Log_Storage("1.2.840.10008.5.1.4.1.1.88.40", "Procedure Log", false),
        Mammography_CAD_SR("1.2.840.10008.5.1.4.1.1.88.50", "Mammography CAD SR", false),
        Key_Object_Selection_Document("1.2.840.10008.5.1.4.1.1.88.59", "Key Object Selection Document", false),
        Chest_CAD_SR("1.2.840.10008.5.1.4.1.1.88.65", "Chest CAD SR", false),
        X_Ray_Radiation_Dose_SR("1.2.840.10008.5.1.4.1.1.88.67", "X-Ray Radiation Dose SR", false),

        // SOP UID	SOP name
        Standalone_Curve_Storage("1.2.840.10008.5.1.4.1.1.9", "Standalone Curve", true),
        Twelve_lead_ECG_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.1.1", "12-lead ECG Waveform", false),
        General_ECG_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.1.2", "General ECG Waveform", false),
        Ambulatory_ECG_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.1.3", "Ambulatory ECG Waveform", false),
        Hemodynamic_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.2.1", "Hemodynamic Waveform", false),
        Cardiac_Electrophysiology_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.3.1", "Cardiac Electrophysiology Waveform", false),
        Basic_Voice_Audio_Waveform_Storage("1.2.840.10008.5.1.4.1.1.9.4.1", "Basic Voice Audio Waveform", false),

        // SOP UID	SOP name
        Patient_Root_Query_Retrieve_Information_Model_FIND("1.2.840.10008.5.1.4.1.2.1.1", "Patient Root Query/Retrieve Information Model – FIND", false),
        Patient_Root_Query_Retrieve_Information_Model_MOVE("1.2.840.10008.5.1.4.1.2.1.2", "Patient Root Query/Retrieve Information Model – MOVE", false),
        Patient_Root_Query_Retrieve_Information_Model_GET("1.2.840.10008.5.1.4.1.2.1.3", "Patient Root Query/Retrieve Information Model – GET", false),
        Study_Root_Query_Retrieve_Information_Model_FIND("1.2.840.10008.5.1.4.1.2.2.1", "Study Root Query/Retrieve Information Model – FIND", false),
        Study_Root_Query_Retrieve_Information_Model_MOVE("1.2.840.10008.5.1.4.1.2.2.2", "Study Root Query/Retrieve Information Model – MOVE", false),
        Study_Root_Query_Retrieve_Information_Model_GET("1.2.840.10008.5.1.4.1.2.2.3", "Study Root Query/Retrieve Information Model – GET", false),
        Patient_Study_Only_Query_Retrieve_Information_Model_FIND("1.2.840.10008.5.1.4.1.2.3.1", "Patient/Study Only Query/Retrieve Information Model – FIND", true),
        Patient_Study_Only_Query_Retrieve_Information_Model_MOVE("1.2.840.10008.5.1.4.1.2.3.2", "Patient/Study Only Query/Retrieve Information Model – MOVE", true),
        Patient_Study_Only_Query_Retrieve_Information_Model_GET("1.2.840.10008.5.1.4.1.2.3.3", "Patient/Study Only Query/Retrieve Information Model – GET", true),

        // SOP UID	SOP name
        Modality_Worklist_Information_Model_FIND("1.2.840.10008.5.1.4.31", "Modality Worklist Information Model – FIND", false),
        General_Purpose_Worklist_Management_Meta_SOP_Class("1.2.840.10008.5.1.4.32", "General Purpose Worklist Management Meta", false),
        General_Purpose_Worklist_Information_Model_FIND("1.2.840.10008.5.1.4.32.1", "General Purpose Worklist Information Model – FIND", false),
        General_Purpose_Scheduled_Procedure_Step_SOP_Class("1.2.840.10008.5.1.4.32.2", "General Purpose Scheduled Procedure Step", false),
        General_Purpose_Performed_Procedure_Step_SOP_Class("1.2.840.10008.5.1.4.32.3", "General Purpose Performed Procedure Step", false),
        Instance_Availability_Notification_SOP_Class("1.2.840.10008.5.1.4.33", "Instance Availability Notification", false),
        General_Relevant_Patient_Information_Query("1.2.840.10008.5.1.4.37.1", "General Relevant Patient Information Query", false),
        Breast_Imaging_Relevant_Patient_Information_Query("1.2.840.10008.5.1.4.37.2", "Breast Imaging Relevant Patient Information Query", false),
        Cardiac_Relevant_Patient_Information_Query("1.2.840.10008.5.1.4.37.3", "Cardiac Relevant Patient Information Query", false),
        Hanging_Protocol_Storage("1.2.840.10008.5.1.4.38.1", "Hanging Protocol", false),
        Hanging_Protocol_Information_Model_FIND("1.2.840.10008.5.1.4.38.2", "Hanging Protocol Information Model – FIND", false),
        Hanging_Protocol_Information_Model_MOVE("1.2.840.10008.5.1.4.38.3", "Hanging Protocol Information Model – MOVE", false),
        Unknown("<unknown>", "<unknown>", false);

        //
        private final String sopUID;
        private final String description;
        private final boolean isRetired;

        Type(String sopUID, String description, boolean isRetired){
            this.sopUID = sopUID;
            this.description = description;
            this.isRetired = isRetired;
        }

        public String getSopUID() {
            return sopUID;
        }

        public String getDescription(){
            return this.description;
        }

        public boolean isRetired() {
            return isRetired;
        }

        // Create index over all entries
        private static final Map<String, Type> index = new HashMap<>();
        static {
            for (Type type : Type.values()) {
                index.put(type.getSopUID(), type);
            }
        }

        public static Type find(String sopClassUID) {
            Type type = index.get(sopClassUID);
            if (null == type) {
                return Unknown;
            }
            return type;
        }

        @Override
        public String toString(){
            return getDescription();
        }
    }

    private Type type;
    private final String name;
    private final String path;
    private final DicomElement rootElement;

    public DicomDocument(DicomElement dicomElement, String name, String path) {
        this.rootElement = dicomElement;
        this.name = name;
        this.path = path; // may be null

        this.type = Type.find(dicomElement.getSopClassUID());
    }

    public DicomElement getRootElement() {
        return rootElement;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public Type getType() {
        return type;
    }

    public String asText(boolean recurse) {
        return rootElement.asText(recurse);
    }

    @Override
    public String toString(){
        return "DicomDocument {file=\"" + getName() + "\" type=\"" + getType().getDescription() + "\"}";
    }
}

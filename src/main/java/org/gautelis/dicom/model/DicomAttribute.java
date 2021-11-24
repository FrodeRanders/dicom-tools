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

import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

/**
 * Created by froran on 2016-01-28.
 */
public class DicomAttribute {

    protected static ElementDictionary dict = ElementDictionary.getStandardElementDictionary();

    private final int tag;
    private final VR vr;
    private final String id;
    private final String name;
    private final String description;
    private final String value;
    private final DicomElement owner;


    public DicomAttribute(int tag, VR vr, String value, DicomElement owner) {
        this.tag = tag;
        this.vr = vr;
        id = TagUtils.toString(tag);
        name = dict.keywordOf(tag);

        description = id + " " + name;
        this.value = value;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public VR getVR() {
        return vr;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() { return value; }

    public DicomElement getOwner() {
        return owner;
    }

    public String asText() {
        return asText("");
    }

    public String asText(String prefix) {
        String text = prefix;
        text += description + " :: ";
        text += value;
        text += "\n";
        return text;
    }

    @Override
    public String toString(){
        return "DicomAttribute {" + id  + " " + vr.name() + " [" + value + "] " + name + "}";
    }
}

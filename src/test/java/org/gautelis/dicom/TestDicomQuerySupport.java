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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryTask;

import java.util.ArrayList;
import java.util.List;

final class TestDicomQuerySupport {
    private TestDicomQuerySupport() {
    }

    static Attributes match(String studyInstanceUID, String patientId) {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        attributes.setString(Tag.PatientID, VR.LO, patientId);
        return attributes;
    }

    static List<Attributes> filterByStudyInstanceUID(List<Attributes> matches, Attributes keys) {
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID, null);
        if (studyInstanceUID == null) {
            return matches;
        }
        List<Attributes> filtered = new ArrayList<>();
        for (Attributes candidate : matches) {
            if (studyInstanceUID.equals(candidate.getString(Tag.StudyInstanceUID))) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    interface MatchFilter {
        boolean matches(Attributes candidate, Attributes keys);
    }

    static class StaticCFindSCP extends BasicCFindSCP {
        private final List<Attributes> matches;
        private final MatchFilter filter;

        StaticCFindSCP(List<Attributes> matches) {
            this(matches, null);
        }

        StaticCFindSCP(List<Attributes> matches, MatchFilter filter) {
            super(UID.StudyRootQueryRetrieveInformationModelFind);
            this.matches = matches;
            this.filter = filter;
        }

        @Override
        protected QueryTask calculateMatches(
                Association as,
                PresentationContext pc,
                Attributes rq,
                Attributes keys
        ) throws DicomServiceException {
            List<Attributes> filtered = new ArrayList<>();
            for (Attributes candidate : matches) {
                if (filter == null || filter.matches(candidate, keys)) {
                    filtered.add(candidate);
                }
            }
            return new StaticQueryTask(as, pc, rq, keys, filtered);
        }
    }

    static class StaticQueryTask extends BasicQueryTask {
        private final List<Attributes> matches;
        private int index = 0;

        StaticQueryTask(
                Association as,
                PresentationContext pc,
                Attributes rq,
                Attributes keys,
                List<Attributes> matches
        ) {
            super(as, pc, rq, keys);
            this.matches = matches;
        }

        @Override
        protected boolean hasMoreMatches() {
            return index < matches.size();
        }

        @Override
        protected Attributes nextMatch() {
            return matches.get(index++);
        }
    }
}

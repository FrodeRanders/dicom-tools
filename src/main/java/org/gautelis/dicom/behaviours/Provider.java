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
package org.gautelis.dicom.behaviours;

public interface Provider {

    /**
     * A provider accepting structured reports, could return
     * {UID.BasicTextSRStorage, UID.EnhancedSRStorage, UID.ComprehensiveSRStorage, UID.MammographyCADSRStorage}
     * where UID is org.dcm4che3.data.UID.
     *
     * @return an array of SOP classes that is accepted
     */
    String[] providesSOPClasses();

    void shutdown();
}

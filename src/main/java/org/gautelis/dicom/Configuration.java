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

import org.gautelis.vopn.lang.Configurable;

public interface Configuration {
    @Configurable(property = "max-units-processed-in-sequence", value = "5000")
    int maxUnitsProcessedInSequence();

    @Configurable(property = "max-units-processed-per-minute", value = "30")
    int maxUnitsProcessedPerMinute();

    @Configurable(property = "remote-host")
    String remoteHost();

    @Configurable(property = "remote-port")
    int remotePort();

    @Configurable(property = "remote-application-entity")
    String remoteApplicationEntity();

    @Configurable(property = "local-scu-application-entity")
    String localScuApplicationEntity();

    @Configurable(property = "local-scu-modality", value = "OT")
    String localScuModality();

    @Configurable(property = "local-scp-application-entity", value = "MY_SCU")
    String localScpApplicationEntity();

    @Configurable(property = "local-scp-host", value = "localhost")
    String localScpHost();

    @Configurable(property = "local-scp-port", value = "4100")
    int localScpPort();

    @Configurable(property = "storage-directory", value = "./STORAGE")
    String storageDirectory();

    @Configurable(property = "accepted-calling-aets", value = "MY_SCP")
    String acceptedCallingAETitles();

    @Configurable(property = "username")
    String username();

    @Configurable(property = "password")
    String password();

    @Configurable(property = "debug", value = "false")
    boolean doDebug();
}

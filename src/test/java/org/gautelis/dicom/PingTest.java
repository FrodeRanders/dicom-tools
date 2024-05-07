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
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Creates a joint SCP/SCU pair. The local SCP is started and then the local SCU
 * connects to it and sends a ping. From a SCU viewpoint, the local SCP could
 * as well have been a remote SCP.
 * <p>
 * Normally, we would operate with a PACS (remote SCP) and a local SCU/SCP pair,
 * where the SCU connects to the PACS (remote SCP) and issues commands. Some PACS
 * do not support retrieving files and instead has to be instructed to MOVE the
 * file to the local SCP. The local SCP will then receive a C-STORE command from
 * the PACS (remote SCP).
 * <p>
 * The configuration below may seem a bit artificial, but it is actually a common
 * setup. In our test, we will only use a single SCP
 */
public class PingTest
{
    private static final Logger log = LogManager.getLogger(PingTest.class);

    @Test
    public void testPing() {
        System.out.println("---");

        final Properties dicomConfig = new Properties();

        // SCU / client-side configuration
        dicomConfig.setProperty("local-scu-application-entity", "MY_SCU");
        dicomConfig.setProperty("local-scu-modality-type", "OT");

        // SCP / client-side configuration. This configuration must match the
        // remote SCP configuration below -- otherwise the SCU will not be able
        // to connect back to the (local) SCP.
        dicomConfig.setProperty("local-scp-application-entity", "MY_SCP");
        dicomConfig.setProperty("local-scp-host", "localhost");
        dicomConfig.setProperty("local-scp-port", "4101");

        dicomConfig.setProperty("storage-directory", "./STORAGE");

        // The local SCP accepts connections from these application entities
        dicomConfig.setProperty("accepted-calling-aets", "MY_SCU,REMOTE_SCP,SOME_PACS");

        // Remote SCP (which is actually same as local SCP :) as seen from the SCU
        dicomConfig.setProperty("remote-application-entity", "MY_SCP");
        dicomConfig.setProperty("remote-host", "localhost");
        dicomConfig.setProperty("remote-port", "4101");

        try (Controller controller = new Controller(dicomConfig)) {
            assertTrue(controller.ping());
            System.out.println("PING!");
        }
        catch (Throwable t) {
            fail(t.getMessage());
        }
    }
}

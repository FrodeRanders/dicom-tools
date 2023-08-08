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
package org.gautelis.dicom.net;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.pdu.UserIdentityRQ;
import org.gautelis.dicom.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Service class user (SCU) node
 */
public class DicomScuNode extends DicomNode implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DicomScuNode.class);

    public DicomScuNode(Configuration dicomConfig) {
        // Client side representation of the connection
        local.setHostname(dicomConfig.localScpHost());
        local.setPort(Connection.NOT_LISTENING);

        // Remote side representation of the connection
        remote.setHostname(dicomConfig.remoteHost());
        remote.setPort(dicomConfig.remotePort());

        remote.setTlsProtocols(local.getTlsProtocols());
        remote.setTlsCipherSuites(local.getTlsCipherSuites());

        // Calling application entity
        ae = new ApplicationEntity(dicomConfig.localScuApplicationEntity().toUpperCase());
        ae.setAETitle(dicomConfig.localScuApplicationEntity());
        ae.addConnection(local); // on which we may not be listening
        ae.setAssociationInitiator(true);
        ae.setAssociationAcceptor(false);

        // Device
        device = new Device(dicomConfig.localScuApplicationEntity().toLowerCase());
        device.addConnection(local);
        device.addApplicationEntity(ae);
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);

        // Configure association
        rq.setCallingAET(dicomConfig.localScuApplicationEntity());
        rq.setCalledAET(dicomConfig.remoteApplicationEntity());
        rq.setImplVersionName("GAUTELIS-SCU"); // Max 16 chars

        // Credentials (if appropriate)
        String username = dicomConfig.username();
        String password = dicomConfig.password();
        if (null != username && !username.isEmpty()
                && null != password && !password.isEmpty()) {
            rq.setUserIdentityRQ(UserIdentityRQ.usernamePasscode(username, password.toCharArray(), true));
        }
    }
}

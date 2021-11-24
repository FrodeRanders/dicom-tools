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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.UserIdentityRQ;
import org.gautelis.dicom.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/*
 * Service class user (SCU) node
 */
public class DicomScuNode extends DicomNode {
    private static final Logger log = LoggerFactory.getLogger(DicomScuNode.class);

    // Transient!
    private Association association = null;

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

        // Configure association
        rq.setCallingAET(dicomConfig.localScuApplicationEntity());
        rq.setCalledAET(dicomConfig.remoteApplicationEntity());
        rq.setImplVersionName("GAUTELIS-SCU"); // Max 16 chars

        // Credentials (if appropriate)
        String username = dicomConfig.username();
        String password = dicomConfig.password();
        if (null != username && username.length() > 0
                && null != password && password.length() > 0) {
            rq.setUserIdentityRQ(UserIdentityRQ.usernamePasscode(username, password.toCharArray(), true));
        }

        //
        executorService = Executors.newCachedThreadPool();
        device.setExecutor(executorService);

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setScheduledExecutor(scheduledExecutorService);
    }

    public void shutdown() {
        try {
            close();

        } catch (InterruptedException ie) {
            String info = "Outstanding RSP problem: " + ie.getMessage();
            log.warn(info);

        } catch (IOException ioe) {
            String info = "Could not disconnect: " + ioe.getMessage();
            log.warn(info);
        }

        scheduledExecutorService.shutdown();
        executorService.shutdown();
    }


    public void open()
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {

        if (null == association) {
            association = ae.connect(local, remote, rq);
        }
    }

    public void close() throws IOException, InterruptedException {
        if (association != null && association.isReadyForDataTransfer()) {
            association.waitForOutstandingRSP();
            association.release();
            association = null;
        }
    }

    public boolean echo() {
        if (null == association) {
            log.error("No association: echo is futile -- please open() first");
        }
        else {
            try {
                DimseRSP rsp = association.cecho();
                rsp.next(); // Consume

                return true;

            } catch (Throwable t) {
                log.warn("Failed to issue ping: {}", t.getMessage(), t);
            }
        }
        return false;
    }

    private void query(
            String CUID, Attributes keys, DimseRSPHandler rspHandler
    ) throws IOException, InterruptedException {
        if (null == association) {
            log.error("No association: query is futile -- please open() first");
        }
        else {
            int priority = 0x0002; // LOW
            association.cfind(CUID, priority, keys, null, rspHandler);
        }
    }

    public void query(
            String CUID, Attributes keys, Consumer<Attributes> block
    ) throws IOException, InterruptedException {
        if (null == association) {
            log.error("No association: query is futile -- please open() first");
        }
        else {
            query(CUID, keys, new DimseRSPHandler(association.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd,
                                       Attributes data) {

                    int status = cmd.getInt(Tag.Status, -1);
                    if (Status.Success != status && Status.Pending != status) {
                        log.trace("C-FIND: Received DimseRSP: Command status: {} ({})",
                                String.format("%04X", status & 0xFFFF), statusToString(status)
                        );
                    }

                    super.onDimseRSP(as, cmd, data);

                    if (Status.isPending(status)) {
                        block.accept(data);
                    }
                }
            });
        }
    }

    private void retrieve(
            String CUID, Attributes keys, String destinationAET, DimseRSPHandler rspHandler
    ) throws IOException, InterruptedException {
        if (null == association) {
            log.error("No association: retrieve is futile -- please open() first");
        }
        else {
            int priority = 0x0002; // LOW
            association.cmove(CUID, priority, keys, null, destinationAET, rspHandler);
        }
    }

    public Optional<Integer> retrieve(
            String CUID, Attributes keys, String destinationAET
    ) throws IOException, InterruptedException {
        int[] status = {-1};

        if (null == association) {
            log.error("No association: retrieve is futile -- please open() first");
        }
        else {
            retrieve(CUID, keys, destinationAET, new DimseRSPHandler(association.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {

                    int _status = cmd.getInt(Tag.Status, -1);
                    if (Status.Success != _status) {
                        log.trace("C-MOVE: Received DimseRSP: Command status: {} ({})",
                                String.format("%04X", _status & 0xFFFF), statusToString(_status)
                        );
                    }

                    status[0] = _status;

                    //
                    super.onDimseRSP(as, cmd, data);
                }
            });
        }

        if (status[0] < 0) {
            return Optional.empty();
        }
        return Optional.of(status[0]);
    }
}

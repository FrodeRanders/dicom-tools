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

import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.gautelis.dicom.net.DicomScpNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ProviderBehaviour<T extends BasicCStoreSCP & Provider> {
    private static final Logger log = LoggerFactory.getLogger(ProviderBehaviour.class);

    private final DicomScpNode scp;
    private final T provider;

    /**
     * Initiates a provider behaviour, backed by a provider that accepts a set of SOP classes.
     * @param scp
     * @param acceptedAETs
     * @param provider
     */
    public ProviderBehaviour(
            DicomScpNode scp, String[] acceptedAETs, T provider
    ) {
        this.scp = scp;
        this.provider = provider;

        // Prepare for callbacks
        scp.withServiceRegistry(registry -> registry.addDicomService(provider));

        for (String cuid : provider.providesSOPClasses()) {
            addOfferedStorageSOPClass(cuid);
        }

        scp.withApplicationEntity(ae -> ae.setAcceptedCallingAETitles(acceptedAETs));

        listen();
    }

    private void addOfferedStorageSOPClass(String cuid, String... tsuids) {

        scp.withApplicationEntity(ae -> {
            if (null == ae.getTransferCapabilityFor(cuid, TransferCapability.Role.SCP)) {

                TransferCapability tc = new TransferCapability(
                        null, cuid,
                        TransferCapability.Role.SCP,
                        tsuids
                );

                ae.addTransferCapability(tc);
            }
        });

        scp.withAAssociateRQ(rq -> {
            if (!rq.containsPresentationContextFor(cuid)) {
                rq.addPresentationContext(
                        new PresentationContext(
                                2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids
                        )
                );

                rq.addRoleSelection(new RoleSelection(cuid, /* is SCU? */ false, /* is SCP? */ true));
            }
        });

        log.info("Accept SOP class (storage): " + cuid);
    }

    private void addOfferedStorageSOPClass(String cuid) {
        addOfferedStorageSOPClass(cuid, DicomScpNode.TRANSFER_SYNTAX_CHAIN);
    }

    private void listen() {
        log.debug("Binding (C-STORE) server connections");
        scp.withDevice(device -> {
            try {
                device.bindConnections();

            } catch (IOException ioe) {
                String info = "Failed to bind storage server behaviour: " + ioe.getMessage();
                log.error(info, ioe);

                throw new RuntimeException("Kunde inte initiera serverdel: " + info, ioe);

            } catch (GeneralSecurityException gse) {
                String info = "Not allowed to bind storage server behaviour: " + gse.getMessage();
                log.error(info, gse);

                throw new RuntimeException("Kunde inte initiera serverdel: " + info, gse);
            }
        });
    }

    public void shutdown() {
        try {
            if (null != provider) {
                provider.shutdown();
            }

            log.debug("Unbinding provider connections");
            scp.withDevice(Device::unbindConnections);

        } catch (Throwable t) {
            String info = "Failed to shutdown provider node: ";
            Throwable cause = DicomScpNode.getBaseCause(t);
            info += cause.getMessage();
            log.error(info, t);
        }
    }
}

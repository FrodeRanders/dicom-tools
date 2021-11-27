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

import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.gautelis.dicom.net.DicomScpNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        for (String cuid : provider.getSOPClasses()) {
            addOfferedStorageSOPClass(cuid);
        }

        scp.withApplicationEntity(ae -> ae.setAcceptedCallingAETitles(acceptedAETs));
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

    public void shutdown() {
        try {
            if (null != provider) {
                provider.shutdown();
            }
        } catch (Throwable t) {
            String info = "Failed to shutdown provider node: ";
            Throwable cause = DicomScpNode.getBaseCause(t);
            info += cause.getMessage();
            log.error(info, t);
        }
    }
}

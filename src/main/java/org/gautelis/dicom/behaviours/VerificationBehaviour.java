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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.gautelis.dicom.net.DicomAssociation;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/*
 * Handles PINGs from both a SCU as well as SCP perspective.
 */
public class VerificationBehaviour {
    private static final Logger log = LoggerFactory.getLogger(VerificationBehaviour.class);

    private final DicomScuNode scuNode;
    private final DicomScpNode scpNode;

    public VerificationBehaviour(DicomScuNode scuNode, DicomScpNode scpNode) {

        this.scuNode = scuNode;
        this.scpNode = scpNode;

        // Define transfer capabilities for verification SOP class on both SCP as well as SCU side
        scpNode.withApplicationEntity(ae -> ae.addTransferCapability(new TransferCapability(null,
                /* SOP Class */ UID.Verification,
                /* Role */ TransferCapability.Role.SCP,
                /* Transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN))
        );

        scuNode.withApplicationEntity(ae -> ae.addTransferCapability(new TransferCapability(null,
                /* SOP Class */ UID.Verification,
                /* Role */ TransferCapability.Role.SCU,
                /* Transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN))
        );

        // Setup presentation context on both SCP as well as SCU side
        scpNode.withAAssociateRQ(rq -> {
            rq.addPresentationContext(new PresentationContext(
                            rq.getNumberOfPresentationContexts() * 2 + 1,
                            /* abstract syntax */ UID.Verification,
                            /* transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN
                    )
            );

            rq.addRoleSelection(new RoleSelection(UID.Verification,/* is SCU? */ false, /* is SCP? */ true));
        });

        scuNode.withAAssociateRQ(rq -> {
            rq.addPresentationContext(new PresentationContext(
                            rq.getNumberOfPresentationContexts() * 2 + 1,
                    /* abstract syntax */ UID.Verification,
                    /* transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN
                )
            );

            rq.addRoleSelection(new RoleSelection(UID.Verification,/* is SCU? */ true, /* is SCP? */ false));
        });

        // We will accept verfication queries as SCP as well
        scpNode.withServiceRegistry(registry -> {
            registry.addDicomService(new BasicCEchoSCP() {
                @Override
                public void onDimseRQ(
                        Association as, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes data) throws IOException {

                    log.debug("Got " + dimse.name());  // Logs SCP side reception of PING

                    if (dimse != Dimse.C_ECHO_RQ) {
                        log.warn("Unrecognized operation: {}", dimse);
                        throw new DicomServiceException(Status.UnrecognizedOperation);
                    }
                    as.tryWriteDimseRSP(pc, Commands.mkEchoRSP(cmd, Status.Success));
                }
            });
        });
    }

    public boolean verify() {
        try {
            try (DicomAssociation association = scuNode.open()) {
                return association.echo();
            }
        } catch (Throwable t) {
            String info = "Failed to verify connection to SCP: " + t.getMessage();
            log.warn(info, t);
        }
        return false;
    }
}

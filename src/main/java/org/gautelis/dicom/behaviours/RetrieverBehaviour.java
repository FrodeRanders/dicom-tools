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
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.gautelis.dicom.net.DicomAssociation;
import org.gautelis.dicom.net.DicomScuNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Optional;
import java.util.function.Consumer;

/*
 * Relevant abstract syntax:
 *
 * - PatientRootQueryRetrieveInformationModelMove
 * - StudyRootQueryRetrieveInformationModelMove
 *
 * Irrelevant abstract syntax:
 *
 * - PatientStudyOnlyQueryRetrieveInformationModelMoveRetired
 * - CompositeInstanceRootRetrieveMove
 * - HangingProtocolInformationModelMove
 * - ColorPaletteInformationModelMove
 * - GenericImplantTemplateInformationModelMove
 * - ImplantAssemblyTemplateInformationModelMove
 * - ImplantTemplateGroupInformationModelMove
 */
public class RetrieverBehaviour {
    private static final Logger log = LoggerFactory.getLogger(RetrieverBehaviour.class);

    private final DicomScuNode node;

    public RetrieverBehaviour(DicomScuNode node) throws IOException {
        this.node = node;

        TransferCapability tc = new TransferCapability(
                null, UID.StudyRootQueryRetrieveInformationModelMove,
                TransferCapability.Role.SCU,
                DicomScuNode.TRANSFER_SYNTAX_CHAIN
        );

        node.withApplicationEntity(ae -> ae.addTransferCapability(tc));

        node.withAAssociateRQ(rq -> {
            rq.addPresentationContext(
                    new PresentationContext(
                            rq.getNumberOfPresentationContexts() * 2 + 1,
                    /* abstract syntax */ UID.StudyRootQueryRetrieveInformationModelMove,
                    /* transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN
                    )
            );

            rq.addRoleSelection(
                    new RoleSelection(
                    /* abstract syntax */ UID.StudyRootQueryRetrieveInformationModelMove,
                    /* is SCU? */ true,
                    /* is SCP? */ false
                    )
            );
        });
    }

    public Optional<Integer> retrieve(
            final Consumer<Attributes> preparationBlock,
            final String destinationAET
    ) {
        try {
            // Prepare search keys
            Attributes keys = new Attributes();
            preparationBlock.accept(keys);

            // Retrieve
            try (DicomAssociation association = node.open()) {
                return association.retrieve(UID.StudyRootQueryRetrieveInformationModelMove, keys, destinationAET);
            }
        } catch (ConnectException ce) {
            String info = "Failed to connect: ";
            Throwable cause = DicomScuNode.getBaseCause(ce);
            info += cause.getMessage();
            log.warn(info);

            throw new RuntimeException(info, ce);

        } catch (Throwable t) {
            String info = "Failed to C-MOVE: ";
            Throwable cause = DicomScuNode.getBaseCause(t);
            info += cause.getMessage();
            log.warn(info, t);

            throw new RuntimeException(info, t);
        }
    }
}

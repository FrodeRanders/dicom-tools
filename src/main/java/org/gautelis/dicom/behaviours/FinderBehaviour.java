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
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.gautelis.dicom.net.DicomScuNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.EnumSet;
import java.util.function.Consumer;

/*
 * Relevant abstract syntax:
 *
 * - PatientRootQueryRetrieveInformationModelFind
 * - StudyRootQueryRetrieveInformationModelFind
 *
 * Irrelevant abstract syntax:
 *
 * - PatientStudyOnlyQueryRetrieveInformationModelFindRetired
 * - ModalityWorklistInformationModelFind
 * - GeneralPurposeWorklistInformationModelFindRetired
 * - HangingProtocolInformationModelFind
 * - ColorPaletteInformationModelFind
 * - GenericImplantTemplateInformationModelFind
 * - ImplantAssemblyTemplateInformationModelFind
 * - ImplantTemplateGroupInformationModelFind
 */
public class FinderBehaviour {
    private static final Logger log = LoggerFactory.getLogger(FinderBehaviour.class);

    private final DicomScuNode node;

    public FinderBehaviour(DicomScuNode node) {
        this.node = node;

        //
        TransferCapability tc = new TransferCapability(
                null, UID.StudyRootQueryRetrieveInformationModelFind,
                TransferCapability.Role.SCU,
                DicomScuNode.TRANSFER_SYNTAX_CHAIN
        );

        if (false && null == tc.getQueryOptions()) {

            EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
            if (!queryOptions.isEmpty()) {
                //queryOptions.add(QueryOption.RELATIONAL);
                queryOptions.add(QueryOption.DATETIME);

                node.withAAssociateRQ(rq -> rq.addExtendedNegotiation(
                        new ExtendedNegotiation(
                                UID.StudyRootQueryRetrieveInformationModelFind,
                                QueryOption.toExtendedNegotiationInformation(queryOptions)
                        )
                    )
                );
            }
        }
        node.withApplicationEntity(ae -> ae.addTransferCapability(tc));

        node.withAAssociateRQ(rq -> {
            rq.addPresentationContext(
                    new PresentationContext(
                            rq.getNumberOfPresentationContexts() * 2 + 1,
                        /* abstract syntax */ UID.StudyRootQueryRetrieveInformationModelFind,
                        /* transfer syntax */ DicomScuNode.TRANSFER_SYNTAX_CHAIN
                    )
            );

            rq.addRoleSelection(
                    new RoleSelection(
                        /* abstract syntax */ UID.StudyRootQueryRetrieveInformationModelFind,
                        /* is SCU? */ true,
                        /* is SCP? */ false
                    )
            );
        });
    }

    public void find(
            final Consumer<Attributes> preparationBlock,
            final Consumer<Attributes> resultHandlerBlock
    ) {
        try {
            // Prepare search keys
            Attributes keys = new Attributes();
            preparationBlock.accept(keys);

            try {
                // Search
                node.open();
                node.query(UID.StudyRootQueryRetrieveInformationModelFind, keys, resultHandlerBlock);

            } finally {
                node.close();
            }
        } catch (ConnectException ce) {
            String info = "Failed to connect to PACS: ";
            Throwable cause = DicomScuNode.getBaseCause(ce);
            info += cause.getMessage();
            log.warn(info);

            throw new RuntimeException(info, ce);

        } catch (Throwable t) {
            String info = "Failed to C-FIND: ";
            Throwable cause = DicomScuNode.getBaseCause(t);
            info += cause.getMessage();
            log.warn(info, t);

            throw new RuntimeException(info, t);
        }
    }
}

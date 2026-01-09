/*
 * Copyright (C) 2026 Frode Randers
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.gautelis.dicom.behaviours.RetrieverBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RetrieverMoveDestinationMismatchTest {

    @Test
    public void testMoveDestinationUnknown() throws Exception {
        int movePort = 5137;

        Properties moveProps = new Properties();
        moveProps.setProperty("local-scp-application-entity", "MOVE_SCP");
        moveProps.setProperty("local-scp-host", "localhost");
        moveProps.setProperty("local-scp-port", Integer.toString(movePort));
        moveProps.setProperty("accepted-calling-aets", "MOVE_SCU");

        Properties moveScuProps = new Properties();
        moveScuProps.setProperty("local-scu-application-entity", "MOVE_SCU");
        moveScuProps.setProperty("local-scp-host", "localhost");
        moveScuProps.setProperty("remote-application-entity", "MOVE_SCP");
        moveScuProps.setProperty("remote-host", "localhost");
        moveScuProps.setProperty("remote-port", Integer.toString(movePort));

        Configuration moveConfig = ConfigurationTool.bindProperties(Configuration.class, moveProps);
        Configuration moveScuConfig = ConfigurationTool.bindProperties(Configuration.class, moveScuProps);

        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicInteger responseStatus = new AtomicInteger(Integer.MIN_VALUE);
        DicomScpNode moveScp = new DicomScpNode(moveConfig);
        moveScp.withApplicationEntity(ae -> ae.addTransferCapability(
                new TransferCapability(
                        null,
                        UID.StudyRootQueryRetrieveInformationModelMove,
                        TransferCapability.Role.SCP,
                        DicomScpNode.TRANSFER_SYNTAX_CHAIN
                )
        ));
        moveScp.withApplicationEntity(ae -> ae.setAcceptedCallingAETitles("MOVE_SCU"));
        moveScp.withServiceRegistry(registry -> registry.addDicomService(
                new DestinationCheckingMoveSCP("EXPECTED_SCP", responseStatus, responseLatch)
        ));

        DicomScuNode moveScu = new DicomScuNode(moveScuConfig);
        RetrieverBehaviour retriever = new RetrieverBehaviour(moveScu);

        try {
            retriever.retrieve(keys -> {
                keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
                keys.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3");
            }, "WRONG_SCP");
            assertTrue("Timed out waiting for C-MOVE response", responseLatch.await(5, TimeUnit.SECONDS));
        } finally {
            moveScp.close();
            moveScu.close();
        }

        assertEquals(Status.MoveDestinationUnknown, responseStatus.get());
    }

    private static class DestinationCheckingMoveSCP extends BasicCMoveSCP {
        private final String expectedDestination;
        private final AtomicInteger responseStatus;
        private final CountDownLatch responseLatch;

        DestinationCheckingMoveSCP(
                String expectedDestination,
                AtomicInteger responseStatus,
                CountDownLatch responseLatch
        ) {
            super(UID.StudyRootQueryRetrieveInformationModelMove);
            this.expectedDestination = expectedDestination;
            this.responseStatus = responseStatus;
            this.responseLatch = responseLatch;
        }

        @Override
        protected RetrieveTask calculateMatches(
                Association as,
                PresentationContext pc,
                Attributes rq,
                Attributes keys
        ) throws DicomServiceException {
            return new DestinationCheckingTask(as, pc, rq, expectedDestination, responseStatus, responseLatch);
        }
    }

    private static class DestinationCheckingTask implements RetrieveTask {
        private final Association association;
        private final PresentationContext pc;
        private final Attributes rq;
        private final String expectedDestination;
        private final AtomicInteger responseStatus;
        private final CountDownLatch responseLatch;
        private volatile boolean canceled;

        DestinationCheckingTask(
                Association association,
                PresentationContext pc,
                Attributes rq,
                String expectedDestination,
                AtomicInteger responseStatus,
                CountDownLatch responseLatch
        ) {
            this.association = association;
            this.pc = pc;
            this.rq = rq;
            this.expectedDestination = expectedDestination;
            this.responseStatus = responseStatus;
            this.responseLatch = responseLatch;
        }

        @Override
        public void onCancelRQ(Association as) {
            canceled = true;
        }

        @Override
        public void run() {
            int status;
            if (canceled) {
                status = Status.Cancel;
            } else {
                String moveDestination = rq.getString(Tag.MoveDestination);
                status = expectedDestination.equals(moveDestination)
                        ? Status.Success
                        : Status.MoveDestinationUnknown;
            }
            Attributes rsp = Commands.mkCMoveRSP(rq, status);
            rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, 0);
            rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, 0);
            rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
            association.tryWriteDimseRSP(pc, rsp);
            responseStatus.set(status);
            responseLatch.countDown();
        }
    }
}

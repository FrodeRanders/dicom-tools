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
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.util.UIDUtils;
import org.gautelis.dicom.behaviours.RetrieverBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RetrieverRoundTripTest {

    @Test
    public void testMoveTriggersStore() throws Exception {
        int storePort = 5135;
        int movePort = 5136;

        Properties storeProps = new Properties();
        storeProps.setProperty("local-scp-application-entity", "STORE_SCP");
        storeProps.setProperty("local-scp-host", "localhost");
        storeProps.setProperty("local-scp-port", Integer.toString(storePort));
        storeProps.setProperty("accepted-calling-aets", "MOVE_SCU");

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

        Configuration storeConfig = ConfigurationTool.bindProperties(Configuration.class, storeProps);
        Configuration moveConfig = ConfigurationTool.bindProperties(Configuration.class, moveProps);
        Configuration moveScuConfig = ConfigurationTool.bindProperties(Configuration.class, moveScuProps);

        CountDownLatch storeLatch = new CountDownLatch(1);
        List<Attributes> stored = Collections.synchronizedList(new ArrayList<>());
        DicomScpNode storeScp = new DicomScpNode(storeConfig);
        storeScp.withApplicationEntity(ae -> ae.addTransferCapability(
                new TransferCapability(
                        null,
                        UID.SecondaryCaptureImageStorage,
                        TransferCapability.Role.SCP,
                        DicomScpNode.TRANSFER_SYNTAX_CHAIN
                )
        ));
        storeScp.withApplicationEntity(ae -> ae.setAcceptedCallingAETitles("MOVE_SCU"));
        storeScp.withServiceRegistry(registry -> registry.addDicomService(new CapturingCStoreSCP(stored, storeLatch)));

        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.PatientID, VR.LO, "PATIENT-STORE");

        AtomicReference<Throwable> moveError = new AtomicReference<>();
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
                new InMemoryCMoveSCP(
                        dataset,
                        "MOVE_SCU",
                        "STORE_SCP",
                        "localhost",
                        storePort,
                        moveError
                )
        ));

        DicomScuNode moveScu = new DicomScuNode(moveScuConfig);
        RetrieverBehaviour retriever = new RetrieverBehaviour(moveScu);

        Optional<Integer> status;
        try {
            status = retriever.retrieve(keys -> {
                keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
                keys.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3");
            }, "STORE_SCP");
            assertTrue("Timed out waiting for C-STORE: " + describe(moveError.get()),
                    storeLatch.await(5, TimeUnit.SECONDS));
        } finally {
            moveScp.close();
            storeScp.close();
            moveScu.close();
        }
        assertEquals(1, stored.size());
        if (status.isPresent()) {
            assertEquals(Status.Success, status.get().intValue());
        }
        assertEquals(dataset.getString(Tag.SOPInstanceUID), stored.get(0).getString(Tag.SOPInstanceUID));
    }

    private static class CapturingCStoreSCP extends BasicCStoreSCP {
        private final List<Attributes> stored;
        private final CountDownLatch storeLatch;

        CapturingCStoreSCP(List<Attributes> stored, CountDownLatch storeLatch) {
            super(UID.SecondaryCaptureImageStorage);
            this.stored = stored;
            this.storeLatch = storeLatch;
        }

        @Override
        protected void store(
                Association as,
                PresentationContext pc,
                Attributes rq,
                PDVInputStream data,
                Attributes rsp
        ) throws IOException {
            try (DicomInputStream in = new DicomInputStream(data)) {
                Attributes dataset = in.readDataset(-1, -1);
                stored.add(dataset);
                storeLatch.countDown();
            }
        }
    }

    private static class InMemoryCMoveSCP extends BasicCMoveSCP {
        private final Attributes dataset;
        private final String callingAet;
        private final String destinationAet;
        private final String destinationHost;
        private final int destinationPort;
        private final AtomicReference<Throwable> moveError;

        InMemoryCMoveSCP(
                Attributes dataset,
                String callingAet,
                String destinationAet,
                String destinationHost,
                int destinationPort,
                AtomicReference<Throwable> moveError
        ) {
            super(UID.StudyRootQueryRetrieveInformationModelMove);
            this.dataset = dataset;
            this.callingAet = callingAet;
            this.destinationAet = destinationAet;
            this.destinationHost = destinationHost;
            this.destinationPort = destinationPort;
            this.moveError = moveError;
        }

        @Override
        protected RetrieveTask calculateMatches(
                Association as,
                PresentationContext pc,
                Attributes rq,
                Attributes keys
        ) throws DicomServiceException {
            return new CMoveRetrieveTask(as, pc, rq);
        }

        private class CMoveRetrieveTask implements RetrieveTask {
            private final Association association;
            private final PresentationContext pc;
            private final Attributes rq;
            private volatile boolean canceled;

            CMoveRetrieveTask(Association association, PresentationContext pc, Attributes rq) {
                this.association = association;
                this.pc = pc;
                this.rq = rq;
            }

            @Override
            public void onCancelRQ(Association as) {
                canceled = true;
            }

            @Override
            public void run() {
                if (canceled) {
                    writeResponse(Status.Cancel, 0, 1);
                    return;
                }

                Association storeAssociation = null;
                ExecutorService executor = Executors.newCachedThreadPool();
                ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();
                try {
                    String moveDestination = rq.getString(Tag.MoveDestination);
                    if (!destinationAet.equals(moveDestination)) {
                        writeResponse(Status.MoveDestinationUnknown, 0, 1);
                        return;
                    }

                    Connection local = new Connection();
                    local.setHostname("localhost");
                    local.setPort(Connection.NOT_LISTENING);

                    Connection remote = new Connection();
                    remote.setHostname(destinationHost);
                    remote.setPort(destinationPort);

                    Device device = new Device("MOVE-SCU");
                    ApplicationEntity ae = new ApplicationEntity(callingAet);
                    ae.setAETitle(callingAet);
                    ae.addConnection(local);
                    ae.setAssociationInitiator(true);
                    ae.setAssociationAcceptor(false);
                    ae.addTransferCapability(new TransferCapability(
                            null,
                            UID.SecondaryCaptureImageStorage,
                            TransferCapability.Role.SCU,
                            DicomScpNode.TRANSFER_SYNTAX_CHAIN
                    ));
                    device.addConnection(local);
                    device.addApplicationEntity(ae);
                    device.setExecutor(executor);
                    device.setScheduledExecutor(scheduled);

                    AAssociateRQ rqStore = new AAssociateRQ();
                    rqStore.setCallingAET(callingAet);
                    rqStore.setCalledAET(destinationAet);
                    rqStore.addPresentationContext(new PresentationContext(
                            1,
                            UID.SecondaryCaptureImageStorage,
                            DicomScpNode.TRANSFER_SYNTAX_CHAIN
                    ));

                    storeAssociation = ae.connect(local, remote, rqStore);
                    DataWriter writer = DataWriterAdapter.forAttributes(dataset);
                    DimseRSP rsp = storeAssociation.cstore(
                            dataset.getString(Tag.SOPClassUID),
                            dataset.getString(Tag.SOPInstanceUID),
                            0x0002,
                            writer,
                            DicomScpNode.TRANSFER_SYNTAX_CHAIN[0]
                    );
                    rsp.next();

                    writeResponse(Status.Success, 1, 0);
                } catch (Exception e) {
                    moveError.compareAndSet(null, e);
                    writeResponse(Status.ProcessingFailure, 0, 1);
                } finally {
                    if (storeAssociation != null) {
                        try {
                            storeAssociation.release();
                        } catch (IOException e) {
                            // Ignore cleanup errors in test.
                        }
                    }
                    executor.shutdown();
                    scheduled.shutdown();
                }
            }

            private void writeResponse(int status, int completed, int failed) {
                Attributes rsp = Commands.mkCMoveRSP(rq, status);
                rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
                rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
                rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
                association.tryWriteDimseRSP(pc, rsp);
            }
        }
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "no error captured";
        }
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }
}

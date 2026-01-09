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
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.UIDUtils;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.vopn.lang.ConfigurationTool;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;

public class CStoreFailureTest {

    @Test
    public void testStoreFailureStatus() throws Exception {
        int storePort = 5138;

        Properties scpProps = new Properties();
        scpProps.setProperty("local-scp-application-entity", "STORE_SCP");
        scpProps.setProperty("local-scp-host", "localhost");
        scpProps.setProperty("local-scp-port", Integer.toString(storePort));
        scpProps.setProperty("accepted-calling-aets", "STORE_SCU");

        Properties scuProps = new Properties();
        scuProps.setProperty("local-scu-application-entity", "STORE_SCU");
        scuProps.setProperty("remote-application-entity", "STORE_SCP");
        scuProps.setProperty("remote-host", "localhost");
        scuProps.setProperty("remote-port", Integer.toString(storePort));

        Configuration scpConfig = ConfigurationTool.bindProperties(Configuration.class, scpProps);
        Configuration scuConfig = ConfigurationTool.bindProperties(Configuration.class, scuProps);

        DicomScpNode scpNode = new DicomScpNode(scpConfig);
        scpNode.withApplicationEntity(ae -> ae.addTransferCapability(
                new TransferCapability(
                        null,
                        UID.SecondaryCaptureImageStorage,
                        TransferCapability.Role.SCP,
                        DicomScpNode.TRANSFER_SYNTAX_CHAIN
                )
        ));
        scpNode.withApplicationEntity(ae -> ae.setAcceptedCallingAETitles("STORE_SCU"));
        scpNode.withServiceRegistry(registry -> registry.addDicomService(new FailingCStoreSCP()));

        ExecutorService executor = Executors.newCachedThreadPool();
        ScheduledExecutorService scheduled = Executors.newSingleThreadScheduledExecutor();

        Connection local = new Connection();
        local.setHostname("localhost");
        local.setPort(Connection.NOT_LISTENING);

        Connection remote = new Connection();
        remote.setHostname(scuConfig.remoteHost());
        remote.setPort(scuConfig.remotePort());

        Device device = new Device("STORE-SCU");
        ApplicationEntity ae = new ApplicationEntity(scuConfig.localScuApplicationEntity());
        ae.setAETitle(scuConfig.localScuApplicationEntity());
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

        org.dcm4che3.net.pdu.AAssociateRQ associateRQ = new org.dcm4che3.net.pdu.AAssociateRQ();
        associateRQ.setCallingAET(scuConfig.localScuApplicationEntity());
        associateRQ.setCalledAET(scuConfig.remoteApplicationEntity());
        associateRQ.addPresentationContext(new PresentationContext(
                1,
                UID.SecondaryCaptureImageStorage,
                DicomScpNode.TRANSFER_SYNTAX_CHAIN
        ));

        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.PatientID, VR.LO, "PATIENT-FAIL");

        int status;
        Association association = null;
        try {
            association = ae.connect(local, remote, associateRQ);
            DataWriter writer = DataWriterAdapter.forAttributes(dataset);
            DimseRSP rsp = association.cstore(
                    dataset.getString(Tag.SOPClassUID),
                    dataset.getString(Tag.SOPInstanceUID),
                    0x0002,
                    writer,
                    DicomScpNode.TRANSFER_SYNTAX_CHAIN[0]
            );
            rsp.next();
            status = rsp.getCommand().getInt(Tag.Status, -1);
        } finally {
            if (association != null && association.isReadyForDataTransfer()) {
                association.release();
            }
            scpNode.close();
            executor.shutdown();
            scheduled.shutdown();
        }

        assertEquals(Status.ProcessingFailure, status);
    }

    private static class FailingCStoreSCP extends BasicCStoreSCP {
        FailingCStoreSCP() {
            super(UID.SecondaryCaptureImageStorage);
        }

        @Override
        protected void store(
                Association as,
                PresentationContext pc,
                Attributes rq,
                org.dcm4che3.net.PDVInputStream data,
                Attributes rsp
        ) throws java.io.IOException {
            throw new DicomServiceException(Status.ProcessingFailure);
        }
    }
}

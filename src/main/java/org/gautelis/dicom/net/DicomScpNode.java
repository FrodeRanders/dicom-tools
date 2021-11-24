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
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.gautelis.dicom.Configuration;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

/*
 * Service class provider (SCP) node
 */
public class DicomScpNode extends DicomNode {

    private final DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

    public DicomScpNode(Configuration dicomConfig) {
        // Client side representation of the connection
        Connection local = new Connection();
        local.setHostname(dicomConfig.localScpHost());
        local.setPort(dicomConfig.localScpPort());

        // Calling application entity, which in this context actually is the
        // receiver application entity (being an SCP and all)
        ae = new ApplicationEntity(dicomConfig.localScpApplicationEntity().toUpperCase());
        ae.setAETitle(dicomConfig.localScpApplicationEntity());
        ae.addConnection(local);
        ae.setAssociationInitiator(false);
        ae.setAssociationAcceptor(true);

        // Device
        device = new Device(dicomConfig.localScpApplicationEntity());
        device.addConnection(local);
        device.addApplicationEntity(ae);
        device.setDimseRQHandler(serviceRegistry);

        //
        executorService = Executors.newCachedThreadPool();
        device.setExecutor(executorService);

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        device.setScheduledExecutor(scheduledExecutorService);
    }

    public void withServiceRegistry(Consumer<DicomServiceRegistry> block) {
        block.accept(serviceRegistry);
    }

    public void shutdown() {
        scheduledExecutorService.shutdown();
        executorService.shutdown();
    }
}

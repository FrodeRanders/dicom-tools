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
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/*
 * An association
 */
public class DicomAssociation implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DicomAssociation.class);

    private final String id;
    private Association association;
    private final DicomNode node;

    public DicomAssociation(String id, Association association, DicomNode node) {
        this.id = id;
        this.association = association;
        this.node = node;
    }

    /* package private */
    String getId() {
        return id;
    }

    public void close() throws Exception {
        if (association != null && association.isReadyForDataTransfer()) {
            try {
                association.waitForOutstandingRSP();
                association.release();
                node.release(this);

            } catch (InterruptedException ie) {
                String info = "Outstanding RSP problem: " + ie.getMessage();
                log.warn(info);
                throw new Exception(info, ie);

            } catch (IOException ioe) {
                String info = "Could not disconnect: " + ioe.getMessage();
                log.warn(info);
                throw new Exception(info, ioe);
            }

            association = null;
        }
    }

    public boolean echo() {
        if (null == association) {
            String info = "No association: echo -- you have to open an association first";
            log.error(info);
            throw new RuntimeException(info);
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
            String info = "No association: query -- you have to open an association first";
            log.error(info);
            throw new RuntimeException(info);
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
            String info = "No association: query -- you have to open an association first";
            log.error(info);
            throw new RuntimeException(info);
        }
        else {
            query(CUID, keys, new DimseRSPHandler(association.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd,
                                       Attributes data) {

                    int status = cmd.getInt(Tag.Status, -1);
                    if (Status.Success != status && Status.Pending != status) {
                        log.trace("C-FIND: Received DimseRSP: Command status: {} ({})",
                                String.format("%04X", status & 0xFFFF), DicomNode.statusToString(status)
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
            String info = "No association: retrieve -- you have to open an association first";
            log.error(info);
            throw new RuntimeException(info);
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
            String info = "No association: retrieve -- you have to open an association first";
            log.error(info);
            throw new RuntimeException(info);
        }
        else {
            retrieve(CUID, keys, destinationAET, new DimseRSPHandler(association.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {

                    int _status = cmd.getInt(Tag.Status, -1);
                    if (Status.Success != _status) {
                        log.trace("C-MOVE: Received DimseRSP: Command status: {} ({})",
                                String.format("%04X", _status & 0xFFFF), DicomNode.statusToString(_status)
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

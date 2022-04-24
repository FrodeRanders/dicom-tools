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

import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.gautelis.dicom.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/*

 */
public abstract class DicomNode implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DicomScuNode.class);

    public static final String[] TRANSFER_SYNTAX_CHAIN = {
            UID.ExplicitVRLittleEndian,
            UID.ImplicitVRLittleEndian
    };

    protected final Connection local = new Connection();
    protected final Connection remote = new Connection();
    protected final AAssociateRQ rq = new AAssociateRQ();

    protected Device device;
    protected ApplicationEntity ae;

    protected final Map<String, DicomAssociation> associations = new HashMap<>();
    protected final ExecutorService executorService = Executors.newCachedThreadPool();
    protected final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public void withDevice(Consumer<Device> block) {
        block.accept(device);
    }

    public void withAAssociateRQ(Consumer<AAssociateRQ> block) {
        block.accept(rq);
    }

    public void withApplicationEntity(Consumer<ApplicationEntity> block) {
        block.accept(ae);
    }

    public void close() {
        try {
            for (DicomAssociation association : associations.values()) {
                association.close();
            }
        } catch (Exception e) {
            String info = "Could not successfully shutdown associations";
            log.warn(info, e);
        }

        scheduledExecutorService.shutdown();
        executorService.shutdown();
    }

    public static String statusToString(int status) {
        switch (status) {
            case Status.Success:
                return "Success";

            case Status.Pending:
                return "Pending";

            case Status.PendingWarning:
                return "Pending warning";

            case Status.Cancel:
                return "Cancel";

            case Status.NoSuchAttribute:
                return "No such attribute";

            case Status.InvalidAttributeValue:
                return "Invalid attribute value";

            case Status.AttributeListError:
                return "Attribute list error";

            case Status.ProcessingFailure:
                return "Processing failure";

            case Status.DuplicateSOPinstance:
                return "Duplicate SOP instance";

            case Status.NoSuchObjectInstance:
                return "No such object instance (no such SOP instance)";

            case Status.NoSuchEventType:
                return "No such event type";

            case Status.NoSuchArgument:
                return "No such argument";

            case Status.InvalidArgumentValue:
                return "Invalid argument value";

            case Status.AttributeValueOutOfRange:
                return "Attribute value out of range";

            case Status.InvalidObjectInstance:
                return "Invalid object instance (invalid SOP instance)";

            case Status.NoSuchSOPclass:
                return "No such SOP class";

            case Status.ClassInstanceConflict:
                return "Class instance conflict (the specified SOP instance is not a member of the specified SOP class)";

            case Status.MissingAttribute:
                return "Missing attribute";

            case Status.MissingAttributeValue:
                return "Missing attribute value";

            case Status.SOPclassNotSupported:
                return "SOP class not supported";

            case Status.NoSuchActionType:
                return "No such action type";

            case Status.NotAuthorized:
                return "Not authorized (the DIMSE-service-user was not authorized to invoke the operation)";

            case Status.DuplicateInvocation:
                return "Duplicate invocation";

            case Status.UnrecognizedOperation:
                return "Unrecognized operation";

            case Status.MistypedArgument:
                return "Mistyped argument (one of the parameters supplied has not been agreed for use on the association between the DIMSE-service-users)";

            case Status.ResourceLimitation:
                return "Resource limitation";

            case Status.OutOfResources:
                return "Out of resources";

            case Status.UnableToCalculateNumberOfMatches:
                return "Unable to calculate number of matches";

            case Status.UnableToPerformSubOperations:
                return "Unable to perform sub operations";

            case Status.MoveDestinationUnknown:
                return "Move destination unknown";

            case Status.IdentifierDoesNotMatchSOPClass:
            //case Status.DataSetDoesNotMatchSOPClassError:
                return "Identifier or dataset does not match SOP class (error)";

            case Status.OneOrMoreFailures:
            //case Status.CoercionOfDataElements:
                return "One or more failures -or- coercion of data elements";

            case Status.ElementsDiscarded:
                return "Elements discarded";

            case Status.DataSetDoesNotMatchSOPClassWarning:
                return "Dataset does not match SOP class (warning)";

            case Status.UnableToProcess:
            //case Status.CannotUnderstand:
                return "Unable to process -or- cannot understand";

            case Status.UPSCreatedWithModifications:
                return "UPS Created with modifications";

            case Status.UPSDeletionLockNotGranted:
                return "UPS Deletion lock not granted";

            case Status.UPSAlreadyInRequestedStateOfCanceled:
                return "UPS Already in requested state of canceled";

            case Status.UPSCoercedInvalidValuesToValidValues:
                return "UPS Coerced invalid values to valid values";

            case Status.UPSAlreadyInRequestedStateOfCompleted:
                return "UPS Already in requested state of completed";

            case Status.UPSMayNoLongerBeUpdated:
                return "UPS May no longer be updated";

            case Status.UPSTransactionUIDNotCorrect:
                return "UPS Transaction UID not correct";

            case Status.UPSAlreadyInProgress:
                return "UPS Already in progress";

            case Status.UPSStateMayNotChangedToScheduled:
                return "UPS State may not be changed to scheduled";

            case Status.UPSNotMetFinalStateRequirements:
                return "UPS Not met final state requirements";

            case Status.UPSDoesNotExist:
                return "UPS Does not exist";

            case Status.UPSUnknownReceivingAET:
                return "UPS Unknown receiving AET";

            case Status.UPSNotScheduled:
                return "UPS Not scheduled";

            case Status.UPSNotYetInProgress:
                return "UPS Not yet in progress";

            case Status.UPSAlreadyCompleted:
                return "UPS Already completed";

            case Status.UPSPerformerCannotBeContacted:
                return "UPS Performer cannot be contacted";

            case Status.UPSPerformerChoosesNotToCancel:
                return "UPS Performer chooses not to cancel";

            case Status.UPSActionNotAppropriate:
                return "UPS Action not appropriate";

            case Status.UPSDoesNotSupportEventReports:
                return "UPS Does not support event reports";

            default:
                return "Unknown: " + status;
        }
    }

    public DicomAssociation open()
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {

        String id = UUID.randomUUID().toString();
        DicomAssociation association = new DicomAssociation(id, ae.connect(local, remote, rq), this);
        associations.put(id, association);
        return association;
    }

    /* package private */
    void release(DicomAssociation association) {
        associations.remove(association.getId());
    }

    public static Throwable getBaseCause(Throwable t) {
        Throwable cause = null;
        Throwable c = t.getCause();
        if (null != c) {
            do {
                cause = c;
                c = c.getCause();
            } while (null != c);
        }

        if (null != cause) {
            t = cause;
        }

        return t;
    }
}

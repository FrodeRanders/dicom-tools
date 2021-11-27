# dicom-tools
Tools and utilities that facilitate the use of dcm4che3
==================================================================

## Setting up a SCU/SCP-pair and ping! beetween them
```java
import org.gautelis.dicom.behaviours.VerificationBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;

import java.util.Properties;

public class Controller {
//
private final Configuration dicomConfig;

    private final DicomScuNode scuNode;
    private final DicomScpNode scpNode;

    private final VerificationBehaviour verifier;

    public Controller(
            final Properties dicomProperties
    ) {
        dicomConfig = ConfigurationTool.bindProperties(Configuration.class, dicomProperties);

        // The 'verifier' acts as both a SCU handled by DicomScuNode and
        // as a SCP handled by DicomScpNod, so we need both.
        scuNode = new DicomScuNode(dicomConfig);
        scpNode = new DicomScpNode(dicomConfig);

        // The verifier handles PINGs, which we may both issue and receive in capacities as
        // both client (SCU) and server (SCP).
        verifier = new VerificationBehaviour(scuNode, scpNode);
    }

    public void shutdown() {
        scpNode.shutdown();
        scuNode.shutdown();
    }

    public boolean ping() {
        return verifier.verify();
    }
}
```
Which could be used as this
```java

final Properties dicomConfig = new Properties();

// SCU / client-side configuration
dicomConfig.setProperty("local-scu-application-entity", "MY_SCU");
dicomConfig.setProperty("local-scu-modality-type", "OT");

// SCP / client-side configuration. This configuration must match the
// remote SCP configuration below -- otherwise the SCU will not be able
// to connect back to the (local) SCP.
dicomConfig.setProperty("local-scp-application-entity", "MY_SCP");
dicomConfig.setProperty("local-scp-host", "localhost");
dicomConfig.setProperty("local-scp-port", "4101");

// The local SCP accepts connections from these application entities
dicomConfig.setProperty("accepted-calling-aets", "MY_SCU,REMOTE_SCP,SOME_PACS");

// Remote SCP (which is actually same as local SCP :) as seen from the SCU
dicomConfig.setProperty("remote-application-entity", "MY_SCP");
dicomConfig.setProperty("remote-host", "localhost");
dicomConfig.setProperty("remote-port", "4101");

Controller controller = null;
try {
    controller = new Controller(dicomConfig);
    if (controller.ping()) 
        System.out.println("PING!");
}
catch (Throwable t) {
    System.err.println("Could not ping");
}
finally {
    if (null != controller)
        controller.shutdown();
}

```

## Setting up a SCU and querying a remote SCP
```java
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.gautelis.dicom.Configuration;
import org.gautelis.dicom.behaviours.FinderBehaviour;
import org.gautelis.dicom.model.DicomElement;
import org.gautelis.dicom.net.DicomScuNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    //
    private final Configuration dicomConfig;
    private final Locale locale;

    private final DicomScuNode scuNode;
    private final FinderBehaviour finder;

    public Controller(
            final Configuration dicomConfig,
            Locale locale
    ) {
        //
        this.dicomConfig = dicomConfig;
        this.locale = locale;

        //
        scuNode = new DicomScuNode(dicomConfig);

        // The 'finder' acts as SCUs, which is handled by DicomScuNode.
        finder = new FinderBehaviour(scuNode);
    }

    public void shutdown() {
        scuNode.shutdown();
    }

    /**
     * Given accession number, retrieve patient information _and_ the study
     * instance UID from PACS.
     */
    public void findStudies(
            final String accessionNumber,
            final String modality,
            final Date startDate, final Date endDate,
            final Consumer<String> studyConsumer
    ) {
        finder.find(
            /*----------------------------------
             * Setup keys for searching
             *---------------------------------*/
            (keys) -> {
                // Indicate character set
                {
                    int tag = Tag.SpecificCharacterSet;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "ISO_IR 100");
                }

                // Study level query
                {
                    int tag = Tag.QueryRetrieveLevel;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "STUDY");
                }

                // Accession number
                {
                    int tag = Tag.AccessionNumber;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, accessionNumber);
                }

                // Study date range
                {
                    int tag = Tag.StudyDate;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    String rangeString = "";
                    if (null != startDate) {
                        String _startDate = org.gautelis.vopn.lang.Date.date2String(startDate, locale).replace("-", "");
                        rangeString += _startDate;

                    }
                    if (null != startDate || null != endDate) {
                        rangeString += "-";
                    }
                    if (null != endDate) {
                        String _endDate = org.gautelis.vopn.lang.Date.date2String(endDate, locale).replace("-", "");
                        rangeString += _endDate;
                    }
                    if (rangeString.length() > 0) {
                        keys.setString(tag, vr, rangeString);
                    } else {
                        keys.setNull(tag, vr);
                    }
                }

                // Filter on modality in study
                {
                    int tag = Tag.ModalitiesInStudy; // optionally supported
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    } else {
                        keys.setNull(tag, vr);
                    }
                }

               // We are interested in study instance UID
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }

                // For logging purposes
                {
                    int tag = Tag.NumberOfStudyRelatedSeries;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }

                {
                    int tag = Tag.NumberOfStudyRelatedInstances;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }

                {
                    int tag = Tag.StudyDescription;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }
            },

            /*----------------------------------
             * Setup handling of search results
             *---------------------------------*/
            (attributes) -> {

                if (attributes.isEmpty())
                    return;

                if (log.isTraceEnabled()) {
                    DicomElement element = new DicomElement("study", attributes);
                    String text = element.asText(/* recurse? */ true);

                    log.trace("=========================================================================");
                    log.trace(text);
                    log.trace("=========================================================================");
                }

                //
                String studyInstanceUID = attributes.getString(Tag.StudyInstanceUID);
                studyConsumer.accept(studyInstanceUID);
            }
        );
    }


    /**
     * Given study instance UID (and the modality we are interested in),
     * retrieve the relevant series associated with that study from PACS.
     */
    public void findSeries(
            final String studyInstanceUID,
            final String modality,
            final BiConsumer<String, String> seriesConsumer
    ) {
        finder.find(
            /*----------------------------------
             * Setup keys for searching
             *---------------------------------*/
            (keys) -> {
                // Indicate character set
                {
                    int tag = Tag.SpecificCharacterSet;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "ISO_IR 100");
                }

                // Series level query
                {
                    int tag = Tag.QueryRetrieveLevel;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, "SERIES");
                }

                // Study instance UID = ...
                {
                    int tag = Tag.StudyInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setString(tag, vr, studyInstanceUID);
                }

                // Filter on modality
                {
                    int tag = Tag.Modality;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && modality.length() > 0) {
                        keys.setString(tag, vr, modality);
                    } else {
                        keys.setNull(tag, vr);
                    }
                }

                // Series instance UID?
                {
                    int tag = Tag.SeriesInstanceUID;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }

                // For logging purposes
                {
                    int tag = Tag.SeriesDescription;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }

                {
                    int tag = Tag.NumberOfSeriesRelatedInstances;
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    keys.setNull(tag, vr);
                }
            },

            /*----------------------------------
             * Setup handling of search results
             *---------------------------------*/
            (attributes) -> {

                if (attributes.isEmpty())
                    return;

                if (log.isTraceEnabled()) {
                    DicomElement element = new DicomElement("series", attributes);
                    String text = element.asText(/* recurse? */ true);

                    log.info("=========================================================================");
                    log.info(text);
                    log.info("=========================================================================");
                }

                String seriesInstanceUID = attributes.getString(Tag.SeriesInstanceUID);
                String availability = attributes.getString(Tag.InstanceAvailability);

                seriesConsumer.accept(seriesInstanceUID, availability);
            }
        );
    }
}
```
In this example we start with an accession number, looking up the study instance UID
and then looking up the series instance UID:
```
// pseudo code
accessionNumber <- "42"
studyInstanceUID <- findStudies(accessionNumber, ...)
seriesInstanceUID <- findSeries(studyInstanceUID, ...)
```
like so:
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gautelis.dicom.Configuration;
import org.gautelis.vopn.lang.ConfigurationTool;

import java.util.Date;
import java.util.Locale;
import java.util.Properties;

public class Application {
    private static final Logger log = LogManager.getLogger(Application.class);

    private static final String ACCESSION_NUMBER_FORMAT = "%010d";

    public static void main(String[] args) {

        final Controller controller[] = { null };
        try {
            controller[0] = new Controller(getConfig(), Locale.US);

            // Start with accession number
            final String accessionNumber = padNumber(ACCESSION_NUMBER_FORMAT, "42");
            
            final String modality = "OT";
            Date startDate = null;
            Date endDate = null;

            controller[0].findStudies(accessionNumber, modality, startDate, endDate, studyInstanceUID -> {
                controller[0].findSeries(studyInstanceUID, modality, (seriesInstanceUID, availability) -> {
                    //////////////////////////////////////////////////////////////////////////////////////
                    // May run multiple times!
                    System.out.printf("Accession number: %s  modality: %s%n", accessionNumber, modality);
                    System.out.printf("  StudyInstanceUID: %s%n", studyInstanceUID);
                    System.out.printf("  SeriesInstanceUID: %s  availability: %s%n", seriesInstanceUID, availability);
                    //////////////////////////////////////////////////////////////////////////////////////
                });
            });
        }
        catch (Throwable t) {
            System.err.printf("Could not operate: %s%n", t.getMessage());
        }
        finally {
            if (null != controller[0])
                controller[0].shutdown();
        }
    }

    private static String padNumber(String format, String number) {
        try {
            return String.format(format, Long.parseLong(number));

        } catch (NumberFormatException nfe) {
            String info = String.format("Invalid accession number: %s", number);
            throw new RuntimeException(info, nfe);
        }
    }

    private static Configuration getConfig() {
        final Properties props = new Properties();

        // SCU / client-side configuration
        props.setProperty("local-scu-application-entity", "MY_SCU");
        props.setProperty("local-scu-modality-type", "OT");

        // SCP / client-side configuration. This configuration must match the
        // remote SCP configuration below -- otherwise the SCU will not be able
        // to connect back to the (local) SCP.
        props.setProperty("local-scp-application-entity", "MY_SCP");
        props.setProperty("local-scp-host", "localhost");
        props.setProperty("local-scp-port", "4101");

        props.setProperty("storage-directory", "./STORAGE");

        // The local SCP accepts connections from these application entities
        props.setProperty("accepted-calling-aets", "MY_SCU,REMOTE_SCP,SOME_PACS");

        // Remote SCP (which is actually same as local SCP :) as seen from the SCU
        props.setProperty("remote-application-entity", "MY_SCP");
        props.setProperty("remote-host", "localhost");
        props.setProperty("remote-port", "4101");

        return ConfigurationTool.bindProperties(Configuration.class, props);
    }
}
```
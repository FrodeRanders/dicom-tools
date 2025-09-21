# Tools and utilities that facilitate the use of dcm4che 5

This software was used to pull structured reports from manual mammography readings from a 
GE PACS for analysis of inter reader agreement and for statistical analysis of breast 
density over a population. As such, it was the largest analysis ever made on this type of 
data, based on mammography screening of 14.000 women (readings for a year). The statistics
was used by General Electrics for baselining and tuning an AI for assisted screening of 
mammography X-rays.

This software adds some structure on top of dcm4che 5 and introduces the notion of 'behaviours'. 
The result is a relatively easy setup that lets you focus on the interesting stuff right away,
without tedious setup. 

![Image](doc/behaviours-annotated.png?raw=true)

Particular to GE PACS, we had to issue a MOVE in order to access the structured reports. 
The GE PACS will then issue a subsequent C-STORE back to us, which has the effect that we
have to operate as an SCP as well as an SCU. The analysis software thus has to expose SCP
capabilities for "storing" the structured reports. Of course, the software was doing 
statistical analysis on the data stream instead of actually storing it.

## Setting up a SCU/SCP-pair and ping! beetween them
```java
import org.gautelis.dicom.behaviours.VerificationBehaviour;
import org.gautelis.dicom.net.DicomScpNode;
import org.gautelis.dicom.net.DicomScuNode;
import org.gautelis.vopn.lang.ConfigurationTool;

import java.util.Properties;

public class Controller implements AutoCloseable {
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

    public void close() {
        scpNode.close();
        scuNode.close();
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

try (Controller controller = new Controller(dicomConfig)) {
    if (controller.ping()) 
        System.out.println("PING!");
}
catch (Throwable t) {
    System.err.println("Could not ping");
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

public class Controller implements AutoCloseable {
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

    public void close() {
        scuNode.close();
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
                    if (!rangeString.isEmpty()) {
                        keys.setString(tag, vr, rangeString);
                    } else {
                        keys.setNull(tag, vr);
                    }
                }

                // Filter on modality in study
                {
                    int tag = Tag.ModalitiesInStudy; // optionally supported
                    VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
                    if (null != modality && !modality.isEmpty()) {
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
                    log.trace(text);
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
                    if (null != modality && !modality.isEmpty()) {
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
                    log.info(text);
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

        try (Controller controller = new Controller(getConfig(), Locale.US)) {
            // Start with accession number
            final String accessionNumber = padNumber(ACCESSION_NUMBER_FORMAT, "42");
            
            final String modality = "OT";
            Date startDate = null;
            Date endDate = null;

            // Under the hood, we will use two concurrent Associations to retrieve data
            // in a nested fashion.
            controller.findStudies(accessionNumber, modality, startDate, endDate, studyInstanceUID -> {
                controller.findSeries(studyInstanceUID, modality, (seriesInstanceUID, availability) -> {
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
    }

    private static String padNumber(String format, String number) {
        try {
            return String.format(format, Long.parseLong(number));

        } catch (NumberFormatException nfe) {
            String info = String.format("Invalid format %s with number %s", format, number);
            throw new RuntimeException(info, nfe);
        }
    }

    private static Configuration getConfig() {
        final Properties props = new Properties();

        // Local SCU
        props.setProperty("local-scu-application-entity", "MYSKO");
        props.setProperty("local-scu-modality-type", "OT");
        
        // Remote SCP 
        props.setProperty("remote-application-entity", "PACS");
        props.setProperty("remote-host", "pacs.remote.net");
        props.setProperty("remote-port", "4100");

        return ConfigurationTool.bindProperties(Configuration.class, props);
    }
}
```

## Search DICOM files using XPath expressions
The fact that DICOM structured reports are internally structured as a tree --
possibly spanning several individual files -- opens up the possibility of using ideas from
XPath (which relates to tree structures in XML) to search within DICOM documents. The analysis
of structured reports, where you have to find "patterns" within DICOM documents maps straight 
away to XPath. 

This project wraps functionality for searching DICOM files using XPath expressions. 
As described in chapter 4 in "DICOM Structured Reporting" by David A. Clunie, a 
structured report exhibits an internal structure of a tree, so it makes sense 
to match this reality with ideas addressing tree structures. Searching XML using
XPath is one such pattern. 

The expressions are XPath alright (using the Jaxen parser and
machinery), but in order to adapt to the DICOM concepts, I had to
implement an XML-ish model onto DICOM.

![Image](doc/xml-like-structure-onto-dicom.png?raw=true)

You will find a DicomDocument, which corresponds to a DICOM file such
as DICOMDIR. You will further find a DicomElement, which corresponds
to individual sequences in the DICOM file. I also had to map
individual DICOM tags onto the XML attribute concept, so if this had
been an XML file then all information would be kept in attributes.

This is a finding tool. Encapsulated within each DicomElement object
you'll find the dcm4che3 org.dcm4che3.data.Attributes object that has
all relevant information. The value representation in the wrapper
objects are relatively rudimentary and only exists to be able to form
XPath expressions.

The idea is to use this tool to find the relevant pieces you are interested
in and then dive into the org.dcm4che3.data.Attributes object to pull
the bits an pieces you need.

Searching for a DICOM tag is currently done via it's name and not
it's id number. Using the composite (group.element) clashes with
the XPath parser -- using the plain number could be an alternative

Using the DICOM Visualizr (a sibling project), this is the tree that
we are searching in and the lone DicomElement we are searching for:

![Image](doc/screencapture.png?raw=true)

## Really simple examples
The test program runs against test-data (found among the resources) and tests these things:

* Using XPath expression: `/`
```
Found DicomElement {DICOMDIR}
```

* Using XPath expression: `//ConceptNameCodeSequence`
```
Found DicomElement {(0040,A043) ConceptNameCodeSequence}
```

* Using XPath expression: `//ConceptNameCodeSequence/@CodeValue`
```
Found DicomAttribute {(0008,0100) CodeValue vr=SH value="45_01004001"}
```

* Using XPath expression:
  `//ConceptNameCodeSequence[@CodeValue='45_01004001']`
```
Found DicomElement {(0040,A043) ConceptNameCodeSequence}
```

* Using XPath expression:
  `//ConceptNameCodeSequence[@CodeValue='45_01004001']/@CodeValue`
```
Found DicomAttribute {(0008,0100) CodeValue vr=SH value="45_01004001"}
```

* Using XPath expression:
  `//ConceptNameCodeSequence[@CodeValue='45_01004001' and @CodingSchemeDesignator='99_PHILIPS']/@CodeValue`
```
Found DicomAttribute {(0008,0100) CodeValue vr=SH value="45_01004001"}
```

## A somewhat more complex example
Consider this tree-structured snippet of a structured report:
```
        [(0040,A730) ContentSequence]
            (0040,A010) RelationshipType :: CONTAINS
            (0040,A040) ValueType :: CONTAINER
            (0040,A050) ContinuityOfContent :: SEPARATE

            [(0040,A043) ConceptNameCodeSequence ConceptNameCodeSequence]
                (0008,0100) CodeValue :: F-01710
                (0008,0102) CodingSchemeDesignator :: SRT
                (0008,0103) CodingSchemeVersion :: 1.0
                (0008,0104) CodeMeaning :: Breast Composition

            [(0040,A730) ContentSequence]
                (0040,A010) RelationshipType :: CONTAINS
                (0040,A040) ValueType :: CODE

                [(0040,A043) ConceptNameCodeSequence]
                    (0008,0100) CodeValue :: F-01710
                    (0008,0102) CodingSchemeDesignator :: SRT
                    (0008,0103) CodingSchemeVersion :: 1.0
                    (0008,0104) CodeMeaning :: Breast Composition

                [(0040,A168) ConceptCodeSequence]
                    (0008,0100) CodeValue :: F-01713
                    (0008,0102) CodingSchemeDesignator :: SRT
                    (0008,0103) CodingSchemeVersion :: 1.0
                    (0008,0104) CodeMeaning :: ACR3

                [(0040,A730) ContentSequence]
                    (0040,A010) RelationshipType :: HAS CONCEPT MOD
                    (0040,A040) ValueType :: CODE

                    [(0040,A043) ConceptNameCodeSequence]
                        (0008,0100) CodeValue :: G-C171
                        (0008,0102) CodingSchemeDesignator :: SRT
                        (0008,0103) CodingSchemeVersion :: 1.0
                        (0008,0104) CodeMeaning :: Laterality

                    [(0040,A168) ConceptCodeSequence]
                        (0008,0100) CodeValue :: T-04020
                        (0008,0102) CodingSchemeDesignator :: SNM3
                        (0008,0103) CodingSchemeVersion :: 1.0
                        (0008,0104) CodeMeaning :: Right breast
```

We want to extract the (estimated) overall breast composition using the SNOMED-RT vocabulary.
This XPath expression would be accurate, matching a great many details from the tree above
```
//ConceptCodeSequence[
  (../../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) 
  and 
  (../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) 
  and 
  (../ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='SNM3' and @CodeValue='T-04020'])
]
```

Going into some detail:

1. The term `//ConceptCodeSequence` matches any DicomElement (in project lingo) named "ConceptCodeSequence",
2. so we add a predicate using the `[ predicate ]`, which consists of two demands
3. `../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']` must match sibling element
1. having two matching attributes
   - `@CodingSchemeDesignator='SRT'` and
   - `@CodeValue='F-01710'`
4. `../ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='SNM3' and @CodeValue='T-04020']` must match sibling element
1. having two matching attributes
   - `@CodingSchemeDesignator='SNM3'` and
   - `@CodeValue='T-04020'`

This is a variation of the preceding XPath query:
```
//ConceptCodeSequence[
  ancestor::ContentSequence/ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']
  and
  preceding-sibling::ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710'] 
  and
  following-sibling::ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='SNM3' and @CodeValue='T-04020'] 
]
```

For more details on how to form XPath expressions, I kindly refer you to [Google](http://lmgtfy.com/?q=XPath+expressions).

## Java code
Locate the DicomElement containing the breast composition (SRT:F-01713) attribute (and not the attribute itself).
```java
DicomLoader loader = new DicomLoader();

File sr = new File("/path/to/SR00003.DCM");
loader.load(sr);

DicomDocument doc = loader.getDicomDocument();
DicomElement rootElement = doc.getDicomObject();

String expr = "//ConceptCodeSequence[(../../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) and (../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) and (../ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='SNM3' and @CodeValue='T-04020'])]";
XPath xpath = new XPath(expr);
System.out.println("Searching right breast density using: " + xpath.toString() + "\n -> " + xpath.debug());
List nodes = xpath.selectNodes(rootElement);
for (Object node : nodes) {
   // We are matching on a DicomElement and not an individual attribute, so
   System.out.println(((DicomElement)node).asText(/* recurse? */ false);
   
   // From here, we can switch to the dcm4che realm completely
   Attributes attributes = ((DicomElement)node).getAttributes();
   System.out.println("Corresponding raw dcm4che3 Attributes:");
   System.out.println(attributes);
}
```

Output:
```
Searching right breast density using: //ConceptCodeSequence[(../../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) and (../ConceptNameCodeSequence[@CodingSchemeDesignator='SRT' and @CodeValue='F-01710']) and (../ContentSequence/ConceptCodeSequence[@CodingSchemeDesignator='SNM3' and @CodeValue='T-04020'])]
 -> [(DefaultXPath): [(DefaultAbsoluteLocationPath): [(DefaultAllNodeStep): descendant-or-self]/[(DefaultNameStep): ConceptCodeSequence]]]
[(0040,A168) ConceptCodeSequence]
    (0008,0100) CodeValue :: F-01713
    (0008,0102) CodingSchemeDesignator :: SRT
    (0008,0103) CodingSchemeVersion :: 1.0
    (0008,0104) CodeMeaning :: ACR3

Corresponding raw dcm4che3 Attributes:
(0008,0100) SH [F-01713] CodeValue
(0008,0102) SH [SRT] CodingSchemeDesignator
(0008,0103) SH [1.0] CodingSchemeVersion
(0008,0104) LO [ACR3] CodeMeaning

```

If we instead were to locate the attribute itself, we could do like this:
```java
// code continues from section above...
String expr2 = expr + "/@CodeValue";
xpath = new XPath(expr2);
nodes = xpath.selectNodes(rootElement);
for (Object node : nodes) {
  // We are matching on a DicomAttribute and not the encompassing DicomElement, so
  System.out.println(((DicomAttribute)node).asText();
}
```

Output:
```
(0008,0100) CodeValue :: F-01713
```

We have transformed the trouble of navigating the DICOM tree to composing XPath expressions!

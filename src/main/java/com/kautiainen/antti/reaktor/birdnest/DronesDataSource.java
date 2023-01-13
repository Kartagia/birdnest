package com.kautiainen.antti.reaktor.birdnest;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.kautiainen.antti.reaktor.birdnest.data.HttpDataSource;

/**
 * Drones handles the drones from the data source.
 * <p>
 * The data source is read, and chosen drone nodes are stored to the drones set
 * on update.
 * The update does not alter the previous drones set, but replaces it to prevent
 * corrupted data.
 * </p>
 * <p>
 * By default positions does select drones which are withing DMZ, but user may
 * override this
 * behavior by changing the {@link #validDrone} to determine the chosen drones
 * differently.
 * </p>
 */
public class DronesDataSource extends HttpDataSource<Document> {

    /**
     * Get the document builder.
     * 
     * @return Get the document builder.
     */
    protected static DocumentBuilder getXmlDocumentBuilder() {
        try {
            DocumentBuilderFactory xmlReaderFactory = DocumentBuilderFactory.newInstance();
            return xmlReaderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // The document buider factory is wrong.
            return null;
        }
    }

    /**
     * Function reading the XML document of the drones.
     * 
     * @return Function which builds XML document from InputStream object.
     *         The returned object is null in case of any error.
     */
    public static Function<InputStream, Document> getDocumentReader() {
        return (InputStream in) -> {
            try {
                return getXmlDocumentBuilder().parse(in);
            } catch (SAXException e) {
                // TODO: logging.
                return null;
            } catch (IOException e) {
                // TODO: logging.
                return null;
            }
        };
    }

    /**
     * The default attribute containing the capture timestamp.
     */
    private static final String CAPTURE_TIMESTAMP = "snapshotTimestamp";

    /**
     * The default data root tag.
     */
    private static final String DATA_ROOT_TAG = "report";

    /**
     * The default DMZ threshold range.
     */
    public static final int DEFAULT_DMZ_THRESHOLD = 100 * 1000;

    /**
     * The default capture tag name.
     */
    protected static final String CAPTURE_TAG_NAME = "capture";

    /**
     * The default drone tag name.
     */
    protected static final String DRONE_TAG_NAME = "drone";

    /**
     * The default rest server for drone report.
     */
    protected static final String DEFAULT_DRONE_REPORT_HOST = "assignments.reaktor.com";

    /**
     * The default URI of the data source.
     */
    protected static final String DEFAULT_DRONE_REPORT_REST_PATH = "/birdnest/drones";

    /**
     * The array list of the drone elements of the matching drones.
     */
    private volatile ArrayList<Element> drones_ = new ArrayList<>();

    /**
     * The function testing whether the drone is confirmed to fly in the NDZ.
     */
    private java.util.function.Predicate<Node> droneTester_ = (Node node) -> {
        if (validDroneNode(node)) {
            // Testing the distance.
            Double distance = getDroneDistanceToNest((Element) node);

            // Ceiling is used on distance to ensure that the drone is within the NDZ. Due
            // coordinate
            // system this creates 1 mm accuracy.
            return (Math.ceil(distance) <= getTreshholdRange());
        }

        // The test failed
        return false;
    };

    /**
     * Test a drone node.
     * 
     * @param node The tested node.
     * @return True, if and only if the given node is valid drone node.
     */
    protected boolean validDroneNode(Node node) {
        return node instanceof Element && getDroneTag().equals(node.getNodeName());
    }

    /**
     * The threshold range of the NDZ in millimeters.
     */
    private double thresholdRange_ = DEFAULT_DMZ_THRESHOLD;

    /**
     * The base Y position of the nest in millimeters.
     */
    private double nestYPosition_ = 250000.0;

    /**
     * The base X position of the nest in millimeters.
     */
    private double nestXPosition_ = 250000.0;

    /**
     * The update time of the drones. This value is undefined
     * (<code>null</code>), if the drones data has not been updated.
     */
    private ZonedDateTime updateTime_ = null;

    /**
     * Create a new drone positions with default source.
     * 
     * @throws URISyntaxException The default uri is malformed.
     */
    public DronesDataSource() throws URISyntaxException {
        super(DEFAULT_DRONE_REPORT_HOST, DEFAULT_DRONE_REPORT_REST_PATH, getDocumentReader());
    }

    /**
     * Create a new data source getting information from given URI.
     * 
     * @param uri the URI from which the data is read.
     */
    public DronesDataSource(@NotNull URI uri) {
        super(uri, getDocumentReader());
    }

    /**
     * Createa a new drone positions with given url.
     * 
     * @param source The source URL for the data.
     * @throws IllegaArgumentException The given source was invalid.
     * @throws IllegalStateException   The document builder initialization failed.
     * @throws URISyntaxException      THe given URL does not translate to URI.
     */
    public DronesDataSource(URL source) throws IllegalArgumentException, IllegalStateException, URISyntaxException {
        this(source.toURI());
    }

    /**
     * Create a new data source with given URI.
     * 
     * @param string
     * @throws URISyntaxException
     */
    public DronesDataSource(String string) throws URISyntaxException {
        this(URI.create(string));
    }

    /**
     * Read the report from data rest server.
     * 
     * @return The XML document containing
     * @throws IOException              The operation failed on input error.
     * @throws StreamCorruptedException The result did not contain proper report.
     * @throws EOFException             The read XML document could not be converted
     *                                  into string.
     */
    public Optional<Document> getReport() throws IOException {
        return getData();
    }

    /**
     * Get the sole child with given tag name.
     * 
     * @param element The parent element. Defaults to an element without children.
     * @param tagName The tag name of the sought child node.
     * @return The sole child node, if any exists. Otherwise, an undefined
     *         value (<code>null</code>).
     */
    public static Node seekChildNode(Element element, String tagName) {
        if (tagName == null || element == null)
            return null;

        Stream<Node> children = seekChildNodes(element,
                (Node node) -> (node instanceof Element && tagName.equals(node.getNodeName())));

        if (children.count() > 1) {
            // There was more than one child matching the value.
            return null;
        } else {
            // There was only one node.
            return children.findFirst().orElse(null);
        }
    }

    /**
     * Get child nodes with given names as list.
     * 
     * @param element The parent element. Defaults to an element without children.
     * @param tagName The tag name of the sought children. If the tag name is an
     *                empty
     *                string, or CDATA, the text nodes, and CDATA sections are
     *                retunred. Defaults to
     *                no tag name at all rejecting all children.
     * @return The list containing all child nodes with given tag.
     */
    public static java.util.List<Node> seekChildNodes(Element element, String tagName) {
        if (tagName == null || element == null)
            return Collections.emptyList();

        Stream<Node> children = seekChildNodes(element,
                (Node node) -> (tagName.equals(node.getNodeName())));

        return children.toList();
    }

    /**
     * Seek children matching to the given tester.
     * 
     * @param node   The element whose children are chosen. Defaults to an undefined
     *               node.
     * @param tester The tester testing the node. Defaults to tester rejecting all
     *               values.
     * @return A stream containin gall children of the given node accepted by the
     *         given tester.
     */
    public static Stream<Node> seekChildNodes(Element node, Predicate<? super Node> tester) {
        Stream.Builder<Node> result = Stream.builder();
        if (node != null && tester != null) {
            NodeList children = node.getChildNodes();
            int childCount = children.getLength();
            Node childNode;
            for (int i = 0; i < childCount; i++) {
                childNode = children.item(i);
                if (tester.test(childNode)) {
                    result.accept(childNode);
                }
            }
        }
        return result.build();
    }

    /**
     * Get the position value of an element.
     * 
     * @param node The node containing the position value.
     * @return The position value of the node.
     * @throws NullPointerException  The given node was undefined.
     * @throws NumberFormatException The given node did not contain valid position
     *                               value.
     */
    protected Double getPositionValue(Element node) throws NullPointerException, NumberFormatException {
        return Double.parseDouble(node.getTextContent().trim());
    }

    /**
     * Get the drone X coordinate.
     * 
     * @return If the given drone is valid, the drone X coordinate. Otherwise,
     *         an undefined value.
     * @throws NullPointerException  The given node does not have the position
     *                               attribute.
     * @throws NumberFormatException The value of the X-coordinate was not valid.
     */
    protected Double getDroneXPosition(Element drone) throws NullPointerException, NumberFormatException {
        if (validDroneNode(drone)) {
            Node xNode = seekChildNode(drone, getXPositionTagName());
            return getPositionValue((Element) xNode);
        }
        // The node was invalid.
        return null;
    }

    /**
     * Get the tag name of the X position element.
     * 
     * @return The defined tag name containing the X position.
     */
    protected String getXPositionTagName() {
        return "xPosition";
    }

    /**
     * Get the drone Y coordinate.
     * 
     * @return If the given drone is valid, the drone Y coordinate. Otherwise,
     *         an undefined value.
     * @throws NullPointerException  The given node does not have the position
     *                               attribute.
     * @throws NumberFormatException The value of the X-coordinate was not valid.
     */
    protected Double getDroneYPosition(Element drone) {
        if (validDroneNode(drone)) {
            Node xNode = seekChildNode(drone, getYPositionTagName());
            return getPositionValue((Element) xNode);
        }
        // The node was invalid.
        return null;
    }

    /**
     * Get the tag name of the Y position element.
     * 
     * @return The defined tag name containing the Y position.
     */
    protected String getYPositionTagName() {
        return "yPosition";
    }

    /**
     * Get the drone Z coordinate.
     * 
     * @return If the given drone is valid, the drone Z coordinate. Otherwise,
     *         an undefined value.
     * @throws NullPointerException  The given node does not have the position
     *                               attribute.
     * @throws NumberFormatException The value of the X-coordinate was not valid.
     */
    protected Double getDroneZPosition(Element drone) throws NullPointerException, NumberFormatException {
        if (validDroneNode(drone)) {
            Node xNode = seekChildNode(drone, getZPositionTagName());
            return getPositionValue((Element) xNode);
        }
        // The node was invalid.
        return null;
    }

    /**
     * Get the tag name of the Z position element.
     * 
     * @return The defined tag name containing the Z position.
     */
    protected String getZPositionTagName() {
        return "zPosition";
    }

    /**
     * The tag of the drone.
     * 
     * @return The tag of the drone.
     */
    private String getDroneTag() {
        return DRONE_TAG_NAME;
    }

    /**
     * Test validity of teh given source URL.
     * 
     * @param source The tested source URL.
     * @return True, if and only if the given URL is accepted.
     */
    public boolean validSourceUrl(URL source) {
        return source != null;
    }

    /**
     * Get the threshold range.
     * 
     * @return The threshold range.
     */
    public double getTreshholdRange() {
        return thresholdRange_;
    }

    /**
     * Get the base X coordinate.
     * 
     * @return The base X coordinate.
     */
    public double getNestXPosition() {
        return nestXPosition_;
    }

    /**
     * Get the base Y coordinate.
     * 
     * @return The base Y coordinate.
     */
    public double getNestYPosition_() {
        return nestYPosition_;
    }

    /**
     * Updates the drone position data.
     * 
     * @throws IOException           The updated failed due IO Exception.
     * @throws SAXException          The update failed due parse exception during
     *                               parsing of the
     *                               XML document.
     * @throws IllegalStateException The update failed due internal state of the
     *                               drones.
     */
    public synchronized void update() throws IOException, SAXException, IllegalStateException {
        try {
            // Get next document from source.
            Document xmlDoc = this.getReport().orElse(null);

            // Testing the validity of the read data.
            if (xmlDoc != null && !validDataNode(xmlDoc)) {
                // The node was invalid.
                xmlDoc = null;
            }
            if (xmlDoc == null) {
                // The retries failed.
                throw new IOException("Could not access the source");
            } else {
                // Updating the data from xml document.
                // TODO: Remove debug output
                System.err.println("xmlDoc: " + xmlDoc.toString());
                handleData(xmlDoc);
            }
        } catch (SAXException saxException) {
            // Stream is corrupted.
            throw new java.io.StreamCorruptedException(
                    saxException == null ? "Parsing the xml drone information failed" : saxException.getMessage());
        } catch (IOException ioe) {
            // IO exception causes
            throw ioe;
        }
    }

    /**
     * Performs update, and return updated drones.
     * 
     * @return The list of updated matching drone nodes.
     * @throws SAXException          The parse of the updated data failed.
     * @throws IllegalStateException The state of the current drones was invalid.
     * @throws IOException           The operation failed due Input Exception.
     */
    public synchronized java.util.List<Element> updateAndGetMatchingDrones()
            throws SAXException, IllegalStateException, IOException {
        update();
        return getMatchingDroneNodes();
    }

    /**
     * Test validity of the XML document.
     * 
     * @param xmlDoc THe tested document
     * @return True, if and only if the given node is valid data document.
     */
    private boolean validDataNode(Document xmlDoc) {
        return xmlDoc != null && this.getDataRootTag().equals(xmlDoc.getDocumentElement().getNodeName());
    }

    /**
     * Get the XML node containing the most recent update.
     * 
     * @param reportElement The report element.
     * @return The most recent capture element. Otherwise, an undefined value
     *         (<code>null</code>).
     */
    protected Element getMostRecentCapture(Element reportElement) {
        Element mostRecentCapture = null;
        if (reportElement != null) {
            java.time.ZonedDateTime captureTime = null;
            java.time.ZonedDateTime mostRecentTime = null;
            Element capture = null;
            NodeList captures = reportElement.getElementsByTagName(getCaptureTagname());
            int nodeCount = captures.getLength();
            for (int i = 0; i < nodeCount; i++) {
                capture = (Element) captures.item(i);
                captureTime = java.time.ZonedDateTime.parse(capture.getAttribute(CAPTURE_TIMESTAMP));
                if (mostRecentTime == null || captureTime != null && mostRecentTime.isBefore(captureTime)) {
                    mostRecentCapture = capture;
                    mostRecentTime = captureTime;
                }
            }
        }
        return mostRecentCapture;
    }

    /**
     * Get the tag name containing hte capture of the drone position information.
     * 
     * @return Always defined string containing valid tag name for capture tag name.
     */
    protected String getCaptureTagname() {
        return CAPTURE_TAG_NAME;
    }

    /**
     * Handle the drone data. This throws parse exception.
     * 
     * @param data The DOM document containing the xml data.
     * @throws SAXException The given document is invalid.
     */
    public synchronized void handleData(Document data) throws SAXException {
        // Testing we do have proper xml data.
        if (data == null || !getDataRootTag().equals(data.getDocumentElement().getNodeName())) {
            // We do have invalid type.
            throw new SAXException("Invalid XML source");
        }

        // Getting the most recent capture - in case the XML file format has chagned to
        // allow several captures.
        Element report = data.getDocumentElement();
        Element mostRecentCapture = getMostRecentCapture(report);

        // Most recetn capture contains the most recent capture of the data.
        ArrayList<Element> matchingDrones = new ArrayList<>();
        if (mostRecentCapture != null) {
            // Handling the drones.
            // This section has to be secured.
            NodeList drones = mostRecentCapture.getElementsByTagName(getDroneTag());
            int droneCount = drones.getLength();
            for (int i = 0; i < droneCount; i++) {
                matchingDrones.add((Element) drones.item(i));
            }
        } else {
            // Logging report that the data was empty.
            System.getLogger(DronesDataSource.class.getName()).log(Level.ERROR, "Capture not found!");
            matchingDrones = new ArrayList<>();
        }

        // Updating the data of drones - this is synchronized to prevetn false
        // information.
        this.drones_ = matchingDrones;
        this.updateTime_ = getCaptureTime(mostRecentCapture);

    }

    /**
     * Get the capture time from the element.
     * 
     * @param element The capture element.
     * @return The capture time of the given element.
     * @throws IllegalArgumentException The given element was invalid - either due
     *                                  invalid timestamp value,
     *                                  or due invalid node.
     */
    protected ZonedDateTime getCaptureTime(Element element) throws IllegalArgumentException {
        if (element != null && this.getCaptureTagname().equals(element.getTagName())) {
            try {
                return ZonedDateTime.parse(element.getAttribute(getSnapShotAttributeName()));
            } catch (java.time.DateTimeException timeException) {
                throw new IllegalArgumentException("Invalid timestamp");
            } catch (NullPointerException npe) {
                throw new IllegalArgumentException("Invalid capture element is missing timestamp");
            }
        } else {
            throw new IllegalArgumentException("Invalid capture element");
        }
    }

    /**
     * Get the attribute name of the capture time of the capture node.
     * 
     * @return Always existing name of the snapshot attribute of the capture node
     *         containing
     *         the capture time.
     */
    protected String getSnapShotAttributeName() {
        return CAPTURE_TIMESTAMP;
    }

    /**
     * Get the XML structure root tag name.
     * 
     * @return the root tag name.
     */
    private String getDataRootTag() {
        return DATA_ROOT_TAG;
    }

    /**
     * Test a drone node for violating the NDZ.
     * 
     * @param droneNode The drone node.
     * @return True, if and only if the given node passes the NDZ test.
     */
    public boolean validDrone(Node droneNode) {
        return this.droneTester_.test(droneNode);
    }

    /**
     * Get the matching drones at the moment.
     * The acquired list is not altered by the drones list.
     */
    public synchronized java.util.List<Element> getMatchingDroneNodes() {
        // Generating list of matching drones.
        return this.drones_.stream().filter((Element element) -> (element != null && validDrone(element))).toList();
    }

    /**
     * Get the drone distance to the nest.
     * 
     * @param droneElement The drone element.
     * @return The distance from the drone to the base, if the drone element is
     *         valid. Otherwise, an undefined value.
     */
    public Double getDroneDistanceToNest(Element droneElement) {
        if (this.validDrone(droneElement)) {
            // Calculating hte distance.
            double xPosition, yPosition;
            synchronized (this) {
                xPosition = getNestXPosition();
                yPosition = getNestYPosition_();
            }
            try {
                xPosition = getDroneXPosition(droneElement) - xPosition;
                yPosition = getDroneXPosition(droneElement) - yPosition;
                return Math.sqrt(Math.pow(xPosition, 2) + Math.pow(yPosition, 2));
            } catch (NullPointerException nullPosition) {
                // Either X- or Y-position was invalid.
                return null;
            }
        } else {
            // The distance is not available.
            return null;
        }
    }

    /**
     * The most recent drones data acquisition time.
     *
     * @return The time of the most recent drones data.
     */
    public java.time.ZonedDateTime getUpdateTime() {
        return updateTime_;
    }

    /**
     * Get the drone serial number.
     * 
     * @param droneNode The drone node.
     * @return The drone serial number, if the drone node was valid.
     *         Otherwise, and undefined value (<code>null</code>)
     */
    public String getDroneSerialNumber(Element droneNode) {
        if (droneNode != null) {
            Node result = seekChildNode(droneNode, getSerialNumberTagName());
            return result == null ? null : result.getTextContent().trim();
        } else {
            return null;
        }
    }

    /**
     * Get the tag name of the serial number containing element.
     * 
     * @return THe tag name of the serial number containing node.
     */
    protected String getSerialNumberTagName() {
        return "serialNumber";
    }

    /**
     * Get the current update interval in milliseconds.
     * 
     * @return The current update interval.
     */
    public synchronized long getUpdateInterval() {
        return 2000;
    }

    /**
     * The list of all drones.
     * 
     * @return The lsit of all drones of the current snapshot.
     */
    public synchronized List<Element> getDroneNodes() {
        return Collections.unmodifiableList(drones_);
    }

}

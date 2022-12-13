
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.text.StringEscapeUtils;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

/**
 * PilotLoader loads pilot data.
 */
public class PilotLoader {

    /**
     * The format strign for read only porperty.
     */
    private static final String READ_ONLY_PROPERTY_FORMAT_STRING = "Read only property %s of %s with value %s";

    /**
     * THe format string taking property name, object name, and value as parameters.
     */
    private static final String UNKNOWN_PROPERTY_FORMAT_STRING = "Unknown %s property of %s with value %s";

    /**
     * THe message indicating an innocent pilot is requested.
     */
    private static final String INNOCENT_PILOT_MESSAGE = "The privacy protection prevents the acquisition of the pilot data";

    /**
     * The message indicating the rest client has already been initialized.
     */
    public static final String REST_CLIENT_ALREADY_INITIALIXED_MESSAGE = "Rest client already initialized";

    /**
     * The undefined value string.
     */
    public static final String UNDEFINED_VALUE_STRING = "undefined";

    /**
     * Quote a string representation, if it is present.
     * 
     * @param value the quoted value.
     * @return The strign reprsentation of the given value. The value is
     *  the string representation of the given value quoted, if the value, and
     *  its string representation are defined. Otherwise, the string {@link #UNDEFINED_VALUE_STRING}
     *  is returned.
     */
    public static String quoteIfPresent(Object value) {
        String stringRep = value == null ? null : value.toString();
        if (stringRep == null) {
            return UNDEFINED_VALUE_STRING;
        } else {
            return String.format("\"%s\"", StringEscapeUtils.escapeJson(stringRep));
        }
    }

    /**
     * The rest clietn service performing the REST client request for pilot data.
     */
    private WebResource restClientService_ = null;

    /**
     * The expiration time of the pilot data in minutes.
     */
    private int expirationTimeInMinutes_ = Pilot.DEFAULT_EXPIRATION_TIMEOUT;

    /**
     * Create pilot laoder for given host and base rest path.
     * 
     * @param host The host of the 
     * @param resourcePath The resource path without parameters.
     * @throws IllegalArgumentException The given host or resource path is invalid.
     */
    public PilotLoader(String host, String resourcePath) throws IllegalArgumentException {
        initRestClient(host, resourcePath);
    }

    /**
     * Initialize rest client.
     * 
     * @param host The REST host.
     * @param resourcePath The resource path to the resource.
     * @throws IllegalArgumentException The given host or resource path was invalid.
     * @throws IllegalStateException The rest client has pready been initialized.
     */
    protected void initRestClient(String host, String resourcePath) throws IllegalArgumentException, IllegalStateException {
        if (this.restClientService_ == null) {
            ClientConfig config = new DefaultClientConfig();
            Client client = Client.create(config);
            restClientService_ = client.resource(UriBuilder.fromUri(host).build()).path(resourcePath);    
        } else {
            throw new IllegalStateException(REST_CLIENT_ALREADY_INITIALIXED_MESSAGE);
        }
    }

    /**
     * Get pilot from service.
     * 
     * @param droneSerial   The serial number of the drone.
     * @param violationTime The time of the violation. The value must be defined and
     *                      withing expiration time.
     * @throws IllegalArgumentException The given drone serial was invalid.
     */
    public Pilot getPilot(String droneSerial, ZonedDateTime violationTime, double violationDistance)
            throws IllegalArgumentException, IOException {
        if (!validViolationTime(violationTime)) {
            throw new IllegalArgumentException(INNOCENT_PILOT_MESSAGE);
        }

        try {
            String pilotString = restClientService_.path(droneSerial).accept(MediaType.APPLICATION_JSON).get(String.class);
            JsonObject json = Json.createReader(new StringReader(pilotString)).readObject();
            Pilot pilot = new Pilot();
            for (Entry<String, JsonValue> entry: json.entrySet()) {
                try {
                    Method method = new PropertyDescriptor(entry.getKey(), Pilot.class).getWriteMethod();
                    if (method == null) {
                        System.getLogger(Pilot.class.getName()).log(Level.INFO, READ_ONLY_PROPERTY_FORMAT_STRING, 
                        quoteIfPresent(entry.getKey()), "Pilot", quoteIfPresent(entry.getValue()));    
                    } else {
                        method.invoke(pilot, entry.getValue());
                    }
                } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
                    // The property is not supported by the bean.
                    System.getLogger(Pilot.class.getName()).log(Level.INFO, UNKNOWN_PROPERTY_FORMAT_STRING, 
                    quoteIfPresent(entry.getKey()), "Pilot", quoteIfPresent(entry.getValue()));
                }
                
            }
            pilot.setClosestDistanceToNest(violationDistance);
            pilot.setDroneSerial(droneSerial);
            pilot.setViolationTime(violationTime);
            pilot.build();
            return pilot;
        } catch (UniformInterfaceException httpError) {
            throw new java.io.EOFException("Http error: " + quoteIfPresent(httpError.getMessage()));
        } catch (ClientHandlerException streamCorrupted) {
            throw new java.io.StreamCorruptedException("Invalid response content " + quoteIfPresent(streamCorrupted.getMessage()));
        }
    }

    /**
     * Test validity of the violation time.
     * 
     * @param violationTime The violation time.
     * @return True, if and only if the given violation time is allowed for
     *         acquisition of the pilot information.
     */
    public boolean validViolationTime(ZonedDateTime violationTime) {
        return ZonedDateTime.now().plusMinutes(expirationTimeInMinutes_).isAfter(violationTime);
    }

}

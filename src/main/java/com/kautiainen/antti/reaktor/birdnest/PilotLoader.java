package com.kautiainen.antti.reaktor.birdnest;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.commons.text.StringEscapeUtils;

import com.kautiainen.antti.reaktor.birdnest.rest.RestDataSource;
import com.kautiainen.antti.reaktor.birdnest.rest.RestParameter;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;

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
     *         the string representation of the given value quoted, if the value,
     *         and
     *         its string representation are defined. Otherwise, the string
     *         {@link #UNDEFINED_VALUE_STRING}
     *         is returned.
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
     * PilotReader reads pilot data from given input stream.
     */
    public static class PilotReader implements Function<InputStream, JsonObject> {

        /**
         * Creates a new pilot reader. The reader has no error handlers.
         */
        public PilotReader() {
        }

        /**
         * Reads the pilot information from the data source.
         * 
         * @param stream The input stream from which the pilot data is read.
         * @return Pilot, if the data source was solid. An undefined (null) value,
         *         if any errors occured.
         */
        public JsonObject apply(InputStream stream) {
            // Reads the pilot data from the given stream.
            return Json.createReader(stream).readObject();
        }

    }

    /**
     * 
     */
    RestDataSource<JsonObject> dataSource_;

    /**
     * The expiration time of the pilot data in minutes.
     */
    private int expirationTimeInMinutes_ = Pilot.DEFAULT_EXPIRATION_TIMEOUT;

    /**
     * IO error handlers.
     */
    private java.util.List<Consumer<? super IOException>> ioErrorHandlers_ = new java.util.ArrayList<>();

    /**
     * Create pilot laoder for given host and base rest path.
     * 
     * @param host         The host of the
     * @param resourcePath The resource path without parameters.
     * @throws IllegalArgumentException The given host or resource path is invalid.
     */
    public PilotLoader(String host, String resourcePath) throws IllegalArgumentException {
        try {
            dataSource_ = new RestDataSource<JsonObject>(new URI("https", host, resourcePath, null),
                    new PilotReader(),
                    new RestParameter<String>("serialNumber",
                            Pattern.compile("[-\\]w+"),
                            (String x) -> (x),
                            (String x) -> (x)));
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new IllegalArgumentException("Invalid rest uri", e);
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
            JsonObject json = this.dataSource_.get(Collections.singletonList(droneSerial));

            Pilot pilot = new Pilot();
            for (Entry<String, JsonValue> entry : json.entrySet()) {
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
            throw new java.io.StreamCorruptedException(
                    "Invalid response content " + quoteIfPresent(streamCorrupted.getMessage()));

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

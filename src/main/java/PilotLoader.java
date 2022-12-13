
import java.io.IOException;
import java.time.ZonedDateTime;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
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

    private WebResource restClientService_;

    private int expirationTimeInMinutes_ = Pilot.DEFAULT_EXPIRATION_TIMEOUT;

    public PilotLoader(String host, String restPath) {
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        restClientService_ = client.resource(UriBuilder.fromUri(host).build());
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
            throw new IllegalArgumentException("The privacy protection prevents the acquisition of the pilot data");
        }

        try {
            Pilot pilot = restClientService_.path(droneSerial).accept(MediaType.APPLICATION_JSON).get(Pilot.class);
            pilot.setDistance(violationDistance);
            pilot.setDroneSerial(droneSerial);
            pilot.setViolationTime(violationTime);
            if (pilot.isValid()) {
                return pilot;
            } else {
                throw new IllegalArgumentException("Invalid pilot");
            }
        } catch (UniformInterfaceException httpError) {
            throw new java.io.EOFException("Http error" + httpError.getMessage());
        } catch (ClientHandlerException streamCorrupted) {
            throw new java.io.StreamCorruptedException("Invalid response content " + streamCorrupted.getMessage());
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

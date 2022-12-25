import java.time.ZonedDateTime;
import java.util.Arrays;

import javax.json.JsonValue;

/**
 * Pilot contains the pilot information.
 */
public class Pilot {
    
    /**
     * Invalid expire time message.
     */
    private static final String INVALID_EXPIRE_TIME_MESSAGE = "Pilot has no expiration time or the time is in future";

    /**
     * Invalid distance to the nest message.
     */
    private static final String INVALID_DISTANCE_TO_NEST_MESSAGE = "Invalid distance to nest";

    /**
     * The invalid drone serila number message.
     */
    private static final String INVALID_DRONE_SARIAL_NUMBER_MESSAGE = "Invalid drone sarial number";

    /**
     * The invalid pilot identifier message.
     */
    private static final String INVALID_PILOT_IDENTIFIER_MESSAGE = "Invalid pilot identifier";

    /**
     * The invalid last name message.
     */
    private static final String INVALID_LAST_NAME_MESSAGE = "Invalid last name";

    /**
     * The invalid first name message.
     */
    private static final String INVALID_FIRST_NAME_MESSAGE = "Invalid first name";

    /**
     * The invalid email address message.
     */
    private static final String INVALID_EMAIL_ADDRESS_MESSAGE = "Invalid email address";

    /**
     * The invalid phone number message.
     */
    public static final String INVALID_PHONE_NUMBER_MESSAGE = "Invalid phone number";

    /**
     * Does the pilot object exist in building mode. Building mode allows setting values which are later locked
     * as constant.
     */
    private boolean building = true;

    /**
     * The default expiration timeout.
     */
    public static final int DEFAULT_EXPIRATION_TIMEOUT = 10;

    /**
     * The expiration time of the pilot data.
     */
    private ZonedDateTime expireTime_ = null;

    /**
     * The phone number of the pilot.
     */
    private String phone_;

    /**
     * The email of the pilot.
     */
    private String email_;

    /**
     * The pilot drone serial number.
     */
    private String droneSerialNumber_; 

    /**
     * The closest distance to the nest the drone has visiited while it has been within
     * the sensor range. In millimeters.
     */
    private double distance_;

    /**
     * The pilot identifier.
     */
    private String pilotId_;

    /**
     * The pilot's first name.
     */
    private String firstName_;

    /**
     * The pilot's last name.
     */
    private String lastName_;

    /**
     * Bean cosntructor creating a new pilot. The pilot is not valid.
     */
    public Pilot() {
    }

    /**
     * Create a new pilot with given details.
     * 
     * @param droneSerial The serial number of the pilot.
     * @param detectionTime The time of the detection.
     * @param distanceToNest The distance to the next.
     * @param name The name of the pilot. Always defined.
     * @param email The email of the pilot.
     * @param phoneNumber The phone number of the pilot.
     */
    public Pilot(String droneSerial, ZonedDateTime detectionTime, double distanceToNest, String name, String email, String phoneNumber) {
        setDroneSerial(droneSerial);
        setClosestDistanceToNest(distanceToNest);
        setEmail(email);
        setPhoneNumber(phoneNumber);
        if (name == null) {
            // Using default name.
            setLastName("[Pilot of " + droneSerial + "]");
        } else {
            java.util.List<String> nameParts = Arrays.asList(name.split("\\s+"));
            if (nameParts.size() < 2) throw new IllegalArgumentException("Full name is required!");
            setFirstName(String.join(" ", nameParts.subList(0, nameParts.size()-1)));
            setLastName(nameParts.get(nameParts.size()-1));
        }
        setExpireTime(updateExpireTime(detectionTime));
        this.build();
    }

    /**
     * Is the pilot incomplete.
     * 
     * @return True, if and only if the information is incomplete.
     */
    public synchronized boolean isIncomplete() {
        return building;
    }

    /**
     * Test the drone serial.
     * 
     * @param serial The tested serial number.
     * @return True, if and only if the serial nubmer is valid.
     */
    public boolean validDroneSerial(String serial) {
        return serial != null && !serial.isBlank();
    }

    /**
     * Test the expire time.
     * 
     * @param time The tested time.
     * @return True, if and only if the expire time is valid.
     */
    public boolean validExpireTime(ZonedDateTime time) {
        return time != null && ZonedDateTime.now().isAfter(time);
    }

    /**
     * Test the distance to next.
     * 
     * @return True, if and only if the given distance is valid distance.
     */
    public boolean validClosestDistanceToNest(double distance) {
        return distance >= 0;
    }

    /**
     * Builds the object.
     * 
     * @throws IllegalArgumentException Any content was invalid when building.
     */
    public synchronized void build() throws IllegalArgumentException {
        if (this.building) {
            // We do not need to do anything unless building.
            if (!validFirstName(getFirstName())) throw new IllegalArgumentException(INVALID_FIRST_NAME_MESSAGE);
            if (!validLastName(getLastName())) throw new IllegalArgumentException(INVALID_LAST_NAME_MESSAGE);
            if (!validPilotId(getPilotId())) throw new IllegalArgumentException(INVALID_PILOT_IDENTIFIER_MESSAGE);
            if (!validEmail(getEmail())) throw new IllegalArgumentException(INVALID_EMAIL_ADDRESS_MESSAGE);
            if (!validPhoneNumber(getPhoneNumber())) throw new IllegalArgumentException(INVALID_PHONE_NUMBER_MESSAGE);
            if (!validDroneSerial(getDroneSerialNumber())) throw new IllegalArgumentException(INVALID_DRONE_SARIAL_NUMBER_MESSAGE);
            if (!validClosestDistanceToNest(getClosestDistanceToNest())) throw new IllegalArgumentException(INVALID_DISTANCE_TO_NEST_MESSAGE);
            if (!validExpireTime(getExpireTime())) throw new IllegalArgumentException(INVALID_EXPIRE_TIME_MESSAGE);
            
            // The test passed. Ceasing building.
            this.building = false;
        }
    }

    /**
     * Get the drone serial number of the pilot.
     * 
     * @return The drone serial number of the pilot.
     */
    public String getDroneSerialNumber() {
        return this.droneSerialNumber_;
    }

    /**
     * Get the closest distance to the next.
     * 
     * @return Get the closest distance to the nest.
     */
    public synchronized double getClosestDistanceToNest() {
        return this.distance_;
    }

    /**
     * Set distance to the nest.
     * 
     * @param distanceToNest The new distance to the next.
     */
    public synchronized void setClosestDistanceToNest(double distanceToNest) {
        if (this.isIncomplete() || distanceToNest < getClosestDistanceToNest()) {
            // The drone is closer to the nest than it has been before.
            this.distance_ = distanceToNest;
        }
    }

    /**
     * Get the current expiration time of the pilot data.
     * 
     * @return The time when the pilot data expires.
     */
    public synchronized ZonedDateTime getExpireTime() {
        return this.expireTime_;
    }

    /**
     * Set the current expiration time.
     * 
     * @param expireTime The new expiration time.
     * @throws IllegalArgumentException The given expiration time was invalid. This may occur if the
     *  expiration time is undefined, or happens before the current expiration time.
     */
    public synchronized void setExpireTime(ZonedDateTime expireTime) throws IllegalArgumentException {
        ZonedDateTime currentExpireTime = getExpireTime();
        boolean isComplete = !this.isIncomplete();
        if (!validExpireTime(expireTime)) {
            // Invalid expire time due inheret invalidity.
            throw new IllegalArgumentException("Invalid expiration time");
        } else if (isComplete && currentExpireTime != null && currentExpireTime.isAfter(expireTime)) {
            // Invalid expire time for complete pilot.
            throw new IllegalArgumentException("Expire time cannot move backward in time");
        } else {
            // Updating the expire time.
            this.expireTime_ = expireTime;
        }
    }

    /**
     * Is the pilot still valid at given time.
     * 
     * @param now The test time.
     * @return True, if and only if the pilot is still valid.
     */
    public boolean isValid(ZonedDateTime now) {
        return getExpireTime().compareTo(now) >= 0;
    }


    /**
     * Get the name of the pilot.
     * 
     * @return The name of the pilot.
     */
    public String getName() {
        String firstName = this.getFirstName();
        String lastName = this.getLastName();
        return String.format("%s%s%s",
        (firstName == null ? "" : firstName), 
        (firstName == null || lastName == null ? "" : " "), 
        (lastName == null ? "" : lastName)
        );
    }

 
    /**
     * Get the expire time for given detection time.
     * 
     * @param detectionTime The detection time.
     * @return The expiration time for an existing detection time. Otherwise, 
     *  an undefined value (<code>null</code>).
     */
    public ZonedDateTime updateExpireTime(ZonedDateTime detectionTime) {
        if (detectionTime != null) {
            return detectionTime.plusMinutes(getExpirationMinutes());
        } else {
            return null;
        }
    }

    /**
     * Get the first name of the pilot.
     * 
     * @return The first name of the pilot.
     */
    public String getFirstName() {
        return this.firstName_;
    }

    /**
     * Test validity of the first name.
     * 
     * @param firstName The tested first name.
     * @return True, if and only if the value is valid.
     */
    public boolean validFirstName(String firstName) {
        return firstName == null || !firstName.isBlank();
    }

    /**
     * Set the first name of the pilot.
     * 
     * @param firstName The new first name of the pilot.
     * @throws IllegalArgumentException The given first name isinvalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setFirstName(String firstName) {
        if (validFirstName(firstName)) {
            if (this.isIncomplete()) {
                this.firstName_ = firstName;
            } else {
                throw new IllegalStateException("Cannot change the value after consruction");
            }
        } else {
            throw new IllegalArgumentException(INVALID_PILOT_IDENTIFIER_MESSAGE);
        }
    }

    /**
     * Set the first name of the pilot.
     * 
     * @param firstName The new first name of the pilot.
     * @throws IllegalArgumentException The given first name isinvalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setFirstName(JsonValue firstName) throws IllegalArgumentException, IllegalStateException {
        this.setFirstName(firstName == null ? null : firstName.toString());
    }

    /**
     * Get the last name of the pilot.
     * 
     * @return The last name of the pilot.
     */
    public String getLastName() {
        return this.lastName_;
    }

    /**
     * Test validity of the last name.
     * 
     * @param lastName The tested last name.
     * @return True, if and only if the value is valid.
     */
    public boolean validLastName(String lastName) {
        return lastName != null && !lastName.isBlank();
    }

    /**
     * Set the last name of the pilot.
     * 
     * @param lastName The new last name of the pilot.
     * @throws IllegalArgumentException The given last name is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setLastName(String lastName) {
        if (validLastName(lastName)) {
            if (this.isIncomplete()) {
                this.lastName_ = lastName;
            } else {
                throw new IllegalStateException("Cannot change the value after consruction");
            }
        } else {
            throw new IllegalArgumentException(INVALID_PILOT_IDENTIFIER_MESSAGE);
        }
    }

    /**
     * Set the last name of the pilot.
     * 
     * @param lastName The new last name of the pilot.
     * @throws IllegalArgumentException The given last name is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setLastName(JsonValue lastName) throws IllegalArgumentException, IllegalStateException {
        this.setLastName(lastName == null ? null : lastName.toString());
    }

    /**
     * Get the email of the pilot.
     * 
     * @return The email of the pilot.
     */
    public String getEmail() {
        return this.email_;
    }

    /**
     * Test validity of the email.
     * 
     * @param email The tested email.
     * @return True, if and only if the value is valid.
     */
    public boolean validEmail(String email) {
        return email == null || !email.isBlank();
    }

    /**
     * Set the email of the pilot.
     * 
     * @param email The new email of the pilot.
     * @throws IllegalArgumentException The given email is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setEmail(String email) {
        if (validEmail(email)) {
            if (this.isIncomplete()) {
                this.email_ = email;
            } else {
                throw new IllegalStateException("Cannot change the value after consruction");
            }
        } else {
            throw new IllegalArgumentException(INVALID_PILOT_IDENTIFIER_MESSAGE);
        }
    }

    /**
     * Set the email of the pilot.
     * 
     * @param email The new email of the pilot.
     * @throws IllegalArgumentException The given email is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setEmail(JsonValue email) throws IllegalArgumentException, IllegalStateException {
        this.setEmail(email == null ? null : email.toString());
    }

    /**
     * Get the phone number of the pilot.
     * 
     * @return The phone number of the pilot.
     */
    public String getPhoneNumber() {
        return this.phone_;
    }

    /**
     * Test validity of the phone number.
     * 
     * @param phoneNumber The tested phone number.
     * @return True, if and only if the value is valid.
     */
    public boolean validPhoneNumber(String phoneNumber) {
        return phoneNumber == null || !phoneNumber.isBlank();
    }

    /**
     * Set the phone number of the pilot.
     * 
     * @param phoneNumber The new phone number of the pilot.
     * @throws IllegalArgumentException The given phone number is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setPhoneNumber(String phoneNumber) {
        if (validPhoneNumber(phoneNumber)) {
            if (this.isIncomplete()) {
                this.phone_ = phoneNumber;
            } else {
                throw new IllegalStateException("Cannot change the value after consruction");
            }
        } else {
            throw new IllegalArgumentException(INVALID_PHONE_NUMBER_MESSAGE);
        }
    }

    /**
     * Set the first name of the pilot.
     * 
     * @param phoneNumber The new first name of the pilot.
     * @throws IllegalArgumentException The given first name is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setPhoneNumber(JsonValue phoneNumber) throws IllegalArgumentException, IllegalStateException {
        this.setPhoneNumber(phoneNumber == null || phoneNumber.toString().isBlank() ? null : phoneNumber.toString());
    }

    /**
     * Get the idenifier of the pilot.
     * 
     * @return The pilot identifier number.
     */
    public String getPilotId() {
        return this.pilotId_;
    }

    /**
     * Test validity of the pilot identifier.
     * 
     * @param pilotId The pilot identifier.
     * @return True, if and only if hte pilot identifier is valid.
     */
    public boolean validPilotId(String pilotId) {
        return pilotId != null && !pilotId.isBlank();
    }

    /**
     * Set the pilot identifier.
     * 
     * @param pilotId The new pilot identifier.
     * @throws IllegalArgumentException The given pilot identifier is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setPilotId(String pilotId) {
        if (validPilotId(pilotId)) {
            if (this.isIncomplete()) {
                this.pilotId_ = pilotId;
            } else {
                throw new IllegalStateException("Cannot change the value after consruction");
            }
        } else {
            throw new IllegalArgumentException(INVALID_PILOT_IDENTIFIER_MESSAGE);
        }
    }


    /**
     * Set the pliot identifier.
     * 
     * @param pilotId The new pilot identifier.
     * @throws IllegalArgumentException The given pilot identifier is invalid.
     * @throws IllegalStateException The built pilot does not support change of the value.
     */
    public void setPilotId(JsonValue pilotId) throws IllegalArgumentException, IllegalStateException {
        this.setPilotId(pilotId == null ? null : pilotId.toString());
    }

    /**
     * Get the expiration minutes.
     * 
     * @return The number of minutes the pilot data stays valid without any
     *  sensor contacts.
     */
    public int getExpirationMinutes() {
        return DEFAULT_EXPIRATION_TIMEOUT;
    }

    public void setDroneSerial(String droneSerial) {
    }

    public void setViolationTime(ZonedDateTime violationTime) {
    }

    public boolean isValid() {
        return false;
    }
}

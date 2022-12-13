import java.time.ZonedDateTime;

/**
 * Pilot contains the pilot information.
 */
public class Pilot {
    
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
     * The name of the pilot.
     */
    private String name_;

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
        this.droneSerialNumber_ = droneSerial;
        this.distance_ = distanceToNest;
        this.name_ = name;
        this.email_ = email;
        this.phone_ = phoneNumber;
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
            if (!validDroneSerial(getDroneSerialNumber())) throw new IllegalArgumentException("Invalid drone sarial number");
            if (!validClosestDistanceToNest(getClosestDistanceToNest())) throw new IllegalArgumentException("Invalid distance to nest");
            if (!validExpireTime(getExpireTime())) throw new IllegalArgumentException("Pilot has no expiration time or the time is in future");
            
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
    public synchronized void setDistance(double distanceToNest) {
        if (distanceToNest < getClosestDistanceToNest()) {
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
        return this.name_;
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
     * Get the phone number of the pilot.
     * 
     * @return The phone number of the pilot.
     */
    public String getPhoneNumber() {
        return this.phone_;
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

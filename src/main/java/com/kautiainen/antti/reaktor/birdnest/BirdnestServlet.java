package com.kautiainen.antti.reaktor.birdnest;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

/**
 * The servlet performing the generation of the requests.
 */
@WebServlet("/BirdNest")
public class BirdnestServlet extends HttpServlet {

    /**
     * The default report data host.
     */
    public static final String DEFAULT_REPORT_DATA_HOST = DronesDataSource.DEFAULT_DRONE_REPORT_HOST;

    /**
     * The default drone data report rest resource path.
     */
    public static final String DEFAULT_REPORT_DATA_PATH = DronesDataSource.DEFAULT_DRONE_REPORT_REST_PATH;

    /**
     * Default pilot data rest path without parameters.
     */
    public static final String DEFAULT_PILOT_DATA_RESOURCE_PATH = "/birdnest/pilots/";

    /*
     * Default pilot data host.
     */
    public static final String DEFAULT_PILOT_DATA_HOST = DEFAULT_REPORT_DATA_HOST;

    /**
     * The violating pilots list. This list is modified during updates.
     */
    private volatile java.util.List<Pilot> violatingPilots = new ArrayList<>();

    /**
     * The data source.
     */
    private DronesDataSource source_ = null;

    /**
     * The thread updating the data.
     */
    private DataUpdaterThread updater_ = null;

    /**
     * The loader of the pilots.
     */
    public PilotLoader pilotLoader = new PilotLoader(DEFAULT_REPORT_DATA_HOST, DEFAULT_PILOT_DATA_RESOURCE_PATH);

    /**
     * Remove expired pilots.
     * 
     * @param now THe current time.
     */
    public synchronized void removeExpiredPilots(ZonedDateTime now) {
        java.util.Iterator<Pilot> pilotIterator = getViolatingPilots().iterator();
        while (pilotIterator.hasNext()) {
            Pilot pilot = pilotIterator.next();
            if (!pilot.isValid(now)) {
                // Removing the expired pilot.
                pilotIterator.remove();
            }
        }
    }

    /**
     * Add violating pilot.
     * 
     * @param pilot The violating pilot.
     * @throws IllegalArgumentException The illegal argument exception.
     */
    protected synchronized void addViolatingPilot(Pilot pilot) throws IllegalArgumentException {
        if (pilot != null) {
            getViolatingPilots().add(pilot);
        } else {
            throw new IllegalArgumentException("Undefined pilot cannot be added");
        }
    }

    /**
     * Get the violating pilots.
     * 
     * @return The collecttion of the violating pilots.
     */
    private synchronized Collection<Pilot> getViolatingPilots() {
        return this.violatingPilots;
    }

    /**
     * Pilot data updater updates the pilot data.
     */
    public class PilotDataUpdater extends Thread {

        public PilotDataUpdater() {

        }

        private ZonedDateTime lastUpdate_ = null;

        private volatile boolean goOn_ = true;

        @Override
        public void run() {
            java.util.List<Element> violatingDrones = null;
            while (goOn_) {
                ZonedDateTime dataUpdate = source_.getUpdateTime();
                synchronized (source_) {
                    if (dataUpdate != null && (lastUpdate_ == null || dataUpdate.isAfter(lastUpdate_))) {
                        // Updating the pilot data.
                        violatingDrones = source_.getMatchingDroneNodes();
                    } else {
                        // No vioalting drones was found.
                        violatingDrones = null;
                    }
                }

                if (violatingDrones == null) {
                    // Sleeping for a quarter second before checking again if pilot information
                    // should be updated.
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                    }

                } else {
                    // Updating pilot data for violating drones - and removing drones which havaen't violated the area for 10 minutes.
                    java.util.List<Element> droneList = null;
                    ZonedDateTime violationTime = null; 

                    // Getting information changing on the data source before using persisting methods.
                    synchronized (source_) {
                        violationTime = source_.getUpdateTime();
                        droneList  = source_.getMatchingDroneNodes();
                    }

                    // Performing updates and additions of new pilots to the violating pilots.
                    if (droneList != null) {
                        synchronized (violatingPilots) {
                            for (Element droneNode: droneList) {
                                String serial = source_.getDroneSerialNumber(droneNode);
                                if (serial != null) {
                                    double distance = source_.getDroneDistanceToNest(droneNode);
                                    java.util.Optional<Pilot> dronePilot = violatingPilots.stream().filter((Pilot pilot) -> (
                                        pilot.getDroneSerialNumber() == serial)).findAny();
                                    if (dronePilot.isPresent()) {
                                        // Updating the pilot.
                                        Pilot pilot = dronePilot.get();
                                        pilot.setClosestDistanceToNest(distance);
                                        pilot.setExpireTime(pilot.updateExpireTime(violationTime));
                                    } else {
                                        // Creating new pilot.
                                        String name, email, phone;

                                        // Get the pilot information.
                                        // TODO: REST JSON query.
                                        try {
                                            Pilot pilot = pilotLoader.getPilot(serial, violationTime, distance);
                                            violatingPilots.add(pilot);
                                        } catch (NullPointerException | IOException exception) {
                                            // The loadign of the pilot failed.
                                            log("Loading of the pilot failed", exception);
                                        } catch (IllegalArgumentException e) {
                                            // The URI was invalid - reporting error.
                                            // TODO: add escaping of serial.
                                            log("Serial of the drone was not suitable: " + serial, e);
                                            name = "[Pilot of " + serial + "]";
                                            email = null;
                                            phone = null;
                                            violatingPilots.add(new Pilot(serial, violationTime, distance, name, email, phone));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Rooting out expired drones.
                removeExpiredPilots(ZonedDateTime.now());
            }
    }

        /**
         * Shutdown the thread.
         */
        public synchronized void shutdown() {
            this.goOn_ = false;
        }

    }

    /**
     * The thread performing pilot data updating.
     */
    private PilotDataUpdater pilotInformation_ = this.new PilotDataUpdater();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public BirdnestServlet() {
        super();
    }

    @Override
    public void destroy() {
        // Stopping the updater thread.
        updater_.shutDown();
        pilotInformation_.shutdown();

        // Calling superclass to release its resources.
        super.destroy();
    }

    @Override
    public void init() throws ServletException {
        // First call super class initialization.
        super.init();

        // Starting the updater thread.
        updater_.start();
        pilotInformation_.start();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
            
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        out.append("<!-- Served at:").append(request.getContextPath()).append("-->");        
        outputViolatingPilotTable(out);
    }

    /**
     * Output vioalting pilots. 
     * 
     * @param request
     * @param out
     */
    public void outputViolatingPilotTable(PrintWriter out) {
        out.append("<table>");
        out.append("<caption>DMZ violating drone pilots</caption>");
        out.append("<tr>");
        out.append("<th>Pilot name</th><th>Email Address</th><th>Phone number</th><th>Closest distance to nest (mm)</th>");
        out.append("</tr>");
        synchronized (violatingPilots) {
            for (Pilot pilot: violatingPilots) {
                out.append("<tr>");
                for (Object data: Arrays.asList(pilot.getName(), pilot.getEmail(), pilot.getPhoneNumber(), pilot.getClosestDistanceToNest())) {
                    out.append("<td>");
                    out.append(data.toString());
                    out.append("</td>");
                }
                out.append("</tr>");
            }
        }
        out.append("</table>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

}

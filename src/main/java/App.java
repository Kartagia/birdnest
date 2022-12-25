import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.validation.constraints.NotNull;

import org.w3c.dom.Element;

/**
 * The main application on server side performing the update of drones.
 */
public class App {

    /**
     * Data source of drone data.
     */
    private volatile static DronesDataSource source;
    /**
     * Output the drone list.
     * 
     * @param output The stream into which the printing is performed.
     * @param drones The printed drone elements as elements.
     * @throws IOException The operation failed due Output Exception.
     */
    public static void printDroneList(@NotNull PrintWriter output, @NotNull List<Element> drones) throws IOException {
        synchronized (source) {
            output.println("Drone list at " + source.getUpdateTime());
            for (Element element : drones) {
                output.print("Drone: ");
                output.print(String.join("; ", java.util.Arrays.asList(
                        source.getDroneSerialNumber(element),
                        Double.toString(source.getDroneDistanceToNest(element)),
                        String.format("X: %d", source.getDroneXPosition(element)),
                        String.format("Y: %d", source.getDroneYPosition(element)),
                        String.format("Altitude: %d", source.getDroneZPosition(element)))));
                output.println();
            }
        }
    }

    /**
     * The main app starting server acquiring data from
     * 
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        try {
            App.source = new DronesDataSource(
                "https://assignments.reaktor.com/birdnest/drones/");
        } catch (IllegalStateException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (URISyntaxException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    

        Map<String, Pilot> pilotRegistry = new TreeMap<>();

        DataUpdaterThread updater = new DataUpdaterThread(App.source);
        updater.start();
        PilotDataUpdater pilotDataUpdater = new PilotDataUpdater(source, pilotRegistry, new PilotLoader(
                BirdnestServlet.DEFAULT_PILOT_DATA_HOST,
                BirdnestServlet.DEFAULT_PILOT_DATA_RESOURCE_PATH));
        // pilotDataUpdater.start();

        // The loop performing the servlet.
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            final PrintWriter writer = new PrintWriter(System.out);

            Thread droneOutputter = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        App.printDroneList(writer, source.getMatchingDroneNodes());
                    } catch (IOException e) {
                        // The output failed.
                        System.getLogger(App.class.getName()).log(Level.ERROR,
                                "Printing drones failed due exception: %s\nStack trace: %s", e.getClass(),
                                e.getStackTrace());
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        System.getLogger(App.class.getName()).log(Level.ERROR, "Drone outputter interrupted %s",
                                e.getMessage());
                    }
                }

            });
            droneOutputter.start();

            String line = reader.readLine();
            boolean goOn = true;
            while (line != null && goOn) {
                line = line.trim();
                if ("quit".equals(line) || "q".equals(line)) {
                    goOn = false;
                    System.out.println("Quitting...");
                } else if ("status".equals(line)) {
                    // Printing status.
                    writer.println("Status: \n");
                    writer.println("Drone outputter Live Threads " + droneOutputter.activeCount());
                    writer.println("Data updater Live Threads " + updater.activeCount());
                } else {
                    // Showing list of violating drones.
                    writer.println("Outputting the drone list on :" + ZonedDateTime.now());
                    java.util.List<Element> violatingDrones = source.getMatchingDroneNodes();
                    printDroneList(writer, violatingDrones);
                }
                line = reader.readLine();
            }
        } catch (java.io.IOException inputError) {
            inputError.printStackTrace();
        }
    }

    /**
     * DroneaReader reads drone list and updats the map of violating pilots.
     */
    public static class PilotDataUpdater extends Thread {

        /**
         * The map storing pilots of violations.
         */
        private final Map<String, Pilot> pilotRegistry;

        /**
         * The data source of the reader.
         */
        private final DronesDataSource source_;

        /**
         * The pilot data loader.
         */
        private final PilotLoader loader_;

        /**
         * The running status of the pilot registry.
         */
        private volatile boolean running = true;

        /**
         * Create new randomizer.
         */
        private java.util.Random randomizer_ = new java.util.Random();

        /**
         * Create new drone reader.
         * 
         * @param source        The report source.
         * @param pilotRegistry The registry of pilots.
         * @param loader        The pilot data loader.
         */
        public PilotDataUpdater(@NotNull DronesDataSource source, @NotNull Map<String, Pilot> pilotRegistry,
                @NotNull PilotLoader loader) {
            this.source_ = source;
            this.loader_ = loader;
            this.pilotRegistry = pilotRegistry;
        }

        /**
         * Get the data source.
         * 
         * @return The data source of the thread.
         */
        public @NotNull DronesDataSource getDataSource() {
            return source_;
        }

        /**
         * The main program of the thread reading drone data, and updating
         * the pilot data. The program checks the report change, and in case of
         * update, updates the pilot data accordingly. It does also purge the expired
         * pilots.
         */
        @Override
        public void run() {
            DronesDataSource source = getDataSource();
            ZonedDateTime updateTime = null;
            ZonedDateTime expireTime = null;
            long expireInterval;
            java.util.List<Element> violatingDrones = Collections.emptyList();
            java.util.List<Element> allDrones = Collections.emptyList();
            synchronized (source) {
                updateTime = source.getUpdateTime();
                expireInterval = source.getUpdateInterval();
                violatingDrones = source.getMatchingDroneNodes();
                allDrones = source.getDroneNodes();
            }
            source.getUpdateTime().plusMinutes(source.getUpdateInterval());

            // The actual execution.
            while (running) {
                // Testing whether we have new data or not.
                if (expireTime == null || !ZonedDateTime.now().isBefore(expireTime)) {
                    // Getting new drone data as the current data has been expired.
                    synchronized (source) {
                        updateTime = source.getUpdateTime();
                        expireInterval = source.getUpdateInterval();
                        violatingDrones = source.getMatchingDroneNodes();
                        allDrones = source.getDroneNodes();
                    }
                }

                // Synchronizing the pilot registry.
                synchronized (pilotRegistry) {
                    // Handling new data, if it is available.
                    if (updateTime == null || expireTime != null && expireTime.compareTo(updateTime) <= 0) {
                        // We do have new data.
                        handleViolations(source, updateTime, violatingDrones);

                        // Refreshing the expire time of all detected drones in the registry.
                        updatePilotDetectionTimes(updateTime, allDrones.stream()
                                .map((Element element) -> (source.getDroneSerialNumber(element))).toList());

                        // Updating expire time of the thread.
                        expireTime = updateTime.plusMinutes(expireInterval);
                    }

                    // Purging old pilot data
                    purgeExpiredPilots();
                }

                // Sleeping before next run.
                try {
                    sleep(getSleepTime());
                } catch (InterruptedException e) {
                    // Sleep ended.
                }
            }

        }

        /**
         * Updates the detection times of the pilots.
         * 
         * @param updateTime   The update time.
         * @param droneSerials The detected drone serials.
         */
        protected synchronized void updatePilotDetectionTimes(@NotNull ZonedDateTime updateTime,
                @NotNull java.util.List<String> droneSerials) {
            for (String serial : droneSerials) {
                if (pilotRegistry.containsKey(serial)) {
                    pilotRegistry.get(serial).updateExpireTime(updateTime);
                }
            }
        }

        /**
         * Handle DMZ violating drones.
         * 
         * @param source          The data source.
         * @param updateTime      Teh update time of the violation.
         * @param violatingDrones The violating drones.
         */
        protected synchronized void handleViolations(DronesDataSource source, ZonedDateTime updateTime,
                java.util.List<Element> violatingDrones) {
            // Adding new pilots and updating the distance.
            for (Element element : violatingDrones) {
                String serial = source.getDroneSerialNumber(element);
                double distance = source.getDroneDistanceToNest(element);
                if (pilotRegistry.containsKey(serial)) {
                    // Updating distance - the expire time is updated along with all drones update.
                    Pilot pilot = pilotRegistry.get(serial);
                    if (pilot.getClosestDistanceToNest() > distance) {
                        pilot.setClosestDistanceToNest(distance);
                    }
                } else {
                    // Creating new pilot data.
                    Pilot pilot;
                    try {
                        pilot = loader_.getPilot(serial, updateTime, distance);
                    } catch (IllegalArgumentException | IOException e) {
                        // Logging error and creating stub pilot
                        System.getLogger(App.class.getName()).log(Level.ERROR, "Could not get pilot info: ",
                                e.getMessage());
                        pilot = new Pilot(serial, updateTime, distance, "[Drone:" + serial + "]", null,
                                null);
                    }
                    pilotRegistry.put(serial, pilot);
                }
            }
        }

        /**
         * Removes all expired pilots from the pilot registry.
         */
        protected synchronized void purgeExpiredPilots() {
            Iterator<Map.Entry<String, Pilot>> iterator = pilotRegistry.entrySet().iterator();
            Pilot pilot;
            while (iterator.hasNext()) {
                pilot = iterator.next().getValue();
                if (!pilot.isValid()) {
                    iterator.remove();
                }
            }
        }

        /**
         * The sleep time of the thread. The implemetnation does use random element in
         * the timer choosing
         * random time between 100 and 199 milliseconds.
         * 
         * @return The sleep time of the tread. This number has to be greater than 0.
         */
        protected int getSleepTime() {
            return 100 + randomizer_.nextInt(100);
        }

        /**
         * Shutdown the thread causing it to cease execution at the first possible
         * occasion.
         */
        public synchronized void shutdown() {
            this.running = false;
        }
    }
}

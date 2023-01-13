package com.kautiainen.antti.reaktor.birdnest;
import java.io.IOException;
import java.time.ZonedDateTime;

import org.xml.sax.SAXException;

/**
 * The thread performing drone data updating.
 * It does wait to the next update time of the data, after successful
 * update, or various random timers on failed read.
 */
public class DataUpdaterThread extends Thread {

    /**
     * The default update innterval in milliseconds.
     */
    public static long DEFAULT_UPDATE_INTERVAL_MS = 2000;

    /**
     * The update interval in milliseconds.
     */
    private long updateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;

    /**
     * The data of the sensor.
     */
    private volatile DronesDataSource data_;

    /**
     * The most recent update time of the current updater.
     */
    private ZonedDateTime updateTime_ = null;

    /**
     * By default the thread is running.
     * When this value is set false, the execution of the tread will end.
     */
    private boolean goOn_ = true;

    /**
     * Createa new data updater with a data source.
     * 
     * @param dataSource The data source.
     * @throws IllegalArgumentException The given source was invalid.
     */
    public DataUpdaterThread(DronesDataSource dataSource) throws IllegalArgumentException {
        if (dataSource == null) {
            throw new IllegalArgumentException("Undefined data is not accepted");
        }
        this.data_ = dataSource;
    }


    @Override
    public void run() {
        // The thread updating the drones data.
        int retries = 0;
        int retryThreshold = 5;
        boolean retry = false;
        while (goOn_) {
            try {
                // The input
                // Update the data.
                data_.update();

                // Updating the update timer.
                setUpdateTime(data_.getUpdateTime());
                retries = 0;
            } catch (IOException ioe) {
                // The input error causes retry to acquire the doc.
                retry = true;
            } catch (IllegalStateException e) {
                // Log the illegal state exception.
                retry = true;
            } catch (SAXException e) {
                retry = true;
            }

            // Performing retry after waiting a while.
            if (retry && retries < retryThreshold) {
                // Retry due failed upate - retrying after the retry count in milliseconds.
                retries++;
                try {
                    // waiting time depends on retries.
                    sleep(retries);
                } catch (InterruptedException interrupted) {
                    // The wait was interrupted.
                    this.interrupt();
                }
            } else {
                // Calculating delay to the next update.
                ZonedDateTime updateMoment = getUpdateTime();
                if (updateMoment != null) {
                    try {
                        // Perform default sleep time to the update interval.
                        sleep(this.getUpdateInterval());
                    } catch (InterruptedException e) {
                    }
                } else {
                    // The update moment is undefined.
                    try {
                        // Perform default sleep of 100 ms.
                        sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

    }

    /**
     * Set the most recent update time.
     * 
     * @param updateTime The update time.
     * @throws IllegalArgumentException The given update time is either
     *                                  undefined, or happens after current time.
     */
    private synchronized void setUpdateTime(ZonedDateTime updateTime)
            throws IllegalArgumentException {
        if (updateTime == null || ZonedDateTime.now().isBefore(updateTime)) {
            throw new IllegalArgumentException("Invalid update time");
        }
        this.updateTime_ = updateTime;
    }

    /**
     * Get the most recetn update time.
     * 
     * @return The update time of the drones data.
     */
    public synchronized ZonedDateTime getUpdateTime() {
        return this.updateTime_;
    }

    /**
     * Get the update interval in milliseconds.
     * 
     * @return The update interval in milliseconds.
     */
    private long getUpdateInterval() {
        return this.updateIntervalMs;
    }

    /**
     * Shut down the thread.
     */
    public synchronized void shutDown() {
        this.goOn_ = false;
    }

}
/**
 * The main application on server side performing the update of drones.
 */
public class App {
    
    /**
     * The main app starting server acquiring data from 
     * @param args The command line arguments.
     */
    public static void main(String[] args) {

        DataUpdaterThread updater = new DataUpdaterThread(new DataSource());
        updater.start();

        // The loop performing the servlet.

    }
}

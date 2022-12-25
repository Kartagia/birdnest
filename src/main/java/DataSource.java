import java.util.Collections;
import java.util.function.Supplier;

import javax.validation.constraints.NotNull;

/**
 * Data source is a data source acquiring data. 
 * 
 * <p>The implementor of data source can assume that the {@link #getData()} is excuted with 
 * data source object synchronized for that data acquisition alone.</p>
 * 
 * @param <TYPE> The type of the acquired data.
 */
public abstract class DataSource<TYPE> implements Supplier<TYPE> {

    /**
     * DataSourceExcepction indicates the data source was not able to acquire the 
     * data due an exception. 
     */
    public static class DataSourceException extends Exception {
        /**
         * Create a new data source exception with a message and no cause. 
         * 
         * @param message The message of the exception.
         */
        public DataSourceException(String message) {
            this(message, null);
        }

        /**
         * Create a new data source exception with a message and cause. 
         * @param message The message of the exception.
         * @param cause The cause of the exception.
         */
        public DataSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception handler hanlding exceptions.
     */
    public static interface ExceptionHandler {

        /**
         * Handles the given exception. 
         * @param e The handled exception.
         * 
         * @return Did the handler process the exception.
         */
        default boolean handleException(@NotNull Exception e) {
            return false;
        }
    }

    /**
     * Specific excpetion handled which handles exceptions of given type.
     * The default implementation handles all exceptions of the given type.
     * 
     * @param <TYPE> The type of the handled exception.
     */
    public static interface SpecificExceptionHandler<TYPE extends Exception> extends ExceptionHandler {

        @Override
        @SuppressWarnings("unchecked")
        default boolean handleException(@NotNull Exception e) {
            try {
                // The value is processed as it is specific type.
                return ((TYPE)e != null);
            } catch(ClassCastException cce) {
                // The value was not processed.
                return false;
            }
        }
    }

    /**
     * Handler handling exceptions caused by the data source. The implementation of this class 
     * should handle all DataSourceExceptions.
     * 
     * The data source always return <code>Optional.empty()</code> after reporting
     * an exception to all handlers.
     */
    public static interface DataSourceExceptionHandler extends SpecificExceptionHandler<DataSourceException> {

        @Override
        default boolean handleException(@NotNull Exception e) {
            if (SpecificExceptionHandler.super.handleException(e)) {
                handleDataSourceException((DataSourceException)e);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Handle the data source exception.
         * 
         * @param exception The handled exception.
         */
        public void handleDataSourceException(DataSourceException exception);
    }

    /**
     * The exceptio handlers handling all exceptions.
     */
    private java.util.List<ExceptionHandler> exceptionHandlers_ = new java.util.ArrayList<>();

    /**
     * Get the exception hanlders.
     * 
     * @return An unmodifiable collection contaiing all exception handlers.
     */
    protected java.util.Collection<ExceptionHandler> getExceptionHandlers() {
        return Collections.unmodifiableCollection(this.exceptionHandlers_);
    }


    /**
     * Add data source exception handler, if it does not already belong to the handlers.
     * 
     * @param handler The added data source exception handler.
     */
    public synchronized void addExceptionHandler(@NotNull ExceptionHandler handler) {
        if (!exceptionHandlers_.contains(handler)) {
            exceptionHandlers_.add(handler);
        }
    }

    /**
     * Remove an exception handler, if it does belong to the handlers.
     * 
     * @param handler The removed data source exception handler.
     */
    public synchronized void removeExceptionHandler(@NotNull ExceptionHandler handler) {
        exceptionHandlers_.remove(handler);
    }

    /**
     * Fires given exception to the exception handlers.
     * 
     * @param exception The fired exception.
     * @param override Does the exception end when first handler has handled the exception.
     */
    protected synchronized void fireException(Exception exception, boolean override) {
        if (override) {
            for (ExceptionHandler handler : getExceptionHandlers()) {
                if (handler.handleException(exception)) {
                    // Ending the excecution of the exception firing on first handler
                    // consuming the exception.
                    return;
                }
            }
        } else {
            // We can fire the exception to the handlers in any order we want.
            getExceptionHandlers().stream().forEach( (ExceptionHandler handler) -> {handler.handleException(exception); } );
        }
    }

    /**
     * Fires the exception to all exception handlers.
     * 
     * @param exception The fired exception.
     */
    protected synchronized void fireException(Exception exception) {
        fireException(exception, false);
    }

    /**
     * Create a new data source without any handlers.
     */
    protected DataSource() {

    }

    /**
     * Create a new data source exception handler with given exception handlers.
     * 
     * @param handlers The exception handlers.
     */
    protected DataSource(ExceptionHandler... handlers) {
        if (handlers != null) {
            for (ExceptionHandler handler: handlers) {
                addExceptionHandler(handler);
            }
        }
    }

    /**
     * Create a new data source exception handler with given exception handlers.
     * 
     * @param handlers The exception handlers.
     */
    protected DataSource(java.util.Collection<? extends ExceptionHandler> handlers) {
        if (handlers != null) {
            for (ExceptionHandler handler: handlers) {
                addExceptionHandler(handler);
            }
        }
    }


    /**
     * Get the resource value. If there is any exception during the handling, the 
     * method should fire exception to the exception handlers, and return empty value.
     * 
     * @return The optional value, if it is available. If the value is not available, 
     * an empty value should be returned.
     */
    protected abstract java.util.Optional<TYPE> getData();

    @Override
    public synchronized TYPE get() {
        return getData().orElse(null);
    }
}

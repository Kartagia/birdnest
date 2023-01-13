package com.kautiainen.antti.reaktor.birdnest.data;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StreamCorruptedException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.validation.constraints.NotNull;

/**
 * DataSource using HTTP calls to fetch the data from data URL.
 * DataSource uses a parser function reading input stream and converting
 * it to the value of the wanted type.
 */
public class HttpDataSource<TYPE> extends NetworkDataSource<TYPE> {

    /**
     * StatusHandler handles status exceptions.
     */
    public static interface StatusHandler<TYPE> {

        /**
         * Handle the given http response.
         * 
         * @param response The response from server.
         * @return The value of the handled response.
         * @throws IOException The exception thrown, if the response indicates input or
         *                     output error.
         */
        default Optional<TYPE> handleStatus(@NotNull HttpResponse<? extends InputStream> response)
                throws IOException {
            return handleStatus(response.statusCode(), response.headers(), response.body());
        }

        /**
         * Handle the status with a status and a message body.
         * 
         * @param status      The status handler.
         * @param messageBody The message body in input stream.
         * @return If the status handling returns valid value of type, the returned
         *         value. Otherwise,
         *         returns an empty value.
         * @throws IOException The status handling caused IOException.
         */
        default Optional<TYPE> handleStatus(int status, InputStream messageBody)
                throws IOException {
            return handleStatus(status, null, messageBody);
        }

        /**
         * Handle the status with a status, headers, and message body.
         * 
         * @param status      The status handler.
         * @param headers     The http headers of the response.
         * @param messageBody The message body in input stream.
         * @return If the status handling returns valid value of type, the returned
         *         value. Otherwise,
         *         returns an empty value.
         * @throws IOException The status handling caused IOException.
         */
        default Optional<TYPE> handleStatus(int status, HttpHeaders headers, InputStream messageBody)
                throws IOException {
            return Optional.empty();
        }
    }

    /**
     * Cahiend Status handler chains several status handlers after each other using next handler when the previous one
     * does not hanlde the result.
     * 
     * @param <TYPE> The return type of the chained handler.
     */
    public static class ChainedStatusHandler<TYPE> implements StatusHandler<TYPE> {

        /**
         * The status handlers of this chained status handler. 
         */
        private final java.util.LinkedList<StatusHandler<? extends TYPE>> handlers = new java.util.LinkedList<>();

        /**
         * Create new chained status handler with given list of handlers.
         * 
         * @param handlers The handlers haldiing hte status messages. The first message returning a present value is returned.
         */
        @SafeVarargs
        public ChainedStatusHandler(final StatusHandler<? extends TYPE>... handlers) {
            for (StatusHandler<? extends TYPE> handler: handlers) {
                if (handler != null)  {
                    this.handlers.addLast(handler);
                }
            }
        }

        public ChainedStatusHandler(StatusHandler<? extends TYPE> first, StatusHandler<? extends TYPE> next) {
            if (first != null) {
                this.handlers.addLast(first);
            } 
            
            if (next != null) {
                this.handlers.addLast(next);
            }
        }

        @Override
        public Optional<TYPE> handleStatus(int status, HttpHeaders headers, InputStream messageBody)
                throws IOException {
            Optional<? extends TYPE> result;
            for (StatusHandler<? extends TYPE> handler: handlers) {
                result = handler.handleStatus(status, headers, messageBody);
                if (result.isPresent()) {
                    // Converting the reuslt to the value.
                    return Optional.of(result.get());
                }
            }

            // Returning the last empty value.
            return Optional.empty();
        }

        
    }

    /**
     * The status handler handling the error status messages of the response.
     */
    private StatusHandler<? extends TYPE> statusHandler_;

    /**
     * Create a new http data source with given URI and reader function.
     * 
     * @param uri           The URI of the data source.
     * @param parseFunction The parser function parsing the message body into wanted
     *                      data type.
     * @throws IllegalArgumentException Any parameter was invalid.
     */
    public HttpDataSource(URI uri, Function<? super InputStream, ? extends TYPE> parseFunction)
            throws IllegalArgumentException {
        this(uri, parseFunction, null);
    }

    /**
     * Create a new http data source with given URI and reader function.
     * 
     * @param uri           The URI of the data source.
     * @param parseFunction The parser function parsing the message body into wanted
     *                      data type.
     * @throws IllegalArgumentException
     */
    public HttpDataSource(URI uri, Function<? super InputStream, ? extends TYPE> parseFunction,
            StatusHandler<? extends TYPE> statusErrorHandler)
            throws IllegalArgumentException {
        super(uri, parseFunction);
        this.statusHandler_ = statusErrorHandler;
    }

    /**
     * Create a new http data source with given URI and reader function.
     * 
     * @param uri           The URI of the data source.
     * @param parseFunction The parser function parsing the message body into wanted
     *                      data type.
     * @throws IllegalArgumentException
     */
    public HttpDataSource(String uri, Function<? super InputStream, ? extends TYPE> parseFunction)
            throws IllegalArgumentException, URISyntaxException {
        this(new URI(uri), parseFunction);
    }

    /**
     * Create a new Http Data Source with given "https" schema, a host, a path and a
     * parse function.
     * 
     * @param host          The host of the URI.
     * @param path          The path of the.
     * @param parseFunction The function parsing the message body into wanted
     *                      object.
     * @throws IllegalArgumentException Any argumen was invalid.
     * @throws URISyntaxException       The given host and were invalid for URI.
     */
    public HttpDataSource(String host, String path, Function<? super InputStream, ? extends TYPE> parseFunction)
            throws IllegalArgumentException, URISyntaxException {
        this(new URI("https", host, path, null), parseFunction);
    }

    /**
     * Create a new Http Data Source with a uri, a parse function, and a response
     * handler.
     * 
     * @param uri             The uri of the network source.
     * @param parseFunction   The parse function parsing the result body.
     * @param responseHandler The handler handling all status messages but 200 and
     *                        404.
     * @throws IllegalArgumentException The given URI was invalid.
     */
    public HttpDataSource(String uri, Function<InputStream, TYPE> parseFunction,
            @NotNull StatusHandler<? extends TYPE> responseHandler)
            throws IllegalArgumentException {
        this(URI.create(uri), parseFunction, responseHandler);
    }

    /**
     * Does the http data soruce have status handler.
     * 
     * @return Does the status handler exist.
     */
    public boolean hasStatusHandler() {
        return this.statusHandler_ != null;
    }

    /**
     * Get the status handler.
     * 
     * @return The status handler handling all status messages exception obviously
     *         handled ones (Ok, Not Found, redirects).
     */
    public StatusHandler<? extends TYPE> getStatusHandler() {
        return this.statusHandler_;
    }

    /**
     * The get request with given parameters.
     * 
     * @param parameters The parameters of the call.
     * @return The GET method requrest using givne parameters. 
     * @throws IllegalArgumentException The parameters were invalid. 
     */
    protected HttpRequest getGetRequest(List<?> parameters)
            throws IllegalArgumentException {
        return HttpRequest.newBuilder(getSourceURI(parameters == null ? Collections.emptyList() : parameters)).GET()
                .build();
    }

    /**
     * The get request with given parameters.
     * 
     * @param parameters The parameters of the call.
     * @return The GET method requrest using givne parameters. 
     * @throws IllegalArgumentException The parameters were invalid. 
     */
    protected HttpRequest getGetRequest(Map<String, ?> parameters)
            throws IllegalArgumentException {
        return HttpRequest.newBuilder(getSourceURI(parameters == null ? Collections.emptyMap() : parameters)).GET()
                .build();
    }

    /**
     * The post request with given parameters.
     * 
     * @param parameters The parameters of the call.
     * @return The POST method requrest using givne parameters. 
     * @throws IllegalArgumentException The parameters were invalid. 
     */
    protected HttpRequest getPostRequest(List<?> parameters)
            throws IllegalArgumentException {
        return HttpRequest.newBuilder(getSourceURI(parameters == null ? Collections.emptyList() : parameters)).POST(getBodyPublisher(parameters))
                .build();
    }

    /**
     * The post request with given parameters.
     * 
     * @param parameters The parameters of the call.
     * @return The POST method requrest using givne parameters. 
     * @throws IllegalArgumentException The parameters were invalid. 
     */
    protected HttpRequest getPostRequest(Map<String, ?> parameters)
            throws IllegalArgumentException {
        return HttpRequest.newBuilder(getSourceURI(parameters == null ? Collections.emptyMap() : parameters)).POST(getBodyPublisher(parameters))
                .build();
    }

    /**
     * Get the body publisher for given parameters.
     * 
     * @param parameters The parameters of the call. 
     * @return the body published with given parameters encoded into it. An undefined value, if no such 
     * publisher exists.
     */
    protected BodyPublisher getBodyPublisher(List<?> parameters) {
        return null;
    }

    /**
     * Get the body publisher for given parameters.
     * 
     * @param parameters The parameters of the call in map from parameter name to parameter value.
     * @return the body published with given parameters encoded into it. An undefined value, if no such 
     * publisher exists.
     */
    protected BodyPublisher getBodyPublisher(Map<String, ?> parameters) {
        return null;
    }


    /**
     * Get the response with default method.
     * 
     * @param parameters The parameters of the call.
     * @return The response with input stream.
     * 
     * @throws java.io.IOException  The operation failed due input error.
     * @throws InterruptedException The operation was interruted.
     */
    protected HttpResponse<InputStream> getDataResponse(List<?> parameters)
            throws java.io.IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = getGetRequest(parameters);
        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        return response;
    }

    /**
     * Get the response with default method.
     * 
     * @param parameters The parameters of the call.
     * @return The response with input stream.
     * 
     * @throws java.io.IOException  The operation failed due input error.
     * @throws InterruptedException The operation was interruted.
     */
    protected HttpResponse<InputStream> getDataResponse(Map<String, ?> parameters)
            throws java.io.IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = getGetRequest(parameters);
        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        return response;
    }



    /**
     * Get the source uri by encoding the parameters into URI.
     * 
     * @param parameters The parameters.
     * @return The URI with given parameters encoded into it.
     */
    protected URI getSourceURI(List<?> parameters) {
        try {
            return getSourceURI().resolve(new URI(null, null, getPathEncodedParameters(parameters),
                    getQueryEncodedParameters(parameters), getFragmentEncodedParameters(parameters)));
        } catch (URISyntaxException syntax) {
            throw new IllegalArgumentException("Invalid parameters", syntax);
        }
    }

    /**
     * Get the source uri by encoding the parameters into URI.
     * 
     * @param parameters The parameters.
     * @return The URI with given parameters encoded into it.
     */
    protected URI getSourceURI(Map<String, ?> parameters) {
        try {
            return getSourceURI().resolve(new URI(null, null, getPathEncodedParameters(parameters),
                    getQueryEncodedParameters(parameters), getFragmentEncodedParameters(parameters)));
        } catch (URISyntaxException syntax) {
            throw new IllegalArgumentException("Invalid parameters", syntax);
        }
    }



    /**
     * Get the fragment with given parameters encoded to it.
     * 
     * @param parameters The parameters.
     * @return The fragment part with given parameters encoded. An undefined value, if there is no fragment.
     */
    protected String getFragmentEncodedParameters(final @NotNull List<?> parameters) throws IllegalArgumentException {
        return null;
    }

    /**
     * Get the query with given parameters encoded into it.
     * @param parameters The parameters.
     * @return The query part with given parameters encoded. An undefined value, if there is no query part.
     */
    protected String getQueryEncodedParameters(final @NotNull List<?> parameters) {
        return null;
    }

    /**
     * Get the path with given parameters encoded into it.
     * @param parameters The parameters. 
     * @return The path part with given parameters encoded into it. An undefined value, if there is no path part.
     */
    protected String getPathEncodedParameters(final @NotNull List<?> parameters) {
        return null;
    }

    /**
     * Get the fragment with given parameters encoded to it.
     * 
     * @param parameters The parameters map from parameter name to the parameter value. 
     * @return The fragment part with given parameters encoded. An undefined value, if there is no fragment.
     */
    protected String getFragmentEncodedParameters(final Map<String, ?> parameters) throws IllegalArgumentException {
        return null;
    }

    /**
     * Get the query with given parameters encoded into it.
     * @param parameters The parameters map from parameter name to the parameter value. 
     * @return The query part with given parameters encoded. An undefined value, if there is no query part.
     */
    protected String getQueryEncodedParameters(final Map<String, ?> parameters) {
        return null;
    }

    /**
     * Get the path with given parameters encoded into it.
     * @param parameters The parameters map from parameter name to the parameter value. 
     * @return The path part with given parameters encoded into it. An undefined value, if there is no path part.
     */
    protected String getPathEncodedParameters(final Map<String, ?> parameters) {
        return null;
    }


    /**
     * Get the response without user given parameters.
     * 
     * @return The response with input stream.
     * 
     * @throws java.io.IOException  The operation failed due input error.
     * @throws InterruptedException The operation was interruted.
     */
    protected synchronized HttpResponse<InputStream> getDataResponse() throws java.io.IOException, InterruptedException {
        return getDataResponse((List<?>)null);
    }

    /**
     * Test validity of the given URI.
     * 
     * @param uri The tested uri.
     * @return True, if and only if the given URI is a valid URI for this data
     *         source.
     */
    public boolean validSourceURI(URI uri) {
        return uri != null && Arrays.asList("http", "https", "file").contains(uri.getScheme());
    }

    /** 
     * Get data with given parameters.
     * 
     * @param parameters The parameters of the data request.
     * @eturn The value of the request with given parameters, or an empty value.
     * @throws IllegalArgumentException The given parameters were invalid. 
     */
    protected synchronized Optional<TYPE> getData(List<?> parameters) throws IllegalArgumentException {
        try {
            HttpResponse<InputStream> response = getDataResponse(parameters);
            return handleResponse(response);
        } catch (IOException | InterruptedException exception) {
            // Reporting the error and returning status.
            this.fireException(exception);
            return Optional.empty();
        }
    }

    /** 
     * Get data with given parameters.
     * 
     * @param parameters The parameters of the data request as mapping from parameter name to parameter value. 
     * @eturn The value of the request with given parameters, or an empty value.
     * @throws IllegalArgumentException The given parameters were invalid. 
     */
    protected synchronized Optional<TYPE> getData(Map<String, ?> parameters) throws IllegalArgumentException {
        try {
            HttpResponse<InputStream> response = getDataResponse(parameters);
            return handleResponse(response);
        } catch (IOException | InterruptedException exception) {
            // Reporting the error and returning status.
            this.fireException(exception);
            return Optional.empty();
        }
    }

    @Override
    protected synchronized Optional<TYPE> getData() {
        try {
            HttpResponse<InputStream> response = getDataResponse();
            return handleResponse(response);
        } catch (IOException | InterruptedException exception) {
            // Reporting the error and returning status.
            this.fireException(exception);
            return Optional.empty();
        }
    }

    /**
     * Handles the HPTTP response.If hte handle response returns an undefined value,
     * the error has been fired to the error listeners. By default handles status
     * code of 200 and 204.
     * All other status codes are sent to the status handlers.
     * 
     * @param response The response.
     * @return The return value, if the given response contained valid data to
     *         create
     *         a resulting object. Otherwise an empty value.
     * @throws IOException              The operation failed due Input error.
     * @throws InterruptedException     The operation was interrupted.
     * @throws StreamCorruptedException Tthe stream was corrupted, and did not
     *                                  contain valid
     *                                  data to compose the resulting object.
     */
    protected synchronized Optional<TYPE> handleResponse(HttpResponse<InputStream> response)
            throws IOException, InterruptedException, StreamCorruptedException {
        if (response.statusCode() == 200) {
            // The request passed successfully.
            // - Parsing the response
            InputStream stream = getDataResponse().body();
            Optional<Function<? super InputStream, ? extends TYPE>> parser = Optional.ofNullable(this.getParser());
            if (parser.isPresent()) {
                return Optional.ofNullable(parser.get().apply(stream));
            } else {
                return Optional.empty();
            }
        } else if (response.statusCode() == 204) {
            // No content.
            return Optional.empty();
        } else {
            if (hasStatusHandler()) {
                // Use the given handler to handle the status.
                return Optional.ofNullable(
                        getStatusHandler().handleStatus(response.statusCode(), response.body()).orElse(null));
            } else if (response.statusCode() >= 400 && response.statusCode() < 600) {
                // Handling the status error.
                throw new java.io.StreamCorruptedException(
                        MessageFormat.format("Server responded with error status {0}", response.statusCode()));
            }

            // The status was such it does not need to be handled, and it does not contain a
            // body.
            return Optional.empty();
        }
    }


    /**
     * Get the response with given list of parameters. 
     * 
     * @param parameters The parameters as a list.
     * @return The response result valeu with given parameters.
     * @throws IllegalArgumentException Any parameter was invalid.
     */
    public TYPE get(List<?> parameters) throws IllegalArgumentException {
        return this.getData(parameters).orElse(null);
    }

    /**
     * Get the response with given parameter map.
     * 
     * @param parameters The parameters as a map from parameter name to parameter value.
     * @return The response result valeu with given parameters.
     * @throws IllegalArgumentException Any parameter was invalid.
     */
    public TYPE get(Map<String, ?> parameters) throws IllegalArgumentException {
        return this.getData(parameters).orElse(null);
    }

    /** 
     * Prints input stream to the print stream.
     * 
     * @param in The input stream from which the data is read.
     * @param out The output stream into which the input stream is written.
     * @throws java.io.IOException The operation failed due I/O Exception. 
     */
    public static void printStream(InputStream in, PrintStream out) throws IOException {
        if (in == null) {
            // Nohting to do
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            out.println("Read line: " + line);
        }
    }

    public static void main(String[] args) {

        URI uri = URI.create("http://assignments.reaktor.com/birdnest/drones/");

        HttpDataSource<InputStream> source = new HttpDataSource<>(uri, (InputStream in) -> (in));

        System.out.println("Source URI: " + source.getSourceURI());

        InputStream in = source.getData().orElse(null);
        if (in == null) {
            System.out.println("We got nothing");
        } else {
            try {
                printStream(in, System.out);
                in.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }
}

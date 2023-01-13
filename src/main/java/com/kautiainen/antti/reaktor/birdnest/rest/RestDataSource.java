package com.kautiainen.antti.reaktor.birdnest.rest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.validation.constraints.NotNull;

import com.kautiainen.antti.reaktor.birdnest.data.HttpDataSource;

import java.io.InputStream;

/**
 * RestDataSource is a Http Data source which allows acquiring getting data with
 * added parameters to the URI during data acquisition call.
 */
public class RestDataSource<TYPE> extends HttpDataSource<TYPE> {

    /**
     * Creates a new REST data source with given a base uri to the resource, and a
     * parser function.
     * 
     * @param baseUri        The base uri into whose path argument the parameter
     *                       values are appended.
     *                       The path may be undefined, or path may not end to "/".
     * @param parserFunction The function parsing the body of the http request into
     *                       wanted type.
     */
    public RestDataSource(URI baseUri, Function<? super InputStream, ? extends TYPE> parserFunction) {
        super(baseUri, parserFunction);
    }

    /**
     * Creates a new REST data source with given a base uri to the resource, and a
     * parser function.
     * 
     * @param baseUri        The base uri into whose path argument the parameter
     *                       values are appended.
     *                       The path may be undefined, or path may not end to "/".
     * @param parserFunction The function parsing the body of the http request into
     *                       wanted type.
     * @param restParameter  The rest parameters added after the given base URI.
     */
    public RestDataSource(URI uri, Function<? super InputStream, ? extends TYPE> parserFunction,
            RestParameter<?>... restParameter) {
        this(uri, parserFunction);

        // Adding the rest parameters added to the end of the URI.
        if (restParameter != null)
            // Adding the first resource wihtout resource path.
            this.restPath_.addResource(this.restPath_.new RestResource((URI)null, restParameter));
    }

    /**
     * Create a new rest data source with a base uri, parse funciton, and given resource uri.
     * If the initial resource URI is absolute path, it will replace the path of the given
     * base Uri.
     * 
     * @param baseUri The base uri including the protocol and host, and base path.
     * @param parserFunction The parser function.
     * @param resourceUri The resource uri of the initial resourse.
     * @param restParameters The parameters of the initial resource.
     * @throws IllegalArgumentException
     */
    public RestDataSource(URI baseUri, Function<? super InputStream, ? extends TYPE> parserFunction,
        String resourceUri, RestParameter<?>... restParameters) throws IllegalArgumentException {
        this(baseUri, parserFunction);
        if (restParameters != null || resourceUri != null) {
            URI resourceUI;
            try {
                resourceUI = new URI(null, null, resourceUri, null);
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                throw new IllegalArgumentException("Invalid resource URI", e);
            }
            this.restPath_.addResource(this.restPath_.new RestResource(resourceUI, restParameters));
        }
    }   

    /**
     * Create a new rest data source with a base uri, parse funciton, and given resource uri.
     * If the initial resource URI is absolute path, it will replace the path of the given
     * base Uri.
     * 
     * @param baseUri The base uri including the protocol and host, and base path.
     * @param parserFunction The parser function.
     * @param resourceUri The resource uri of the initial resourse.
     * @param restParameters The parameters of the initial resource.
     * @throws IllegalArgumentException
     */
    public RestDataSource(URI baseUri, Function<? super InputStream, ? extends TYPE> parserFunction,
        URI resourceUri, RestParameter<?>... restParameters) throws IllegalArgumentException {
        this(baseUri, parserFunction);
        if (restParameters != null || resourceUri != null) {
            this.restPath_.addResource(this.restPath_.new RestResource(resourceUri, restParameters));
        }
    }   


    /**
     * The rest path of the source URI.
     */
    private final RestPath restPath_ = new RestPath();

    /**
     * Get the rest path of the rest source.
     * 
     * @return The rest path of the rest data source.
     */
    protected @NotNull RestPath getRestPath() {
        return restPath_;
    }

    /**
     * Does the source have parameter with given name.
     * 
     * @param name the parameter name. 
     * @return True, if and only if the REST path has given parameter.
     */
    public boolean hasRestParameter(String name) {
        return this.getRestPath().getKnownParameterNames().contains(name);
    }

    /**
     * REST restricts the source uri.
     */
    public boolean validSourceURI(URI uri) {
        return uri != null && uri.getQuery() == null && uri.getFragment() == null;
    }

    public URI getSourceURI(List<?> parameters) throws IllegalArgumentException {
        return getSourceURI().resolve(getRestPath().getUri(parameters));
    }

    public URI getSourceURI(Map<String, ?> parameters) throws IllegalArgumentException {
        return getSourceURI().resolve(getRestPath().getUri(parameters));        
    }

}

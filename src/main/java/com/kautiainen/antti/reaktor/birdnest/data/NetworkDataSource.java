package com.kautiainen.antti.reaktor.birdnest.data;
import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.io.IOException;
import java.io.InputStream;

import javax.validation.constraints.NotNull;

/**
 * Class representing network data sources.
 */
public class NetworkDataSource<TYPE> extends DataSource<TYPE> {
    

    /**
     * The base service uri.
     */
    private URI uri_;

    /**
     * The funciton handling parsing.
     */
    private Function<? super InputStream, ? extends TYPE> parser_;

    /**
     * Create a new network data source with a uri, and with no exception handlers.
     * 
     * @param uri The URI used to acquire the data.
     * @param parser The parser parsing the input stream of the URI into data.
     * @throws IllegalArgumentException Given URI was invalid.
     */
    public NetworkDataSource(@NotNull URI uri, Function<? super InputStream, ? extends TYPE> parser) throws IllegalArgumentException {
        super();
        if (!setSourceURI(uri)) {
            throw new IllegalArgumentException("Invalid source URI");
        }
        this.parser_ = parser;
    }

    /**
     * Get the parser performing the parsing of the input stream into wanted type.
     * 
     * @return The parser function parsing input stream into wanted type.
     */
    public @NotNull Function<? super InputStream, ? extends TYPE> getParser() {
        return this.parser_ == null ? (Object obj) -> ( (TYPE)null) : this.parser_;
    }

    @Override
    protected synchronized Optional<TYPE> getData() {
        try {
            return Optional.ofNullable(getParser().apply(uri_.toURL().openStream()));
        } catch (IOException ioe) {
            fireException(ioe);
            return Optional.empty();
        }
    }

    /**
     * Test validity of the given URI.
     * 
     * @param uri The tested uri.
     * @return True, if and only if the given URI is a valid URI for this data source.
     */
    public boolean validSourceURI(URI uri) {
        return uri != null;
    }
   
    
    /**
     * Get the service source uri.
     * 
     * @return The base service URI used to acquire the data.
     */
    public synchronized URI getSourceURI() {
        return this.uri_;
    }

    /**
     * Set teh source URI.
     * 
     * @param newURI The new source URI.
     * @return True, if and only if the given URI was set as current URI.
     */
    protected synchronized boolean setSourceURI(URI newURI) {
        if (validSourceURI(newURI)) {
            // The uri was accepted.
            this.uri_ = newURI;
            return true;
        } else {
            // The uri was rejected.
            return false;
        }
    }

    /**
     * Get the resource path of the data source.
     * 
     * @return The resource path to the data source, if any exist.
     */
    public java.util.Optional<String> getResourcePath() {
        return Optional.ofNullable(getSourceURI().getPath());
    }

}

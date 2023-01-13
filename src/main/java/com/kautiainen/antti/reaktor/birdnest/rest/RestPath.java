package com.kautiainen.antti.reaktor.birdnest.rest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.validation.constraints.NotNull;

/**
 * REST path. Rest path contains zero or more REST resources. REST resource
 * consists resource path followed by the resource parameters.
 * 
 * <p>
 * The arrays {@link #getParameterEncoders()}, {@link #getParameterDecoders()},
 * and {@link #getParameterValidators()} should have
 * same number of elements.
 * </p>
 */
public class RestPath {

    /**
     * The list containing the resource parts of the restful URI.
     * The parameters within the path are represented with undefined values values.
     */
    private java.util.ArrayList<RestPath.RestResource> parts = new java.util.ArrayList<>();

    /**
     * The character set of the path.
     */
    private Charset charset_ = Charset.forName("UTF-8");

    /**
     * Rest resource represents single rest resource of the path.
     */
    public class RestResource {

        /**
         * Rest resource path segment URI only contains path.
         */
        private URI baseUri;

        /**
         * The rest parameters of the rest resource.
         */
        private final List<RestParameter<?>> parameters = new java.util.ArrayList<>();

        /**
         * Creates a new rest resource with parameter definitions.
         * 
         * @param baseUri    The base uri. If undefined, defaults to no base uri.
         * @param parameters The rest parameter definitions of the resource.
         * @throws IllegalArgumentException Any parameter is invalid.
         */
        public RestResource(String baseUri, RestParameter<?>... parameters) throws IllegalArgumentException {
            try {
                this.baseUri = new URI(null, null, baseUri, null);
            } catch (URISyntaxException use) {
                throw new IllegalArgumentException("Invalid base URI", use);
            }
        }

        public RestResource(URI resourceUI, RestParameter<?>[] restParameters) {
            this.baseUri = resourceUI;
            if (restParameters != null) {
                Arrays.stream(restParameters).forEach( this::addParameter );
            }
        }

        /**
         * Get the number of parameters this resorce has.
         * 
         * @return The number of parameters this resource has.
         */
        public int getParameterCount() {
            return this.getRestParameters().size();
        }

        /**
         * Get the rest parameters of the current resource.
         * 
         * @return The rest parameters of the current resource.
         */
        public List<RestParameter<?>> getRestParameters() {
            return Collections.unmodifiableList(this.parameters);
        }

        /**
         * Add parameter to the resource.
         * 
         * @param parameter The added parameter.
         * @throws IllegalArgumentException The added parameter was not accepted.
         */
        protected void addParameter(RestParameter<?> parameter) throws IllegalArgumentException {
            if (parameter == null) throw new IllegalArgumentException("Invalid rest parameter.", 
            new NullPointerException("Undefined parameter."));
            if (getKnownParameterNames().contains(parameter.getName())) 
                throw new IllegalArgumentException("Invalid rest parameter",
                new IllegalArgumentException("Duplicate parameter name is not allowed."));

            // Adding the parameter to the rest parameters.
            this.parameters.add(parameter);
        }

        /**
         * Generate the uri with parameters. 
         * 
         * @param parameterValues The parameter values.
         * @param charset The character set of the string representations.
         * @return The URI containing given parameter values.
         */
        public URI getUri(List<?> parameters, Charset charset) throws IllegalArgumentException {
            StringBuilder parameterValuePath = new StringBuilder();
            List<RestParameter<?>> restParameters = getRestParameters();
            if ((parameters == null ? 0 : parameters.size()) != restParameters.size()) {
                throw new IllegalArgumentException("Invalid number of parameters");
            }
            java.util.Iterator<?> parameterIter = parameters == null ? Collections.emptyIterator()
                    : parameters.iterator();
            for (RestParameter<?> restParameter : restParameters) {
                if (parameterValuePath.length() > 0) {
                    // Adding parameter separator.
                    parameterValuePath.append("/");
                }
                parameterValuePath.append(restParameter.encodeValue((Object) parameterIter.next(), charset));
            }

            URI parameterUri;
            try {
                parameterUri = new URI(null, null, parameterValuePath.toString(), null);
            } catch (URISyntaxException e) {
                // This should never happen, if the parameters are propery encoded.
                e.printStackTrace();
                throw new Error("This should never happen!");
            }
            return baseUri.resolve(parameterUri);
        }

        /**
         * Generate the uri with parameters. 
         * 
         * @param parameterValues The parameter values.
         * @param charset The character set of the string representations.
         * @return The URI containing given parameter values.
         */
        public URI getUri(Map<String, ?> parameterValues, @NotNull Charset charset) {
            StringBuilder parameterValuePath = new StringBuilder();
            List<RestParameter<?>> restParameters = getRestParameters();
            if (parameterValues == null) {
                parameterValues = Collections.emptyMap();
            }
            for (RestParameter<?> restParameter : restParameters) {
                if (!parameterValues.containsKey(restParameter.getName())) {
                    throw new IllegalArgumentException("Missing paramter", new java.util.NoSuchElementException(restParameter.getName()));
                }
                if (parameterValuePath.length() > 0) {
                    // Adding parameter separator.
                    parameterValuePath.append("/");
                }
                parameterValuePath.append(restParameter.encodeValue(parameterValues.get(restParameter.getName()), charset));
            }

            // Generating the URI.
            URI parameterUri;
            try {
                parameterUri = new URI(null, null, parameterValuePath.toString(), null);
            } catch (URISyntaxException e) {
                // This should never happen, if the parameters are propery encoded.
                e.printStackTrace();
                throw new Error("This should never happen!");
            }
            return baseUri.resolve(parameterUri);
        }

        /**
         * The base uri of the resource.
         * 
         * @return The base uri of the resource.
         */
        public URI getBaseUri() {
            return this.baseUri;
        }
    }

    /**
     * Is the path absolute path.
     * 
     * @return True, if and only if the path is absolute.
     */
    public boolean isAbsolute() {
        return !parts.isEmpty() && parts.get(0).getBaseUri().isAbsolute();
    }


    /**
     * Create an empty rest path.
     */
    public RestPath() {
    }

    /**
     * Create a rest path with gi en base path and rest parameters.
     * 
     * @param basePath The base path. 
     * @param restParameters The rest parameter definitions.
     * @throws IllegalArgumentException Either base path or rest parameters were invalid.
     */
    public RestPath(String basePath, RestParameter<?>... restParameters) throws IllegalArgumentException {
        addResource(this.new RestResource(basePath, restParameters));
    }




    /**
     * Create a new rest path from given rest resources.
     * 
     * @param resources The rest resources of the path.
     * @throws IllegalArgumentException Any resource was invalid.
     */
    public RestPath(@NotNull RestResource... resources) throws IllegalArgumentException {
        for (RestResource resource: resources) {
            addResource(resource);
        }
    }

    /**
     * Add resource to the current path.
     * 
     * @param resource The added resource.
     * @throws IllegalArgumentException The given resource was rejected for some
     *                                  reason.
     */
    public void addResource(RestResource resource) throws IllegalArgumentException {
        this.parts.add(resource);
    }

    /**
     * Get the list of rest parameters of this resource.
     * 
     * @return The rest parameters of this resource.
     */
    public java.util.List<RestParameter<?>> getRestParameters() {
        return this.parts.stream().collect(() -> (new java.util.ArrayList<RestParameter<?>>()),
                (List<RestParameter<?>> list, RestPath.RestResource resource) -> {
                    list.addAll(resource.getRestParameters());
                }, (List<RestParameter<?>> a, List<RestParameter<?>> b) -> {
                    a.addAll(b);
                });
    }

    /**
     * Get the parameter value validator for the parameter of an index.
     * 
     * @param index The index of the wanted parameter.
     * @return The predicate testing the value of a parameter at given index.
     * @throws IndexOutOfBoundsException The given index is invalid index.
     */
    public Predicate<?> getParameterValueValidator(int index)
    throws IndexOutOfBoundsException {
        return this.getRestParameters().get(index).getValueValidator();
    }

    /**
     * The charset of the path.
     * 
     * @return The character set of the template.
     */
    public @NotNull Charset getCharset() {
        return charset_;
    }

    /**
     * Get the URI containing the REST path with given parameters. 
     * 
     * @param parameters The parameters of the REST resources of the path as list.  
     * @return The rest path URI containing given parameter values.
     * @throws IllegalArgumentException The parameter list contained invalid
     *  number of parameters, or some of the parameters was invalid.
     */
    public URI getUri(Object... parameters) throws IllegalArgumentException {
        return getUri(parameters == null ? java.util.Collections.emptyList() : java.util.Arrays.asList(parameters));
    }

    /**
     * Get the URI containing the REST path with given parameters. 
     * 
     * @param parameters THe parameters of the REST resource of the pat as map from parameter name
     * to parameter value.
     * @return The rest path URI containing given parameter values.
     * @throws IllegalArgumentException The given parameter map either contained an invalid value of a parameter,
     *  or lacked required parameter value.
     */
    public URI getUri(java.util.Map<String, ?> parameters) throws IllegalArgumentException {
        URI result = null, resourceUri = null;
        try {
            for (RestResource resource : parts) {
                resourceUri = resource.getUri(parameters,
                        getCharset());
                if (result != null) {
                    // Appending the resource uri to the resulting uri.
                    result = result.resolve(resourceUri);
                } else {
                    // Setting the result.
                    result = resourceUri;
                }
            }
            // Create result.
            return result == null ? new URI(null, null, null, null) : result;
        } catch (URISyntaxException uri) {
            throw new IllegalArgumentException("Any transformation of the values resulted invalid URI", uri);
        }

    }

    /**
     * Get the URI containing the REST path with given parameters. 
     * 
     * @param parameters The parameters of the uri. 
     * @return The rest path URI containing given parameter values.
     * @throws IllegalArgumentException The parameter list contained invalid
     *  number of parameters, or some of the parameters was invalid.
     */
    public URI getUri(List<?> parameters) throws IllegalArgumentException {
        int parameterIndex = 0;
        URI result = null, resourceUri = null;
        int parameterCount = 0;
        try {
            for (RestResource resource : parts) {
                parameterCount = resource.getParameterCount();
                resourceUri = resource.getUri(parameters.subList(parameterIndex, parameterIndex + parameterCount),
                        getCharset());
                if (result != null) {
                    // Appending the resource uri to the resulting uri.
                    result = result.resolve(resourceUri);
                } else {
                    // Setting the result.
                    result = resourceUri;
                }
                parameterIndex += parameterCount;
            }

            // Testing if we had too many parameters.
            if (parameterIndex < parameters.size()) {
                throw new IllegalArgumentException("Too many parameters!", new IndexOutOfBoundsException(parameterIndex));
            }

            // Create result.
            return result == null ? new URI(null, null, null, null) : result;
        } catch (IndexOutOfBoundsException io) {
            // The given parameters array had too few elements.
            throw new IllegalArgumentException("Not enough parameters!", io);
        } catch (URISyntaxException uri) {
            throw new IllegalArgumentException("Any transformation of the values resulted invalid URI", uri);
        }

    }

    /**
     * Get the known paramter names.
     * @return The list containing known parameter names. 
     */
    public List<String> getKnownParameterNames() {
        return this.getRestParameters().stream().map( RestParameter::getName ).toList();
    }

}
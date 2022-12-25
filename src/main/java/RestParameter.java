import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

/**
 * This class represents singel REST parameter. It does know how to convert
 * values to
 * strings, and how to revert valeus back from string.
 */
public class RestParameter<TYPE> {

    private String name_;
    private Predicate<String> stringValidator_;
    private @NotNull Predicate<? super TYPE> valueValidator_;
    private @NotNull Function<? super TYPE, String> encodeFunction_;
    private @NotNull Function<String, ? extends TYPE> decodeFunction_;

    /**
     * Creata a new REST parameter with a name, a string representation matcher
     * pattern, an encoder function, and a decoder function.
     * The value validator is derived form encoder.
     * 
     * @param name    The name of the rest parameter.
     * @param pattern The pattern matching ot the string representation of the
     *                value.
     * @param encoder The encoder covnerting the parameter value into a string.
     * @param decoder The decoder converting a string into value.
     * @throws IllegalArgumentException Any paramer was invalid.
     */
    public RestParameter(String name, Pattern pattern, @NotNull Function<? super TYPE, String> encoder,
            @NotNull Function<String, ? extends TYPE> decoder)
            throws IllegalArgumentException {
        this.setName(name);
        this.setStringValidator(new PatternPredicate(pattern));
        this.encodeFunction_ = encoder;
        this.decodeFunction_ = decoder;
        this.valueValidator_ = (TYPE value) -> (getStringValidator().test(encoder.apply(value)));
    }

    /**
     * Creata a new REST parameter with a name, a string representation matcher
     * pattern, an encoder function, and a decoder function.
     * The value validator is derived form encoder.
     * 
     * @param name    The name of the rest parameter.
     * @param pattern The pattern matching ot the string representation of the
     *                value.
     * @param encoder The encoder covnerting the parameter value into a string.
     * @param decoder The decoder converting a string into value.
     * @throws IllegalArgumentException Any paramer was invalid.
     */
    public RestParameter(String name, @NotNull Function<? super TYPE, String> encoder,
            @NotNull Function<String, ? extends TYPE> decoder,
            Predicate<String> stringTester, Predicate<? super TYPE> valueTester)
            throws IllegalArgumentException {
        this.setName(name);
        this.encodeFunction_ = encoder;
        this.decodeFunction_ = decoder;
        this.setStringValidator(null);
        this.valueValidator_ = valueTester != null ? valueTester
                : (TYPE value) -> (getStringValidator().test(encoder.apply(value)));
    }

    /**
     * Set the name of the predicate.
     * 
     * @param name The name of the predicate.
     * @throws IllegalArgumentException The given name is invalid.
     */
    protected void setName(String name) throws IllegalArgumentException {
        if (name == null || name.isEmpty() ||
                !(name.codePoints().allMatch(Character::isJavaIdentifierPart)
                        && Character.isJavaIdentifierStart(name.codePointAt(0)))) {
            throw new IllegalArgumentException("Invalid name: not a valid java identifier");
        }
        this.name_ = name;
    }

    /**
     * Predicate, which is always true.
     * 
     * The tested type allows equality of different objects.
     */
    public static class TruePredicate<TYPE> implements Predicate<TYPE> {
        /**
         * The tested type used to determine equality, if given.
         */
        private Class<TYPE> testedType_;

        /**
         * Create a new true predicate with equal only to iteself.
         */
        public TruePredicate() {
            this.testedType_ = null;
        }

        /**
         * Createa new true predicate equal to all other true predicates with
         * same tested type.
         * 
         * @param testedType The tested type.
         */
        public TruePredicate(@NotNull Class<TYPE> testedType) {
            this.testedType_ = testedType;
        }

        /**
         * Test the value.
         */
        public boolean test(TYPE type) {
            return true;
        }

        public boolean equals(TruePredicate<?> another) {
            return another != null && (another == this ||
                    java.util.Objects.equals(this.getTestedType(), another.getTestedType()));
        }

        /**
         * The tested type of the predicate.
         * 
         * @return The tested type of the predicate. If undefined, the predicate
         *         does not know its runtime type.
         */
        public final Class<TYPE> getTestedType() {
            return this.testedType_;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (obj == this)
                return true;
            if (obj instanceof TruePredicate<?> predicate) {
                Class<TYPE> testedType = getTestedType();
                return testedType != null && testedType.equals(predicate.getTestedType());
            } else {
                return false;
            }
        }

        /**
         * Hash-code implementation branches on the case the predicate knows its
         * tested type.
         */
        public int hashCode() {
            if (testedType_ == null) {
                return super.hashCode();
            } else {
                return testedType_.hashCode();
            }
        }
    }

    /**
     * Get the true predicate.
     * 
     * @param <TYPE> The type of the wanted predicate.
     * @return Teh true predicate of the given type.
     */
    public static <TYPE> Predicate<TYPE> getTruePredicate() {
        return new TruePredicate<TYPE>();
    }

    public static <TYPE> Predicate<TYPE> getTruePredicate(@NotNull Class<TYPE> type) {
        return new TruePredicate<TYPE>(type);
    }

    /**
     * Set the string representation validator.
     * 
     * @param predicate The predicate testing string representation validating
     *                  function.
     *                  An undefined (<code>null</code>) value is interpreted
     *                  predicate accepting all values.
     */
    @SuppressWarnings("unchecked")
    protected void setStringValidator(Predicate<?> predicate) throws IllegalArgumentException {

        if (predicate == null) {
            this.stringValidator_ = getTruePredicate(String.class);
        }

        try {
            this.stringValidator_ = (Predicate<String>) predicate;
        } catch (ClassCastException cce) {

        }

        // It was not a string, trying character sequence.
        try {
            final Predicate<CharSequence> charSeqPredicate = (Predicate<CharSequence>) predicate;
            this.stringValidator_ = new Predicate<String>() {

                @Override
                public boolean test(String str) {
                    return charSeqPredicate.test(str);
                }

                @Override
                public boolean equals(Object obj) {
                    return obj != null && (obj == this || obj == charSeqPredicate);
                }

                @Override
                public int hashCode() {
                    return charSeqPredicate.hashCode();
                }
            };
        } catch (ClassCastException cce) {

        }

        try {
            Predicate<Object> objPredicate = (Predicate<Object>) predicate;
            this.stringValidator_ = (String str) -> (objPredicate.test(str));
        } catch (ClassCastException cce) {
            // If this fails, we have invalid predicate
            throw new IllegalArgumentException("Incompatible predicate");
        }

    }

    /**
     * Get the name of the parameter.
     * 
     * @return The name of the parameter.
     */
    public @NotNull String getName() {
        return name_;
    }

    /**
     * Get the string validator of the parameter value.
     * 
     * @return The string validator of the parameter value.
     */
    public @NotNull Predicate<String> getStringValidator() {
        return this.stringValidator_;
    }

    /**
     * Get the value validator of the parameter value.
     * 
     * @return Get the value validator of the paramter value.
     */
    public @NotNull Predicate<? super TYPE> getValueValidator() {
        return this.valueValidator_;
    }

    /**
     * The function converting value of type into non-URL-encoded string.
     * 
     * @return A function that encodes a value into a string.
     */
    public @NotNull Function<? super TYPE, String> getEncodeFunction() {
        return this.encodeFunction_;
    }

    /**
     * The function decoding a value from URL-decoded string.
     * 
     * @return A funciton that decodes an url-decoded string.
     */
    public @NotNull Function<String, ? extends TYPE> getDecodeFunction() {
        return this.decodeFunction_;
    }

    /**
     * The type name of the parameters.
     * 
     * @return The type name of the parameter.
     */
    public Optional<String> getTypeName() {
        return Optional.empty();
    }

    /**
     * Encodes the given value to URL encoded value.
     * 
     * @param value The value to encode.
     * @return The URL-encoded string value.
     * @throws IllegalArgumentException The given value was invalid.
     */
    @SuppressWarnings("unchecked")
    final public String encodeValue(Object value, Charset charset) throws IllegalArgumentException {
        try {
            TYPE typed = (TYPE)value;
            if (getValueValidator().test(typed)) {
                return URLEncoder.encode(this.getEncodeFunction().apply(typed), charset);
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        } catch (ClassCastException cce) {
            throw new IllegalArgumentException("Invalid value type");
        }
    }

    /**
     * Decodes the given URL-encoded value to the orignal value.
     * 
     * @param value The URL-encoded value.
     * @return The original value.
     * @throws IllegalArgumentException The given encoded value was invalid.
     */
    final public TYPE decodeValue(String urlValue, Charset charset) throws IllegalArgumentException {
        String unencoded = URLDecoder.decode(urlValue, charset);
        if (getStringValidator().test(unencoded)) {
            return this.getDecodeFunction().apply(unencoded);
        } else {
            throw new IllegalArgumentException("Invalid string reprsentation");
        }
    }

}
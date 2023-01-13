package com.kautiainen.antti.reaktor.birdnest.pattern;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.validation.constraints.NotNull;

/**
 * PatternPredicate matches character seqeunce using regular expression pattern.
 */
public class PatternPredicate implements Predicate<CharSequence> {

    /**
     * The predicate used for matching pattern.
     */
    private @NotNull Pattern pattern_;

    /**
     * Create a new pattern predicate.
     * 
     * @param pattern The pattern predicate.
     */
    public PatternPredicate(@NotNull Pattern pattern) {
        this.pattern_ = pattern;
    }

    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj instanceof PatternPredicate predicatePattern) {
            return (predicatePattern.getPattern().toString().equals(this.getPattern().toString()));
        }

        // The test failed if we end here.
        return false;
    }

    /**
     * The pattern of this predicate.
     * 
     * @return The pattern used for this predicate.
     */
    public Pattern getPattern() {
        return this.pattern_;
    }

    /**
     * Tests the given charater seqeunce.
     * 
     * @param tested The tested character sequence.
     * @return True, if and on ly if the given charcter sequence passes the
     *         predicate.
     */
    public final boolean test(CharSequence tested) {
        return tested != null && getPattern().matcher(tested).matches();
    }

    /**
     * Combines the current patern predicate with given pattern predicate.
     * 
     * @param predicate The pattern predicate
     * @return The predicate which is true, if either current predicate or given
     *         predicate is true.
     * @throws PatternSyntaxException The patterns shared same pattern group name.
     */
    public PatternPredicate or(@NotNull PatternPredicate predicate) throws PatternSyntaxException {
        return new PatternPredicate(Pattern
                .compile("(?:" + this.getPattern().toString() + "|" + predicate.getPattern().toString() + ")"));
    }

    @Override
    public Predicate<CharSequence> or(Predicate<? super CharSequence> nextPredicate) {
        return Predicate.super.and(nextPredicate);
    }

    @Override
    public int hashCode() {
        return getPattern().toString().hashCode();
    }
}
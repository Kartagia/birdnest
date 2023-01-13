package com.kautiainen.antti.reaktor.birdnest.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import com.kautiainen.antti.reaktor.birdnest.pattern.PatternPredicate;
import com.kautiainen.antti.reaktor.birdnest.rest.RestPath.RestResource;

/**
 * Testing RESTPath. 
 */
public class RestPathTest {

    public static<TYPE> boolean equalLists(List<? extends TYPE> list, List<? extends TYPE> tested) {
        if (list == null) return tested == null;
        if (list.size() != tested.size()) {
            return false;
        }
        Iterator<? extends TYPE> listIterator = list.iterator();
        Iterator<? extends TYPE> testedIterator = tested.iterator();
        while (listIterator.hasNext()) {
            if (!java.util.Objects.equals(listIterator.next(), testedIterator.next())) {
                return false;
            }
        }
        return true; 
    }

    @Test
    public void testAddResource() {
        RestPath path = new RestPath();
        List<RestResource> expResult = new ArrayList<>(), result = path.getRestResources(); 

        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));

        PatternPredicate wordPattern = new PatternPredicate(Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS)); 
        RestParameter<?> parameter = new RestParameter<String>("name", 
        (String s) -> (s), (String s) -> (s), 
        (String s) -> (s != null && wordPattern.test(s)),
        (String s) -> (s != null && wordPattern.test(s))
        );
        RestPath.RestResource added = path.new RestResource("nimi", parameter);
        path.addResource(added);
        expResult.add(added);
        result = path.getRestResources();

        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));    
    }

    @Test
    public void testGetKnownParameterNames() {

    }

    @Test
    public void testGetParameterValueValidator() {

    }

    @Test
    public void testGetRestParameters() {
        RestPath path = new RestPath();
        List<RestParameter<?>> expResult = new ArrayList<>(), result = path.getRestParameters(); 

        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));

        PatternPredicate wordPattern = new PatternPredicate(Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS)); 
        RestParameter<?> parameter = new RestParameter<String>("name", 
        (String s) -> (s), (String s) -> (s), 
        (String s) -> (s != null && wordPattern.test(s)),
        (String s) -> (s != null && wordPattern.test(s))
        );
        RestPath.RestResource added = path.new RestResource("nimi", parameter);
        path.addResource(added);
        expResult.add(parameter);
        result = path.getRestParameters();

        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));    
    }

    @Test 
    public void RestResource_addParameter() {
        RestPath path = new RestPath();
        RestPath.RestResource resource = path.new RestResource("user");
        List<RestParameter<?>> expResult = new ArrayList<>(), result = resource.getRestParameters(); 

        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));    

        PatternPredicate wordPattern = new PatternPredicate(Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS)); 
        RestParameter<?> parameter = new RestParameter<String>("name", 
        (String s) -> (s), (String s) -> (s), 
        (String s) -> (s != null && wordPattern.test(s)),
        (String s) -> (s != null && wordPattern.test(s))
        );
        RestPath.RestResource added = path.new RestResource("nimi", parameter);
        expResult.add(parameter);
        result = added.getRestParameters();
        assertTrue(
            "Expected " + expResult.toString() + ", but got " + result.toString(),
            equalLists(expResult, result));    
       
    }

    @Test
    public void testGetUri() {

    }

    @Test
    public void testGetUri2() {

    }

    @Test
    public void testGetUri3() {

    }

    @Test
    public void testIsAbsolute() {

    }
}

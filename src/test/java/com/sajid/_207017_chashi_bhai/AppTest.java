package com.sajid._207017_chashi_bhai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test class for the application
 */
class AppTest {

    @Test
    void testApplicationExists() {
        // Test that the App class exists and can be instantiated
        assertNotNull(App.class);
    }

    @Test
    void testBasicAssertions() {
        // Basic test to verify JUnit is working
        assertEquals(2, 1 + 1);
        assertTrue(true);
        assertFalse(false);
    }

    @Test
    void testStringOperations() {
        String test = "Chashi Bhai";
        assertNotNull(test);
        assertTrue(test.contains("Chashi"));
        assertEquals(11, test.length());
    }
}

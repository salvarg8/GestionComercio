package org.gestionComercio.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BCryptPasswordServiceTest {

    private BCryptPasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new BCryptPasswordService();
    }

    @Test
    void testEncode() {
        String rawPassword = "password123";
        String encodedPassword = passwordService.encode(rawPassword);

        assertNotNull(encodedPassword);
        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(encodedPassword.length() > 0);
        assertTrue(passwordService.matches(rawPassword, encodedPassword));
    }

    @Test
    void testMatches_Success() {
        String rawPassword = "password123";
        String encodedPassword = passwordService.encode(rawPassword);

        assertTrue(passwordService.matches(rawPassword, encodedPassword));
    }

    @Test
    void testMatches_Failure() {
        String rawPassword = "password123";
        String wrongPassword = "wrongpassword";
        String encodedPassword = passwordService.encode(rawPassword);

        assertFalse(passwordService.matches(wrongPassword, encodedPassword));
    }
}

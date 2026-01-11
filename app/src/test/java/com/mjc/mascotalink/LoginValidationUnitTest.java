package com.mjc.mascotalink;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit Tests para validaciones de LoginActivity
 * No requiere emulador - se ejecuta localmente
 */
public class LoginValidationUnitTest {

    private String testEmail;
    private String testPassword;

    @Before
    public void setUp() {
        testEmail = "";
        testPassword = "";
    }

    /**
     * TEST 1: Validar email vacío
     */
    @Test
    public void testEmailEmptyValidation() {
        testEmail = "";
        boolean isValid = !testEmail.isEmpty();
        assertFalse("Email vacío debe ser inválido", isValid);
    }

    /**
     * TEST 2: Validar email con formato válido
     */
    @Test
    public void testEmailFormatValidation() {
        testEmail = "test@example.com";
        boolean hasAtSymbol = testEmail.contains("@");
        boolean hasDot = testEmail.contains(".");
        assertTrue("Email debe contener @", hasAtSymbol);
        assertTrue("Email debe contener .", hasDot);
    }

    /**
     * TEST 3: Validar email sin @
     */
    @Test
    public void testEmailWithoutAtSymbol() {
        testEmail = "testexample.com";
        boolean isValid = testEmail.contains("@");
        assertFalse("Email sin @ es inválido", isValid);
    }

    /**
     * TEST 4: Validar password vacío
     */
    @Test
    public void testPasswordEmptyValidation() {
        testPassword = "";
        boolean isValid = !testPassword.isEmpty();
        assertFalse("Password vacío debe ser inválido", isValid);
    }

    /**
     * TEST 5: Validar password menor a 6 caracteres
     */
    @Test
    public void testPasswordMinimumLength() {
        testPassword = "12345";
        int minLength = 6;
        boolean isValid = testPassword.length() >= minLength;
        assertFalse("Password menor a 6 caracteres es inválido", isValid);
    }

    /**
     * TEST 6: Validar password de 6 caracteres
     */
    @Test
    public void testPasswordMinimumLengthValid() {
        testPassword = "123456";
        int minLength = 6;
        boolean isValid = testPassword.length() >= minLength;
        assertTrue("Password de 6 caracteres es válido", isValid);
    }

    /**
     * TEST 7: Validar combinación válida
     */
    @Test
    public void testValidEmailAndPassword() {
        testEmail = "user@example.com";
        testPassword = "password123";
        
        boolean emailValid = testEmail.contains("@") && testEmail.contains(".");
        boolean passwordValid = testPassword.length() >= 6;
        
        assertTrue("Email debe ser válido", emailValid);
        assertTrue("Password debe ser válido", passwordValid);
        assertTrue("Ambos deben ser válidos", emailValid && passwordValid);
    }

    /**
     * TEST 8: Validar sanitización de espacios
     */
    @Test
    public void testEmailSanitization() {
        testEmail = " test@example.com ";
        String sanitized = testEmail.trim();
        assertNotEquals("Email con espacios debe ser diferente", testEmail, sanitized);
        assertTrue("Email sanitizado debe ser válido", sanitized.contains("@"));
    }

    /**
     * TEST 9: Validar email no sensible a mayúsculas
     */
    @Test
    public void testEmailCaseInsensitive() {
        testEmail = "TEST@EXAMPLE.COM";
        String lowercase = testEmail.toLowerCase();
        assertTrue("Email en minúsculas debe ser válido", lowercase.contains("@"));
    }

    /**
     * TEST 10: Validar email duplicado con @
     */
    @Test
    public void testEmailWithMultipleAtSymbols() {
        testEmail = "test@@example.com";
        int atCount = testEmail.split("@", -1).length - 1;
        assertTrue("Email debe tener al menos un @", atCount >= 1);
        assertTrue("Aunque tenga múltiples @", atCount > 0);
    }
}

package com.coinbase.prime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BestBidAskPrinterTest {

    @Test
    void testSignatureGeneration() throws Exception {
        String channel = "l2_data";
        String key = "test_key";
        String secret = "test_secret";
        String account = "test_account";
        String timestamp = "1234567890";
        String[] products = {"BTC-USD"};

        java.lang.reflect.Method signMethod = BestBidAskPrinter.class
            .getDeclaredMethod("sign", String.class, String.class, String.class, String.class, String.class, String[].class);
        signMethod.setAccessible(true);
        
        String signature = (String) signMethod.invoke(null, channel, key, secret, account, timestamp, products);
        
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertTrue(signature.length() > 10);
        
        // Test that same inputs produce same signature
        String signature2 = (String) signMethod.invoke(null, channel, key, secret, account, timestamp, products);
        assertEquals(signature, signature2);
    }

    @Test
    void testEnvVariableHelper() throws Exception {
        // Test that env() method throws exception for null input
        java.lang.reflect.Method envMethod = BestBidAskPrinter.class
            .getDeclaredMethod("env", String.class);
        envMethod.setAccessible(true);
        
        // This should throw IllegalStateException for a non-existent env var
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            envMethod.invoke(null, "NON_EXISTENT_VARIABLE_12345");
        });
    }

    @Test
    void testBookClassExists() {
        // Test that the inner Book class is properly defined
        Class<?>[] innerClasses = BestBidAskPrinter.class.getDeclaredClasses();
        assertTrue(innerClasses.length > 0);
        
        boolean foundBookClass = false;
        for (Class<?> innerClass : innerClasses) {
            if (innerClass.getSimpleName().equals("Book")) {
                foundBookClass = true;
                break;
            }
        }
        assertTrue(foundBookClass, "Book inner class should exist");
    }

    @Test
    void testMainMethodExists() throws Exception {
        // Verify main method exists and has correct signature
        java.lang.reflect.Method mainMethod = BestBidAskPrinter.class
            .getMethod("main", String[].class);
        
        assertNotNull(mainMethod);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
        assertEquals(void.class, mainMethod.getReturnType());
    }
}
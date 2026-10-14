package com.dbtool.panels;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DataMaskingTest {

    @Test
    void testEmailMasking() {
        String email = "john.doe@example.com";
        String masked = DataMaskingDialog.applyMask(email, DataMaskingDialog.MaskingStrategy.EMAIL_MASK, 42);
        assertNotEquals(email, masked);
        assertTrue(masked.contains("@"));
        assertTrue(masked.startsWith("j***e@"));
    }

    @Test
    void testPhoneMasking() {
        String phone = "+1 (555) 234-5678";
        String masked = DataMaskingDialog.applyMask(phone, DataMaskingDialog.MaskingStrategy.PHONE_MASK, 42);
        assertEquals("***-***-5678", masked);
    }

    @Test
    void testCardMasking() {
        String card = "4111-2222-3333-4444";
        String masked = DataMaskingDialog.applyMask(card, DataMaskingDialog.MaskingStrategy.CARD_MASK, 42);
        assertEquals("****-****-****-4444", masked);
    }

    @Test
    void testRedaction() {
        String secret = "SuperSecretPassword123";
        String masked = DataMaskingDialog.applyMask(secret, DataMaskingDialog.MaskingStrategy.REDACT, 42);
        assertEquals("[REDACTED]", masked);
    }

    @Test
    void testNumericJitter() {
        String val = "1000.00";
        String masked = DataMaskingDialog.applyMask(val, DataMaskingDialog.MaskingStrategy.NUMERIC_JITTER, 42);
        double num = Double.parseDouble(masked);
        // Jitter is between 85% and 115%
        assertTrue(num >= 850.0 && num <= 1150.0);
    }

    @Test
    void testSha256Hash() {
        String id = "customer-98765";
        String masked = DataMaskingDialog.applyMask(id, DataMaskingDialog.MaskingStrategy.SHA256_HASH, 42);
        assertTrue(masked.startsWith("hash_"));
    }
}

package com.dbtool.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CryptoUtilTest {
    @Test
    void testObfuscationRoundTrip() {
        String original = "p@ssw0rd!123";
        String encoded = CryptoUtil.obfuscate(original);
        assertNotEquals(original, encoded);
        String decoded = CryptoUtil.deobfuscate(encoded);
        assertEquals(original, decoded);
    }
}

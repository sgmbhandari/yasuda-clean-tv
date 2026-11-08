package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class ResetPinTest {
    @Test fun validatesVerifierAndRejectsWrongPin() {
        val salt = "00112233445566778899aabbccddeeff"
        val digest = ResetPin.derive("123456".toCharArray(), salt)
        assertTrue(ResetPin.matches("123456".toCharArray(), salt, digest))
        assertFalse(ResetPin.matches("123457".toCharArray(), salt, digest))
        assertFalse(ResetPin.matches("12345".toCharArray(), salt, digest))
        assertFalse(ResetPin.matches("abcdef".toCharArray(), salt, digest))
    }
    @Test fun rejectsMalformedProvisioningWithoutDerivingAnUnboundedKey() {
        assertFalse(ResetPin.matches("123456".toCharArray(), "not hex", "00"))
        assertFalse(ResetPin.matches("123456".toCharArray(), "00", "0".repeat(64)))
    }
}

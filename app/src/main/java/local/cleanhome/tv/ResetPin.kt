package local.cleanhome.tv

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The deployment supplies a per-TV verifier, never a shared PIN in the APK. */
object ResetPin {
    const val ITERATIONS = 120_000
    private fun decode(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun derive(pin: CharArray, salt: String): String {
        require(salt.matches(Regex("[a-f0-9]{32}")))
        val spec = PBEKeySpec(pin, decode(salt), ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        } finally { spec.clearPassword() }
    }
    fun matches(pin: CharArray, salt: String, digest: String): Boolean {
        if (pin.size != 6 || pin.any { it !in '0'..'9' } || !salt.matches(Regex("[a-f0-9]{32}")) || !digest.matches(Regex("[a-f0-9]{64}"))) return false
        return MessageDigest.isEqual(decode(derive(pin, salt)), decode(digest))
    }
}

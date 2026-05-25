package encryption;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 encryption / decryption utility (CBC mode with random IV).
 *
 * <p>Each encrypt call prepends a fresh 16-byte IV to the cipher-text so that
 * the same plain-text produces a different cipher-text every time.  The decrypt
 * call reads the IV from the first 16 bytes of the Base64-decoded payload.</p>
 *
 * <p>The shared session key is derived from a passphrase using SHA-256 so that
 * both sides only need to agree on a string, not exchange raw key bytes.</p>
 */
public final class EncryptionUtil {

    private EncryptionUtil() {}

    private static final String ALGORITHM   = "AES";
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";
    private static final int    IV_LENGTH   = 16;           // bytes (128 bits)
    private static final int    KEY_BITS    = 256;

    // ── Shared session passphrase (would be exchanged via Diffie-Hellman in prod) ──
    // For this educational project we use a fixed passphrase agreed out-of-band.
    private static final String DEFAULT_PASSPHRASE = "D1str1butedChatApp-AES256-Key!@#";

    // ── Key derivation ───────────────────────────────────────────────────────

    /**
     * Derives a 256-bit AES key from an arbitrary passphrase string using SHA-256.
     */
    public static SecretKey deriveKey(String passphrase) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes   = sha.digest(passphrase.getBytes("UTF-8"));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Generates a random 256-bit AES key (useful for per-session keys).
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
        kg.init(KEY_BITS, new SecureRandom());
        return kg.generateKey();
    }

    /**
     * Encodes a SecretKey to a Base64 string so it can be transmitted / stored.
     */
    public static String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Reconstructs a SecretKey from its Base64 encoding.
     */
    public static SecretKey decodeKey(String b64) {
        byte[] raw = Base64.getDecoder().decode(b64);
        return new SecretKeySpec(raw, ALGORITHM);
    }

    // ── Encryption ───────────────────────────────────────────────────────────

    /**
     * Encrypts {@code plainText} with the default shared passphrase.
     *
     * @return Base64-encoded string of [ IV (16 bytes) || cipher-text ]
     */
    public static String encrypt(String plainText) throws Exception {
        return encrypt(plainText, deriveKey(DEFAULT_PASSPHRASE));
    }

    /**
     * Encrypts {@code plainText} with the supplied {@code key}.
     *
     * @return Base64-encoded string of [ IV (16 bytes) || cipher-text ]
     */
    public static String encrypt(String plainText, SecretKey key) throws Exception {
        // Generate random IV
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        byte[] cipherBytes = cipher.doFinal(plainText.getBytes("UTF-8"));

        // Prepend IV to cipher-text
        byte[] combined = new byte[IV_LENGTH + cipherBytes.length];
        System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
        System.arraycopy(cipherBytes, 0, combined, IV_LENGTH, cipherBytes.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    // ── Decryption ───────────────────────────────────────────────────────────

    /**
     * Decrypts a payload produced by {@link #encrypt(String)} using the default passphrase.
     */
    public static String decrypt(String encryptedBase64) throws Exception {
        return decrypt(encryptedBase64, deriveKey(DEFAULT_PASSPHRASE));
    }

    /**
     * Decrypts a payload produced by {@link #encrypt(String, SecretKey)}.
     */
    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        // Split IV and cipher-text
        byte[] iv         = new byte[IV_LENGTH];
        byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);

        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, "UTF-8");
    }

    // ── Password hashing (for user auth) ────────────────────────────────────

    /**
     * Returns a hex-encoded SHA-256 hash of the given password.
     * In production you would use bcrypt; this is sufficient for the project scope.
     */
    public static String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash      = md.digest(password.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public static boolean verifyPassword(String plainPassword, String storedHash) throws Exception {
        String incoming = hashPassword(plainPassword);
        if (incoming.length() != storedHash.length()) return false;
        int diff = 0;
        for (int i = 0; i < incoming.length(); i++) {
            diff |= incoming.charAt(i) ^ storedHash.charAt(i);
        }
        return diff == 0;
    }
}

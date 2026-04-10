package com.example.expressionphotobooth.data.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import android.util.Base64;

/**
 * Utility class for password hashing using SHA-256
 * Provides secure password handling before storage or transmission
 */
public class PasswordSecurityUtil {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    /**
     * Hash password with random salt using SHA-256
     * Format: base64(salt) + ":" + base64(hash)
     *
     * @param password Plain text password
     * @return Salted hash in format: salt:hash
     * @throws NoSuchAlgorithmException If SHA-256 algorithm is not available
     */
    public static String hashPassword(String password) throws NoSuchAlgorithmException {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        // Generate random salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        // Hash password with salt
        byte[] hash = hashPasswordWithSalt(password, salt);

        // Encode to Base64 for storage
        String encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        String encodedHash = Base64.encodeToString(hash, Base64.NO_WRAP);

        return encodedSalt + ":" + encodedHash;
    }

    /**
     * Verify password against stored hash
     *
     * @param password Plain text password to verify
     * @param storedHash Stored hash in format: salt:hash
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null) {
            return false;
        }

        try {
            // Extract salt and hash from stored value
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                return false;
            }

            byte[] salt = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] storedHashBytes = Base64.decode(parts[1], Base64.NO_WRAP);

            // Hash provided password with same salt
            byte[] providedHash = hashPasswordWithSalt(password, salt);

            // Compare hashes using constant-time comparison to prevent timing attacks
            return constantTimeEquals(providedHash, storedHashBytes);
        } catch (IllegalArgumentException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Hash password with specific salt
     *
     * @param password Plain text password
     * @param salt Salt bytes
     * @return Hashed password
     * @throws NoSuchAlgorithmException If SHA-256 algorithm is not available
     */
    private static byte[] hashPasswordWithSalt(String password, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        digest.update(salt);
        return digest.digest(password.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Constant-time comparison to prevent timing attacks
     * Compares two byte arrays in constant time
     *
     * @param a First byte array
     * @param b Second byte array
     * @return true if arrays are equal, false otherwise
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }

    /**
     * Generate SHA-256 hash without salt (for non-password data integrity check)
     *
     * @param data Data to hash
     * @return Base64 encoded SHA-256 hash
     * @throws NoSuchAlgorithmException If SHA-256 algorithm is not available
     */
    public static String generateSimpleHash(String data) throws NoSuchAlgorithmException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }
}

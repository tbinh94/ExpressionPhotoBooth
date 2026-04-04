package com.example.expressionphotobooth.data.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Utility class for AES encryption/decryption using Android Keystore
 * Provides secure encryption for sensitive image data
 */
public class EncryptionUtil {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "expressionphotobooth_key";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";
    private static final int KEY_SIZE = 256;

    private static EncryptionUtil instance;
    private final KeyStore keyStore;
    private final SecureRandom secureRandom;

    private EncryptionUtil() throws Exception {
        this.keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        this.keyStore.load(null);
        this.secureRandom = new SecureRandom();
        ensureKeyExists();
    }

    public static synchronized EncryptionUtil getInstance() throws Exception {
        if (instance == null) {
            instance = new EncryptionUtil();
        }
        return instance;
    }

    /**
     * Ensure that the encryption key exists in Android Keystore
     */
    private void ensureKeyExists() throws Exception {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createKey();
        }
    }

    /**
     * Create a new AES key in Android Keystore
     */
    private void createKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setKeySize(KEY_SIZE)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(false) // Set to true if biometric auth needed
                .build();

        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    /**
     * Encrypt data using AES algorithm
     * @param plaintext Data to encrypt
     * @return Base64 encoded encrypted data with IV prepended
     * @throws Exception If encryption fails
     */
    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        // Get IV from cipher
        byte[] iv = cipher.getIV();
        byte[] encryptedData = cipher.doFinal(plaintext.getBytes());

        // Combine IV + encrypted data and encode to Base64
        byte[] ivAndEncryptedData = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, ivAndEncryptedData, 0, iv.length);
        System.arraycopy(encryptedData, 0, ivAndEncryptedData, iv.length, encryptedData.length);

        return Base64.encodeToString(ivAndEncryptedData, Base64.DEFAULT);
    }

    /**
     * Decrypt data using AES algorithm
     * @param encryptedText Base64 encoded encrypted data with IV prepended
     * @return Decrypted plaintext
     * @throws Exception If decryption fails
     */
    public String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        byte[] decodedData = Base64.decode(encryptedText, Base64.DEFAULT);

        // Extract IV (first 16 bytes for AES)
        byte[] iv = new byte[16];
        System.arraycopy(decodedData, 0, iv, 0, 16);

        // Extract encrypted data (remaining bytes)
        byte[] encryptedData = new byte[decodedData.length - 16];
        System.arraycopy(decodedData, 16, encryptedData, 0, encryptedData.length);

        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] decryptedData = cipher.doFinal(encryptedData);
        return new String(decryptedData);
    }

    /**
     * Encrypt byte array (for binary data like images)
     * @param data Data to encrypt
     * @return Base64 encoded encrypted data with IV prepended
     * @throws Exception If encryption fails
     */
    public String encryptBytes(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return null;
        }

        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = cipher.getIV();
        byte[] encryptedData = cipher.doFinal(data);

        byte[] ivAndEncryptedData = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, ivAndEncryptedData, 0, iv.length);
        System.arraycopy(encryptedData, 0, ivAndEncryptedData, iv.length, encryptedData.length);

        return Base64.encodeToString(ivAndEncryptedData, Base64.DEFAULT);
    }

    /**
     * Decrypt byte array
     * @param encryptedText Base64 encoded encrypted data with IV prepended
     * @return Decrypted byte array
     * @throws Exception If decryption fails
     */
    public byte[] decryptBytes(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }

        byte[] decodedData = Base64.decode(encryptedText, Base64.DEFAULT);

        byte[] iv = new byte[16];
        System.arraycopy(decodedData, 0, iv, 0, 16);

        byte[] encryptedData = new byte[decodedData.length - 16];
        System.arraycopy(decodedData, 16, encryptedData, 0, encryptedData.length);

        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        return cipher.doFinal(encryptedData);
    }
}


package com.example.expressionphotobooth.data.security;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Secure image storage service
 * Handles: Image compression -> Encryption -> Internal storage
 * Follows secure storage best practices
 */
public class SecureImageStorageService {
    private static final String INTERNAL_STORAGE_DIR = "secure_images";
    private final Context context;
    private final EncryptionUtil encryptionUtil;

    public SecureImageStorageService(Context context) throws Exception {
        this.context = context;
        this.encryptionUtil = EncryptionUtil.getInstance();
        ensureStorageDirectoryExists();
    }

    /**
     * Ensure internal storage directory exists with proper permissions
     */
    private void ensureStorageDirectoryExists() {
        File storageDir = getStorageDirectory();
        if (!storageDir.exists()) {
            storageDir.mkdirs();
            // Set restrictive permissions (readable/writable by app only)
            storageDir.setReadable(false, false);
            storageDir.setWritable(false, false);
            storageDir.setReadable(true, true);
            storageDir.setWritable(true, true);
        }
    }

    /**
     * Get internal storage directory for secure image storage
     */
    private File getStorageDirectory() {
        return new File(context.getFilesDir(), INTERNAL_STORAGE_DIR);
    }

    /**
     * Save image securely with encryption
     * Process: Compress -> Encrypt -> Save to internal storage
     *
     * @param bitmap Image bitmap to save
     * @param userId User ID (for file organization)
     * @param sessionId Session ID (for file naming)
     * @return File path if successful, null otherwise
     */
    public String saveSecureImage(Bitmap bitmap, String userId, String sessionId) {
        if (bitmap == null || userId == null || userId.isEmpty() || sessionId == null) {
            return null;
        }

        try {
            // Step 1: Compress image to PNG bytes
            byte[] compressedData = compressImage(bitmap);
            if (compressedData == null || compressedData.length == 0) {
                return null;
            }

            // Step 2: Encrypt compressed data
            String encryptedData = encryptionUtil.encryptBytes(compressedData);
            if (encryptedData == null || encryptedData.isEmpty()) {
                return null;
            }

            // Step 3: Save to internal storage
            String fileName = generateFileName(userId, sessionId);
            File file = new File(getStorageDirectory(), fileName);

            // Create user subdirectory if needed
            File userDir = new File(getStorageDirectory(), userId);
            if (!userDir.exists()) {
                userDir.mkdirs();
                userDir.setReadable(false, false);
                userDir.setWritable(false, false);
                userDir.setReadable(true, true);
                userDir.setWritable(true, true);
            }

            file = new File(userDir, fileName);

            // Write encrypted data to file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(encryptedData.getBytes());
                fos.flush();
            }

            // Set restrictive file permissions (readable/writable by app only)
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);

            return file.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load secure image from internal storage
     * Process: Read -> Decrypt -> Decompress -> Return bitmap
     *
     * @param filePath File path returned from saveSecureImage
     * @return Decrypted bitmap or null if fails
     */
    public Bitmap loadSecureImage(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }

            // Step 1: Read encrypted data from file
            String encryptedData = readFileAsString(file);
            if (encryptedData == null || encryptedData.isEmpty()) {
                return null;
            }

            // Step 2: Decrypt data
            byte[] decryptedData = encryptionUtil.decryptBytes(encryptedData);
            if (decryptedData == null || decryptedData.length == 0) {
                return null;
            }

            // Step 3: Decompress and decode bitmap
            return decompressImage(decryptedData);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Delete secure image file
     * Overwrites data before deletion for extra security
     */
    public boolean deleteSecureImage(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return true; // Already deleted
            }

            // Overwrite file with random data before deletion
            overwriteFileWithRandomData(file);

            // Delete the file
            return file.delete();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Compress image to PNG byte array
     */
    private byte[] compressImage(Bitmap bitmap) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decompress image from byte array
     */
    private Bitmap decompressImage(byte[] data) {
        try {
            return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate encrypted file name
     */
    private String generateFileName(String userId, String sessionId) {
        return "img_" + sessionId + ".enc";
    }

    /**
     * Read file content as string
     */
    private String readFileAsString(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        fis.read(data);
        fis.close();
        return new String(data);
    }

    /**
     * Overwrite file with random data before deletion
     * Prevents data recovery from disk
     */
    private void overwriteFileWithRandomData(File file) throws IOException {
        long fileSize = file.length();
        byte[] randomData = new byte[(int) Math.min(fileSize, 1024 * 1024)]; // Max 1MB per write
        java.security.SecureRandom random = new java.security.SecureRandom();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            long remaining = fileSize;
            while (remaining > 0) {
                random.nextBytes(randomData);
                int toWrite = (int) Math.min(remaining, randomData.length);
                fos.write(randomData, 0, toWrite);
                remaining -= toWrite;
            }
            fos.flush();
        }
    }

    /**
     * Get secure image storage info for debugging
     */
    public String getStorageInfo() {
        File storageDir = getStorageDirectory();
        return "Storage Dir: " + storageDir.getAbsolutePath() + 
               "\nExists: " + storageDir.exists() +
               "\nTotal Space: " + (storageDir.getTotalSpace() / 1024 / 1024) + "MB" +
               "\nFree Space: " + (storageDir.getFreeSpace() / 1024 / 1024) + "MB";
    }
}





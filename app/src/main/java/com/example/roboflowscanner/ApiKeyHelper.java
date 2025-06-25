package com.example.roboflowscanner;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class ApiKeyHelper {
    private static final String KEY_ALIAS = "RoboflowApiKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    public static boolean isValidApiKey(String apiKey) {
        return apiKey != null && apiKey.length() == 20; // Contoh validasi sederhana
    }
    public static void saveApiKey(Context context, String apiKey) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());

                keyGenerator.generateKey();
            }

            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT);
            String encryptedBase64 = Base64.encodeToString(encrypted, Base64.DEFAULT);

            // Simpan IV dan encrypted key ke SharedPreferences
            context.getSharedPreferences("ApiKeyPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("iv", ivBase64)
                    .putString("apiKey", encryptedBase64)
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static String getApiKey(Context context) {
        try {
            String ivBase64 = context.getSharedPreferences("ApiKeyPrefs", Context.MODE_PRIVATE)
                    .getString("iv", null);
            String encryptedBase64 = context.getSharedPreferences("ApiKeyPrefs", Context.MODE_PRIVATE)
                    .getString("apiKey", null);

            if (ivBase64 == null || encryptedBase64 == null) return null;

            byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);
            byte[] encrypted = Base64.decode(encryptedBase64, Base64.DEFAULT);

            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
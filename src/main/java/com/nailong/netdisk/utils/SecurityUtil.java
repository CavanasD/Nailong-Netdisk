package com.nailong.netdisk.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class SecurityUtil {

    private static final String KEY = "NailongNetDiskKey"; // 16 bytes for AES-128 if needed, padding...
    private static final String LOG_FILE = "waf_events.log";

    // Simple reversible encryption (AES)
    public static String encryptSecID(String content) {
        try {
            // Fix key directly to 16 bytes
            String keyStr = String.format("%-16s", KEY).substring(0, 16);
            SecretKeySpec secretKey = new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return "ERROR_GEN_SECID";
        }
    }

    public static String decryptSecID(String secId) {
        try {
            String keyStr = String.format("%-16s", KEY).substring(0, 16);
            SecretKeySpec secretKey = new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] original = cipher.doFinal(Base64.getUrlDecoder().decode(secId));
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "INVALID_SECID";
        }
    }

    public static String generateSecID(String type, String userId) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // Format: TIME|TYPE|UID
        String raw = time + "|" + type + "|" + (userId == null ? "ANON" : userId);
        return encryptSecID(raw);
    }

    public static void logSecurityEvent(String secId, String rawContent) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(LocalDateTime.now() + " [SEC_EVENT] ID:" + secId + " Content:" + rawContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


package com.miao.login.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AuthMe 密码哈希验证工具
 *
 * 支持 AuthMe 默认的几种哈希格式:
 * - $SHA$salt$hash          (SHA256, AuthMe 默认)
 * - $SHA$<strong>$salt$hash (高强度 SHA256, 迭代次数)
 * - MD5/<hash>              (无盐 MD5, 老版本)
 * - SHA256/<hash>           (无盐 SHA256)
 * - <plain>                 (明文, 不推荐)
 *
 * 不支持 BCrypt/Argon2/PBKDF2 (需要第三方库, 接管后建议玩家重置密码)
 */
public final class AuthMeHashUtil {

    private AuthMeHashUtil() {
    }

    /**
     * 验证 AuthMe 格式的密码
     *
     * @param plainPassword 玩家输入的明文密码
     * @param storedHash    AuthMe 数据库中存储的哈希字符串
     * @return 是否匹配
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) return false;
        storedHash = storedHash.trim();

        try {
            // $SHA$salt$hash 格式 (AuthMe 默认)
            if (storedHash.startsWith("$SHA$")) {
                return verifySHA(plainPassword, storedHash);
            }
            // $SHA$<strong>$salt$hash 格式 (高强度)
            if (storedHash.startsWith("$SHA$<strong>")) {
                return verifyStrongSHA(plainPassword, storedHash);
            }
            // MD5/<hash> 格式
            if (storedHash.startsWith("MD5$")) {
                String hash = storedHash.substring(4);
                return safeEquals(md5Hex(plainPassword), hash.toLowerCase());
            }
            // SHA256/<hash> 格式
            if (storedHash.startsWith("SHA256$")) {
                String hash = storedHash.substring(7);
                return safeEquals(sha256Hex(plainPassword), hash.toLowerCase());
            }
            // 纯 MD5 (32位 hex)
            if (storedHash.length() == 32 && isHex(storedHash)) {
                return safeEquals(md5Hex(plainPassword), storedHash.toLowerCase());
            }
            // 纯 SHA256 (64位 hex)
            if (storedHash.length() == 64 && isHex(storedHash)) {
                return safeEquals(sha256Hex(plainPassword), storedHash.toLowerCase());
            }
            // 明文 (不推荐, 但兼容)
            return plainPassword.equals(storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 $SHA$salt$hash 格式
     * 算法: hash = SHA256( SHA256(password) + salt )
     */
    private static boolean verifySHA(String password, String stored) {
        // stored = "$SHA$salt$hash"
        String[] parts = stored.split("\\$");
        // parts = ["", "SHA", "salt", "hash"]
        if (parts.length != 4) return false;
        String salt = parts[2];
        String expectedHash = parts[3];

        String innerHash = sha256Hex(password);
        String actualHash = sha256Hex(innerHash + salt);
        return safeEquals(actualHash, expectedHash.toLowerCase());
    }

    /**
     * 验证 $SHA$<strong>$salt$hash 格式 (高强度 SHA256)
     * 算法: 多次迭代
     */
    private static boolean verifyStrongSHA(String password, String stored) {
        // 简化处理: 当作普通 SHA 处理
        // 真正的 <strong> 格式需要解析迭代次数, 这里回退到普通 SHA
        return verifySHA(password, stored.replace("<strong>", ""));
    }

    /**
     * 计算字符串的 SHA-256 哈希 (十六进制小写)
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /**
     * 计算字符串的 MD5 哈希 (十六进制小写)
     */
    private static String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 不可用", e);
        }
    }

    /**
     * 字节数组转十六进制字符串 (小写)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 恒定时间字符串比较 (防时序攻击)
     */
    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 判断字符串是否为十六进制
     */
    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
package com.ferbator.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class HashGenerator {

    /**
     * Генерирует короткий хеш для (URL + UUID).
     *
     * @param originalUrl Оригинальная ссылка
     * @param userUuid    UUID пользователя
     * @return Строка вида "abc123", зависящая от userUuid
     */
    public static String hashUrlForUser(String originalUrl, UUID userUuid) {
        try {

            String randomSalt = UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String input = originalUrl + userUuid.toString() + randomSalt + now;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            String fullHex = bytesToHex(hashBytes);

            return fullHex.substring(0, 8);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ошибка: не найден алгоритм SHA-256", e);
        }
    }

    /**
     * Вспомогательный метод для конвертации массива байтов в hex-строку.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}


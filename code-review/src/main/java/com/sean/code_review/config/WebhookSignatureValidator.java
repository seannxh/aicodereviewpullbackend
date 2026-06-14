package com.sean.code_review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Component
public class WebhookSignatureValidator {

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    public boolean isValid(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder("sha256=");
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString().equals(signatureHeader);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}
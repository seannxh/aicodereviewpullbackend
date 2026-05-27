package com.sean.code_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import okhttp3.*;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Date;

/**
 * Handles GitHub App authentication.
 *
 * Flow:
 *  1. Build a short-lived JWT signed with the app's RSA private key.
 *  2. Exchange the JWT for a per-installation access token via the GitHub API.
 *  3. Cache the installation token in Redis for 55 min (tokens expire after 60 min).
 *
 * Every user who installs your GitHub App gets a unique installation_id.
 * That id arrives in every webhook payload under installation.id.
 * Pass it to getInstallationToken() to get the right token for that repo.
 */
@Service
public class GitHubAppAuthService {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private String privateKeyPath;

    private final StringRedisTemplate redisTemplate;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubAppAuthService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns a valid installation access token for the given installation.
     * Result is cached in Redis so repeated calls within the same hour are cheap.
     */
    public String getInstallationToken(long installationId) throws Exception {
        String cacheKey = "github:installation:token:" + installationId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String jwt = generateAppJwt();
        String token = fetchInstallationToken(jwt, installationId);

        // Cache for 55 minutes — GitHub tokens expire after 60, give 5 min buffer
        redisTemplate.opsForValue().set(cacheKey, token, Duration.ofMinutes(55));
        return token;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a 9-minute JWT signed with RS256 using the app's private key.
     * GitHub requires iss = app id, iat slightly in the past to handle clock skew.
     */
    private String generateAppJwt() throws Exception {
        RSAPrivateKey privateKey = loadPrivateKey();
        long nowMs = System.currentTimeMillis();

        return Jwts.builder()
                .issuer(appId)
                .issuedAt(new Date(nowMs - 60_000))       // 1 min in the past (clock drift tolerance)
                .expiration(new Date(nowMs + 9 * 60_000)) // 9 minutes from now
                .signWith(privateKey)                      // JJWT auto-selects RS256 for RSAPrivateKey
                .compact();
    }

    /**
     * Reads the PEM file from disk and parses the RSA private key.
     * Supports both PKCS#1 ("BEGIN RSA PRIVATE KEY") and PKCS#8 ("BEGIN PRIVATE KEY")
     * formats using Bouncy Castle.
     */
    private RSAPrivateKey loadPrivateKey() throws Exception {
        String pem = Files.readString(Paths.get(privateKeyPath));

        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

            if (obj instanceof PEMKeyPair keyPair) {
                // PKCS#1 format — "-----BEGIN RSA PRIVATE KEY-----"
                return (RSAPrivateKey) converter.getKeyPair(keyPair).getPrivate();
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo keyInfo) {
                // PKCS#8 format — "-----BEGIN PRIVATE KEY-----"
                return (RSAPrivateKey) converter.getPrivateKey(keyInfo);
            } else {
                throw new IllegalStateException("Unrecognised PEM object type: " + obj.getClass().getName());
            }
        }
    }

    /**
     * Calls POST /app/installations/{id}/access_tokens with the app JWT.
     * Returns the token string from the response.
     */
    private String fetchInstallationToken(String jwt, long installationId) throws IOException {
        String url = "https://api.github.com/app/installations/" + installationId + "/access_tokens";

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + jwt)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "(empty)";
            if (!response.isSuccessful()) {
                throw new IOException(
                        "Failed to get installation token for installation " + installationId
                        + ": HTTP " + response.code() + " — " + body);
            }
            JsonNode root = objectMapper.readTree(body);
            return root.path("token").asText();
        }
    }
}

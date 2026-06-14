package com.sean.code_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.Security;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles GitHub App authentication:
 *   1. Generates a short-lived JWT signed with the App's RSA private key
 *   2. Exchanges the JWT for a per-installation access token (valid 1 hour)
 *   3. Caches tokens so we don't hit the API on every webhook call
 *
 * Private key loading priority:
 *   - GITHUB_PRIVATE_KEY env var (full PEM content, literal \n accepted)
 *   - GITHUB_PRIVATE_KEY_PATH env var / github.app.private-key-path (path to .pem file)
 */
@Service
public class GitHubAppAuthService {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key:}")
    private String privateKeyEnv;

    @Value("${github.app.private-key-path:}")
    private String privateKeyPath;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // installationId -> cached token
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a valid installation access token for the given installation.
     * Fetches a fresh one from GitHub if the cached token is expired or missing.
     */
    public String getInstallationToken(long installationId) throws Exception {
        CachedToken cached = tokenCache.get(installationId);
        if (cached != null && !cached.isExpired()) {
            return cached.token();
        }

        String jwt = generateAppJwt();
        String token = fetchInstallationToken(installationId, jwt);

        // Cache with a 55-minute TTL (tokens last 1 hour; we refresh 5 min early)
        tokenCache.put(installationId, new CachedToken(token, Instant.now().plusSeconds(3300)));
        return token;
    }

    // -------------------------------------------------------------------------
    // JWT generation
    // -------------------------------------------------------------------------

    /**
     * Builds a signed RS256 JWT for authenticating as the GitHub App itself.
     * GitHub requires iat to be backdated 60 s to tolerate clock drift.
     */
    private String generateAppJwt() throws Exception {
        PrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(appId)
                .issuedAt(Date.from(now.minusSeconds(60)))   // clock-drift buffer
                .expiration(Date.from(now.plusSeconds(540))) // 9 min (max is 10)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    // -------------------------------------------------------------------------
    // Private key loading
    // -------------------------------------------------------------------------

    private PrivateKey loadPrivateKey() throws Exception {
        String pem = resolvePem();

        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();

            if (obj == null) {
                throw new IllegalStateException(
                        "PEM parser returned null — check that the private key is valid PEM format.");
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (obj instanceof PEMKeyPair keyPair) {
                // Traditional RSA format: -----BEGIN RSA PRIVATE KEY-----
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }

            if (obj instanceof PrivateKeyInfo info) {
                // PKCS#8 format: -----BEGIN PRIVATE KEY-----
                return converter.getPrivateKey(info);
            }

            throw new IllegalArgumentException(
                    "Unrecognised PEM object type: " + obj.getClass().getName()
                    + ". Expected RSA PRIVATE KEY or PRIVATE KEY.");
        }
    }

    /**
     * Resolves the raw PEM string from either the env-var value or a file on disk.
     * Env vars set in Docker/CI often use literal backslash-n; we normalise those.
     */
    private String resolvePem() throws IOException {
        if (privateKeyEnv != null && !privateKeyEnv.isBlank()) {
            return privateKeyEnv.replace("\\n", "\n");
        }

        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            return Files.readString(Paths.get(privateKeyPath));
        }

        throw new IllegalStateException(
                "No GitHub App private key configured. "
                + "Set GITHUB_PRIVATE_KEY (full PEM) or GITHUB_PRIVATE_KEY_PATH (path to .pem file).");
    }

    // -------------------------------------------------------------------------
    // GitHub API call — exchange JWT for installation token
    // -------------------------------------------------------------------------

    private String fetchInstallationToken(long installationId, String jwt) throws IOException {
        String url = "https://api.github.com/app/installations/" + installationId + "/access_tokens";

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + jwt)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "(empty body)";
            if (!response.isSuccessful()) {
                throw new IOException(
                        "Failed to fetch installation token for installation " + installationId
                        + ": HTTP " + response.code() + " — " + body);
            }
            JsonNode json = objectMapper.readTree(body);
            String token = json.path("token").asText();
            if (token == null || token.isBlank()) {
                throw new IOException("GitHub returned no token in response: " + body);
            }
            return token;
        }
    }

    // -------------------------------------------------------------------------
    // Token cache record
    // -------------------------------------------------------------------------

    private record CachedToken(String token, Instant expiresAt) {
        /** Returns true if the token should be refreshed (within 60 s of expiry). */
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minusSeconds(60));
        }
    }
}

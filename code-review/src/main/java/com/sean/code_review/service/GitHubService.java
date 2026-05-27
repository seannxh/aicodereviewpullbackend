package com.sean.code_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sean.code_review.model.entity.ReviewComment;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GitHubService {

    /** PAT used as a fallback when no installationId is available (local dev, manual reviews). */
    @Value("${github.app.token}")
    private String patToken;

    private final GitHubAppAuthService gitHubAppAuthService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubService(GitHubAppAuthService gitHubAppAuthService) {
        this.gitHubAppAuthService = gitHubAppAuthService;
    }

    // -------------------------------------------------------------------------
    // Public methods — all accept installationId; pass 0 to use PAT fallback
    // -------------------------------------------------------------------------

    public String getPullRequestDiff(String repoFullName, int prNumber,
                                     long installationId) throws Exception {
        String token = resolveToken(installationId);
        String url = "https://api.github.com/repos/" + repoFullName + "/pulls/" + prNumber;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github.v3.diff")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API error fetching diff: " + response.code());
            }
            return response.body().string();
        }
    }

    public String getLatestCommitSha(String repoFullName, int prNumber,
                                     long installationId) throws Exception {
        String token = resolveToken(installationId);
        String url = "https://api.github.com/repos/" + repoFullName + "/pulls/" + prNumber;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API error fetching PR: " + response.code());
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return root.path("head").path("sha").asText();
        }
    }

    public void postReviewComments(String repoFullName, int prNumber,
                                   String commitSha, List<ReviewComment> comments,
                                   long installationId) throws Exception {
        String token = resolveToken(installationId);
        String url = "https://api.github.com/repos/" + repoFullName
                + "/pulls/" + prNumber + "/comments";

        for (ReviewComment comment : comments) {
            if (comment.getFilePath() == null || comment.getFilePath().isEmpty()) continue;

            String body = buildCommentBody(comment);
            String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("body", body);
                put("commit_id", commitSha);
                put("path", comment.getFilePath());
                put("line", comment.getLineNumber() != null ? comment.getLineNumber() : 1);
                put("side", "RIGHT");
            }});

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Failed to post comment: " + response.code()
                            + " — " + (response.body() != null ? response.body().string() : ""));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Token resolution — installation token if we have an ID, PAT otherwise
    // -------------------------------------------------------------------------

    /**
     * Returns an installation access token when installationId is a valid non-zero
     * value, or falls back to the PAT for local development and manual reviews.
     */
    private String resolveToken(long installationId) throws Exception {
        if (installationId > 0) {
            return gitHubAppAuthService.getInstallationToken(installationId);
        }
        System.out.println("[GitHubService] No installationId — using PAT fallback");
        return patToken;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildCommentBody(ReviewComment comment) {
        String emoji = switch (comment.getSeverity()) {
            case "error"   -> "🔴";
            case "warning" -> "🟡";
            default        -> "🔵";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append(" **").append(comment.getSeverity().toUpperCase()).append("**\n\n");
        sb.append(comment.getComment());

        if (comment.getSuggestion() != null && !comment.getSuggestion().isEmpty()) {
            sb.append("\n\n**Suggestion:** ").append(comment.getSuggestion());
        }

        return sb.toString();
    }
}

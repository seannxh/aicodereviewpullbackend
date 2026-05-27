package com.sean.code_review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sean.code_review.model.entity.Review;
import com.sean.code_review.model.entity.ReviewComment;
import com.sean.code_review.repository.ReviewCommentRepository;
import com.sean.code_review.repository.ReviewRepository;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClaudeReviewService {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    @Value("${claude.api.url}")
    private String claudeApiUrl;

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final GitHubService gitHubService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public ClaudeReviewService(ReviewRepository reviewRepository,
                               ReviewCommentRepository reviewCommentRepository,
                               GitHubService gitHubService) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.gitHubService = gitHubService;
    }

    public void reviewPullRequest(Review review) {
        // Resolve installation ID — default to 0 so GitHubService falls back to PAT
        long installationId = review.getInstallationId() != null ? review.getInstallationId() : 0L;

        try {
            review.setStatus("processing");
            reviewRepository.save(review);

            String diff = gitHubService.getPullRequestDiff(
                    review.getRepoFullName(), review.getPrNumber(), installationId);

            System.out.println("Diff length: " + (diff != null ? diff.length() : "null"));
            System.out.println("Diff preview: " + (diff != null && diff.length() > 200
                    ? diff.substring(0, 200) : diff));

            if (diff == null || diff.trim().isEmpty()) {
                review.setStatus("complete");
                reviewRepository.save(review);
                System.out.println("No diff found for PR #" + review.getPrNumber());
                return;
            }

            String commitSha = gitHubService.getLatestCommitSha(
                    review.getRepoFullName(), review.getPrNumber(), installationId);

            String prompt = buildPrompt(diff);
            String claudeResponse = callClaudeApi(prompt);
            List<ReviewComment> comments = parseComments(claudeResponse, review);

            reviewCommentRepository.saveAll(comments);

            if (!comments.isEmpty()) {
                gitHubService.postReviewComments(
                        review.getRepoFullName(), review.getPrNumber(),
                        commitSha, comments, installationId);
            }

            review.setStatus("complete");
            reviewRepository.save(review);

            System.out.println("Review complete. Found " + comments.size() + " comments.");

        } catch (Exception e) {
            review.setStatus("failed");
            reviewRepository.save(review);
            System.err.println("Review failed for PR #" + review.getPrNumber()
                    + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    private String buildPrompt(String diff) {
        return """
            You are a senior code reviewer. Analyze this PR diff and find issues.

            Respond with a JSON array only, no other text:
            [
              {
                "file": "path/to/file.java",
                "line": 42,
                "severity": "error",
                "comment": "Description of the issue",
                "suggestion": "How to fix it"
              }
            ]

            Severity levels: "error", "warning", "suggestion"
            If no issues found return: []

            Diff:
            """ + diff;
    }

    // -------------------------------------------------------------------------
    // Claude API call
    // -------------------------------------------------------------------------

    private String callClaudeApi(String prompt) throws IOException {
        String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("model", "claude-sonnet-4-5");
            put("max_tokens", 4096);
            put("messages", List.of(new java.util.HashMap<>() {{
                put("role", "user");
                put("content", prompt);
            }}));
        }});

        Request request = new Request.Builder()
                .url(claudeApiUrl)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("x-api-key", claudeApiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Claude API error: " + response.code());
            }
            return response.body().string();
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private List<ReviewComment> parseComments(String claudeResponse, Review review) {
        List<ReviewComment> comments = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(claudeResponse);
            String content = root.path("content").get(0).path("text").asText();
            System.out.println("Claude raw response: " + content);

            // Strip markdown code fences if present
            String jsonArray = content.trim();
            if (jsonArray.startsWith("```")) {
                jsonArray = jsonArray.replaceAll("```json\\s*", "")
                                     .replaceAll("```\\s*", "")
                                     .trim();
            }

            if (jsonArray.startsWith("[")) {
                JsonNode commentsNode = objectMapper.readTree(jsonArray);
                for (JsonNode node : commentsNode) {
                    ReviewComment comment = new ReviewComment();
                    comment.setReview(review);
                    comment.setFilePath(node.path("file").asText());
                    comment.setLineNumber(node.path("line").asInt());
                    comment.setSeverity(node.path("severity").asText());
                    comment.setComment(node.path("comment").asText());
                    comment.setSuggestion(node.path("suggestion").asText());
                    comment.setPostedToGithub(false);
                    comments.add(comment);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Claude response: " + e.getMessage());
        }
        return comments;
    }
}

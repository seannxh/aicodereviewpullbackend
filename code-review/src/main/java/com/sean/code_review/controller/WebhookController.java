package com.sean.code_review.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sean.code_review.model.entity.Review;
import com.sean.code_review.repository.ReviewRepository;
import com.sean.code_review.service.ClaudeReviewService;
import com.sean.code_review.config.WebhookSignatureValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookSignatureValidator signatureValidator;
    private final ReviewRepository reviewRepository;
    private final ClaudeReviewService claudeReviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(WebhookSignatureValidator signatureValidator,
                             ReviewRepository reviewRepository,
                             ClaudeReviewService claudeReviewService) {
        this.signatureValidator = signatureValidator;
        this.reviewRepository = reviewRepository;
        this.claudeReviewService = claudeReviewService;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGithubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String payload) {

        if (!signatureValidator.isValid(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("Event ignored");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String action = root.path("action").asText();

            if (!"opened".equals(action) && !"synchronize".equals(action)) {
                return ResponseEntity.ok("Action ignored");
            }

            String repoFullName = root.path("repository").path("full_name").asText();
            int    prNumber     = root.path("number").asInt();
            String prTitle      = root.path("pull_request").path("title").asText();

            // GitHub App webhooks include an installation block; extract the ID so
            // we can request a scoped installation token instead of using the PAT.
            long installationId = root.path("installation").path("id").asLong(0);

            Review review = new Review();
            review.setRepoFullName(repoFullName);
            review.setPrNumber(prNumber);
            review.setPrTitle(prTitle);
            review.setStatus("pending");
            review.setInstallationId(installationId > 0 ? installationId : null);
            reviewRepository.save(review);

            // Run review async so webhook returns 200 immediately
            new Thread(() -> claudeReviewService.reviewPullRequest(review)).start();

            return ResponseEntity.ok("Review queued for PR #" + prNumber);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }
}

package com.sean.code_review.controller;

import com.sean.code_review.model.entity.Review;
import com.sean.code_review.repository.ReviewRepository;
import com.sean.code_review.service.ClaudeReviewService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = {"http://localhost:5173", "http://codereview-lb-1137663267.us-east-1.elb.amazonaws.com"}, allowCredentials = "true")
public class  ReviewController {

    private final ReviewRepository reviewRepository;
    private final ClaudeReviewService claudeReviewService;

    public ReviewController(ReviewRepository reviewRepository,
                            ClaudeReviewService claudeReviewService) {
        this.reviewRepository = reviewRepository;
        this.claudeReviewService = claudeReviewService;
    }

    @GetMapping
    public ResponseEntity<java.util.List<Review>> listReviews() {
        return ResponseEntity.ok(reviewRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Review> getReview(@PathVariable String id) {
        return reviewRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Review> startManualReview(@RequestBody ReviewRequest request) {
        String repoFullName = request.getRepoUrl()
                .replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .replace(".git", "")
                .trim();

        Review review = new Review();
        review.setRepoFullName(repoFullName);
        review.setPrNumber(request.getPrNumber());
        review.setPrTitle("Manual Review");
        review.setStatus("pending");

        reviewRepository.save(review);

        claudeReviewService.reviewPullRequest(review);

        return ResponseEntity.ok(review);
    }

    @Setter
    @Getter
    public static class ReviewRequest {
        private String repoUrl;
        private int prNumber;

    }
}
package com.sean.code_review.repository;

import com.sean.code_review.model.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, String> {
    List<ReviewComment> findByReviewId(String reviewId);
    List<ReviewComment> findByReviewIdAndPostedToGithubFalse(String reviewId);
}
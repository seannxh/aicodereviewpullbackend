package com.sean.code_review.repository;

import com.sean.code_review.model.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    List<Review> findByRepoFullNameOrderByCreatedAtDesc(String repoFullName);
    List<Review> findByStatus(String status);
}
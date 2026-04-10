package com.aicareerlab.repository;

import com.aicareerlab.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    @Query("SELECT r FROM Recommendation r JOIN FETCH r.jobPosting WHERE r.user.id = :userId ORDER BY r.sentAt DESC")
    List<Recommendation> findByUserIdOrderBySentAtDesc(@Param("userId") Long userId);

    @Query("SELECT r.jobPosting.id FROM Recommendation r WHERE r.user.id = :userId")
    Set<Long> findRecommendedJobIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT r.jobPosting.url FROM Recommendation r WHERE r.user.id = :userId")
    Set<String> findRecommendedJobUrlsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Query("SELECT r.user.id, COUNT(r) FROM Recommendation r GROUP BY r.user.id")
    List<Object[]> countGroupByUserId();
}

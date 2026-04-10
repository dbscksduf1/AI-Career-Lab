package com.aicareerlab.repository;

import com.aicareerlab.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(Long userId);

    @Query("SELECT p FROM UserProfile p JOIN FETCH p.user")
    List<UserProfile> findAllWithUser();
}

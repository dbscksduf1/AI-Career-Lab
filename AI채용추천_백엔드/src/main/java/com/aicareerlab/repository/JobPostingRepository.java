package com.aicareerlab.repository;

import com.aicareerlab.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findByUrl(String url);
    boolean existsByUrl(String url);

    @Query("SELECT j.url FROM JobPosting j WHERE j.url IN :urls")
    Set<String> findExistingUrls(@Param("urls") Collection<String> urls);
}

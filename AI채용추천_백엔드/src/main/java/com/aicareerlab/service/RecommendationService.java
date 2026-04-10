package com.aicareerlab.service;

import com.aicareerlab.dto.RecommendationGroupResponse;
import com.aicareerlab.entity.Recommendation;
import com.aicareerlab.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;

    public List<RecommendationGroupResponse> getGroupedByDate(Long userId) {
        List<Recommendation> all = recommendationRepository.findByUserIdOrderBySentAtDesc(userId);

        // 날짜별 그룹핑
        Map<LocalDate, List<Recommendation>> grouped = all.stream()
                .collect(Collectors.groupingBy(r -> r.getSentAt().toLocalDate()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Recommendation>>comparingByKey().reversed())
                .map(e -> RecommendationGroupResponse.from(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}

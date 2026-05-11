package com.uaeitjobs.service;

import com.uaeitjobs.dto.SubscriptionDTO;
import com.uaeitjobs.entity.HRSubscription;
import com.uaeitjobs.entity.User;
import com.uaeitjobs.exception.ValidationException;
import com.uaeitjobs.repository.HRSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final HRSubscriptionRepository repository;

    public HRSubscription currentEntity(User user) {
        return repository.findFirstByUserAndActiveTrueOrderByStartedAtDesc(user).orElseGet(() -> create(user, "free"));
    }

    public SubscriptionDTO.Response current(User user) {
        return toResponse(currentEntity(user));
    }

    public void assertCanPost(User user) {
        HRSubscription subscription = currentEntity(user);
        if (subscription.getJobsPosted() >= subscription.getMonthlyJobLimit()) {
            throw new ValidationException("Monthly job posting limit reached");
        }
    }

    @Transactional
    public void incrementPosted(User user) {
        HRSubscription subscription = currentEntity(user);
        subscription.setJobsPosted(subscription.getJobsPosted() + 1);
    }

    @Transactional
    public SubscriptionDTO.Response upgrade(User user, String tier) {
        repository.findFirstByUserAndActiveTrueOrderByStartedAtDesc(user).ifPresent(existing -> existing.setActive(false));
        return toResponse(create(user, tier));
    }

    private HRSubscription create(User user, String tier) {
        HRSubscription subscription = new HRSubscription();
        subscription.setUser(user);
        subscription.setTier(tier);
        subscription.setExpiresAt(OffsetDateTime.now().plusDays(30));
        switch (tier) {
            case "starter" -> {
                subscription.setFeaturedJobsLimit(2);
                subscription.setMonthlyJobLimit(10);
            }
            case "professional" -> {
                subscription.setFeaturedJobsLimit(10);
                subscription.setMonthlyJobLimit(50);
            }
            case "free" -> {
                subscription.setFeaturedJobsLimit(0);
                subscription.setMonthlyJobLimit(1);
            }
            default -> throw new ValidationException("Unknown subscription tier");
        }
        return repository.save(subscription);
    }

    private SubscriptionDTO.Response toResponse(HRSubscription subscription) {
        return new SubscriptionDTO.Response(subscription.getId(), subscription.getTier(), subscription.getFeaturedJobsLimit(), subscription.getFeaturedJobsUsed(), subscription.getMonthlyJobLimit(), subscription.getJobsPosted(), subscription.getStartedAt(), subscription.getExpiresAt(), subscription.isActive());
    }
}

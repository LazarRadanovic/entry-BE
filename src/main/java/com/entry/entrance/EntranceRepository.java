package com.entry.entrance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntranceRepository extends JpaRepository<Entrance, UUID> {

    Optional<Entrance> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    Optional<Entrance> findByStripeCustomerId(String stripeCustomerId);

    /** Entrances whose free trial ends in the window and that have no Stripe subscription. */
    @Query("select e from Entrance e where e.trialEndsAt > :from and e.trialEndsAt <= :to and e.stripeSubscriptionId is null")
    List<Entrance> findTrialsEndingBetween(@Param("from") Instant from, @Param("to") Instant to);
}

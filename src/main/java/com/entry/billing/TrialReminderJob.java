package com.entry.billing;

import com.entry.entrance.EntranceRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Once a day, reminds admins whose free trial ends in 7, 3 or 1 day(s). Each run looks at a
 * one-day window per reminder, so every entrance gets each reminder once.
 */
@Component
@RequiredArgsConstructor
public class TrialReminderJob {

    private static final long[] REMIND_AT_DAYS = {7, 3, 1};

    private final EntranceRepository entrances;
    private final BillingPolicy policy;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "${app.billing.reminder-cron:0 0 9 * * *}", zone = "UTC")
    @Transactional(readOnly = true)
    public void remind() {
        if (!policy.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        for (long days : REMIND_AT_DAYS) {
            Instant to = now.plus(Duration.ofDays(days));
            Instant from = to.minus(Duration.ofDays(1));
            entrances.findTrialsEndingBetween(from, to)
                    .forEach(e -> events.publishEvent(new TrialEndingEvent(e.getId(), days)));
        }
    }
}

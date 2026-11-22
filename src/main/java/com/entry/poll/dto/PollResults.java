package com.entry.poll.dto;

import java.util.UUID;

/** Percentages are whole numbers that always add up to 100 (or both 0 with no votes). */
public record PollResults(UUID pollId, long yesCount, long noCount, long totalVotes, int yesPercent, int noPercent) {

    public static PollResults of(UUID pollId, long yes, long no) {
        long total = yes + no;
        int yesPercent = total == 0 ? 0 : (int) Math.round(yes * 100.0 / total);
        int noPercent = total == 0 ? 0 : 100 - yesPercent;
        return new PollResults(pollId, yes, no, total, yesPercent, noPercent);
    }
}

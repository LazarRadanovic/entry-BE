package com.entry.fee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What each apartment owes for one cost. */
public final class CostShares {

    private CostShares() {
    }

    /**
     * Shares of the given (already assigned) units. For BY_AREA, rounding leftovers go to the largest apartment
     * so the shares always add up to the total. Returns null when a BY_AREA cost has an apartment without area.
     */
    public static Map<UUID, BigDecimal> compute(CostType type, BigDecimal amount, List<FeeUnit> units) {
        Map<UUID, BigDecimal> shares = new HashMap<>();
        if (type == CostType.PER_APARTMENT) {
            units.forEach(u -> shares.put(u.getId(), amount.setScale(2, RoundingMode.HALF_UP)));
            return shares;
        }
        if (units.isEmpty()) {
            return shares;
        }
        if (units.stream().anyMatch(u -> u.getAreaM2() == null || u.getAreaM2().signum() <= 0)) {
            return null;
        }
        BigDecimal totalArea = units.stream().map(FeeUnit::getAreaM2).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = BigDecimal.ZERO;
        for (FeeUnit u : units) {
            BigDecimal share = amount.multiply(u.getAreaM2()).divide(totalArea, 2, RoundingMode.HALF_UP);
            shares.put(u.getId(), share);
            sum = sum.add(share);
        }
        BigDecimal remainder = amount.setScale(2, RoundingMode.HALF_UP).subtract(sum);
        if (remainder.signum() != 0) {
            FeeUnit largest = units.stream().max(Comparator.comparing(FeeUnit::getAreaM2)).orElseThrow();
            shares.merge(largest.getId(), remainder, BigDecimal::add);
        }
        return shares;
    }
}

package com.entry.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CostSharesTest {

    @Test
    void perApartmentEveryonePaysTheAmount() {
        List<FeeUnit> units = List.of(unit("1", null), unit("2", null));
        Map<UUID, BigDecimal> shares = CostShares.compute(CostType.PER_APARTMENT, new BigDecimal("500"), units);
        assertThat(shares.values()).containsOnly(new BigDecimal("500.00"));
    }

    @Test
    void byAreaSplitsProportionally() {
        FeeUnit small = unit("1", "40");
        FeeUnit big = unit("2", "60");
        Map<UUID, BigDecimal> shares = CostShares.compute(CostType.BY_AREA, new BigDecimal("1000"), List.of(small, big));
        assertThat(shares.get(small.getId())).isEqualByComparingTo("400");
        assertThat(shares.get(big.getId())).isEqualByComparingTo("600");
    }

    @Test
    void byAreaRemainderGoesToLargestSoSharesAddUp() {
        FeeUnit a = unit("1", "50");
        FeeUnit b = unit("2", "50");
        FeeUnit c = unit("3", "70");
        Map<UUID, BigDecimal> shares = CostShares.compute(CostType.BY_AREA, new BigDecimal("100"), List.of(a, b, c));
        // 29.41 + 29.41 + 41.18 = 100.00 (41.176... rounds to 41.18, so no remainder here); check the sum anyway
        assertThat(shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");

        FeeUnit x = unit("4", "10");
        FeeUnit y = unit("5", "10");
        FeeUnit z = unit("6", "10");
        Map<UUID, BigDecimal> thirds = CostShares.compute(CostType.BY_AREA, new BigDecimal("100"), List.of(x, y, z));
        assertThat(thirds.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(thirds.values()).containsExactlyInAnyOrder(new BigDecimal("33.34"), new BigDecimal("33.33"),
                new BigDecimal("33.33"));
    }

    @Test
    void byAreaWithMissingAreaHasNoShares() {
        assertThat(CostShares.compute(CostType.BY_AREA, BigDecimal.TEN, List.of(unit("1", "40"), unit("2", null))))
                .isNull();
    }

    private static FeeUnit unit(String apartment, String area) {
        FeeUnit u = new FeeUnit(UUID.randomUUID(), "1", apartment);
        u.setId(UUID.randomUUID());
        u.setAreaM2(area == null ? null : new BigDecimal(area));
        return u;
    }
}

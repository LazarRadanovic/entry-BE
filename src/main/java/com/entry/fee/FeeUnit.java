package com.entry.fee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One apartment in the entrance cost tables. Created automatically for residents' apartments or by the admin. */
@Entity
@Table(name = "fee_unit")
@Getter
@Setter
@NoArgsConstructor
public class FeeUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entrance_id", nullable = false)
    private UUID entranceId;

    @Column(nullable = false, length = 10)
    private String floor;

    @Column(nullable = false, length = 10)
    private String apartment;

    /** Floor area, used to split BY_AREA costs; null until the admin enters it. */
    @Column(name = "area_m2", precision = 7, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FeeUnit(UUID entranceId, String floor, String apartment) {
        this.entranceId = entranceId;
        this.floor = floor.trim();
        this.apartment = apartment.trim();
    }

    /** Case-insensitive identity of an apartment, matching the DB unique index. */
    public static String key(String floor, String apartment) {
        return floor.trim().toLowerCase(Locale.ROOT) + "|" + apartment.trim().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return key(floor, apartment);
    }
}

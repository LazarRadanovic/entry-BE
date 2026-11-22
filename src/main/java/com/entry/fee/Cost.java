package com.entry.fee;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One cost of an entrance (building cleaning, garage cleaning, a repair...). */
@Entity
@Table(name = "cost")
@Getter
@Setter
@NoArgsConstructor
public class Cost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entrance_id", nullable = false)
    private UUID entranceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CostType type;

    /** Per-apartment amount (PER_APARTMENT) or the total to split (BY_AREA). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CostRecurrence recurrence;

    /** True: every apartment of the entrance; false: only {@link #unitIds}. */
    @Column(name = "applies_to_all", nullable = false)
    private boolean appliesToAll = true;

    @ElementCollection
    @CollectionTable(name = "cost_unit", joinColumns = @JoinColumn(name = "cost_id"))
    @Column(name = "unit_id")
    private Set<UUID> unitIds = new HashSet<>();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean appliesTo(UUID unitId) {
        return appliesToAll || unitIds.contains(unitId);
    }
}

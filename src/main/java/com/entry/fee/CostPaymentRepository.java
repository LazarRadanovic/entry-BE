package com.entry.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** cost_payment is a pure (cost, unit, period) table; native queries keep it simple. */
public interface CostPaymentRepository extends Repository<Cost, UUID> {

    interface PaymentRow {
        UUID getCostId();

        UUID getUnitId();

        String getPeriod();

        Instant getPaidAt();
    }

    /** Payments of the given year ("YYYY-MM") plus one-time payments ("ONCE"). */
    @Query(value = """
            SELECT cost_id AS "costId", unit_id AS "unitId", period AS "period", paid_at AS "paidAt"
            FROM cost_payment
            WHERE cost_id IN (:costIds) AND (period LIKE :yearPrefix OR period = 'ONCE')""", nativeQuery = true)
    List<PaymentRow> findForYear(@Param("costIds") Collection<UUID> costIds, @Param("yearPrefix") String yearPrefix);

    @Query(value = "SELECT count(*) > 0 FROM cost_payment WHERE cost_id = :costId", nativeQuery = true)
    boolean anyFor(@Param("costId") UUID costId);

    @Modifying
    @Query(value = """
            INSERT INTO cost_payment (cost_id, unit_id, period, amount, marked_by)
            VALUES (:costId, :unitId, :period, :amount, :markedBy)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    int markPaid(@Param("costId") UUID costId, @Param("unitId") UUID unitId, @Param("period") String period,
            @Param("amount") BigDecimal amount, @Param("markedBy") UUID markedBy);

    @Modifying
    @Query(value = "DELETE FROM cost_payment WHERE cost_id = :costId AND unit_id = :unitId AND period = :period",
            nativeQuery = true)
    int markUnpaid(@Param("costId") UUID costId, @Param("unitId") UUID unitId, @Param("period") String period);
}

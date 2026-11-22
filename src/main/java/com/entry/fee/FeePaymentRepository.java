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

/** fee_payment is a pure (unit, year, month) table; native queries keep it simple. */
public interface FeePaymentRepository extends Repository<FeeUnit, UUID> {

    interface PaymentRow {
        UUID getUnitId();

        int getMonth();

        Instant getPaidAt();
    }

    @Query(value = """
            SELECT unit_id AS "unitId", month AS "month", paid_at AS "paidAt"
            FROM fee_payment WHERE year = :year AND unit_id IN (:unitIds)""", nativeQuery = true)
    List<PaymentRow> findForYear(@Param("year") int year, @Param("unitIds") Collection<UUID> unitIds);

    @Modifying
    @Query(value = """
            INSERT INTO fee_payment (unit_id, year, month, amount, marked_by)
            VALUES (:unitId, :year, :month, :amount, :markedBy)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    int markPaid(@Param("unitId") UUID unitId, @Param("year") int year, @Param("month") int month,
            @Param("amount") BigDecimal amount, @Param("markedBy") UUID markedBy);

    @Modifying
    @Query(value = "DELETE FROM fee_payment WHERE unit_id = :unitId AND year = :year AND month = :month",
            nativeQuery = true)
    int markUnpaid(@Param("unitId") UUID unitId, @Param("year") int year, @Param("month") int month);
}

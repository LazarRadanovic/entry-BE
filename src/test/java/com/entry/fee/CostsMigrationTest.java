package com.entry.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.entry.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** V9 turns the old single cleaning fee into a cost and keeps the paid months. Runs in its own schema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CostsMigrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cleaningFeePaymentsBecomeCostPayments() {
        String schema = "mig_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).target("8").load().migrate();

        UUID entrance = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".entrance (id, name, address, invite_code, fee_amount, fee_currency)"
                + " VALUES (?, 'E', 'A', ?, 500, 'RSD')", entrance, schema.substring(0, 16));
        jdbc.update("INSERT INTO " + schema + ".fee_unit (id, entrance_id, floor, apartment) VALUES (?, ?, '1', '2')",
                unit, entrance);
        jdbc.update("INSERT INTO " + schema + ".fee_payment (unit_id, year, month, amount) VALUES (?, 2026, 3, 500)",
                unit);

        Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

        Map<String, Object> cost = jdbc.queryForMap("SELECT * FROM " + schema + ".cost WHERE entrance_id = ?",
                entrance);
        assertThat(cost.get("name")).isEqualTo("Čišćenje zgrade");
        assertThat(cost.get("applies_to_all")).isEqualTo(true);
        assertThat(cost.get("recurrence")).isEqualTo("MONTHLY");
        assertThat(jdbc.queryForObject("SELECT period FROM " + schema + ".cost_payment WHERE unit_id = ?",
                String.class, unit)).isEqualTo("2026-03");
        jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
}

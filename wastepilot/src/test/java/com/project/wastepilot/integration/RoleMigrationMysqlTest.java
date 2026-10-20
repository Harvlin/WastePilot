package com.project.wastepilot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Seeds pre-V13 data (role lived in user_settings), runs V13, and asserts the role really moved.
 * Destructive: cleans the target schema, then restores it to the latest version.
 */
@EnabledIfEnvironmentVariable(named = "WASTEPILOT_MYSQL_TEST_URL", matches = ".+")
class RoleMigrationMysqlTest {

  private static DataSource dataSource() {
    DriverManagerDataSource ds = new DriverManagerDataSource(
        System.getenv("WASTEPILOT_MYSQL_TEST_URL"),
        System.getenv("WASTEPILOT_MYSQL_TEST_USER"),
        System.getenv("WASTEPILOT_MYSQL_TEST_PASSWORD"));
    ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return ds;
  }

  private static Flyway flyway(DataSource ds, String target) {
    return Flyway.configure().dataSource(ds).cleanDisabled(false).target(target).load();
  }

  private static void seedUser(JdbcTemplate jdbc, UUID id, String email, String settingsRole) {
    jdbc.update(
        "INSERT INTO auth_users (id, full_name, email, password_hash, created_at, updated_at) "
            + "VALUES (UNHEX(REPLACE(?, '-', '')), 'Seed', ?, 'x', NOW(6), NOW(6))",
        id.toString(), email);
    jdbc.update(
        "INSERT INTO user_settings (id, user_id, company, email, role, daily_token_budget, "
            + "notify_anomalies, notify_recommendations, notify_overdue_batches, timezone) "
            + "VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'Co', ?, ?, 1000, TRUE, TRUE, TRUE, 'UTC')",
        UUID.randomUUID().toString(), id.toString(), email, settingsRole);
  }

  private static String roleOf(JdbcTemplate jdbc, UUID id) {
    return jdbc.queryForObject(
        "SELECT role FROM auth_users WHERE id = UNHEX(REPLACE(?, '-', ''))", String.class, id.toString());
  }

  @Test
  void v13CarriesSupervisorRoleFromUserSettingsToAuthUsers() {
    DataSource ds = dataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    flyway(ds, "12").clean();
    flyway(ds, "12").migrate();

    UUID supervisor = UUID.randomUUID();
    UUID operator = UUID.randomUUID();
    UUID noSettings = UUID.randomUUID();
    seedUser(jdbc, supervisor, "sup-" + supervisor + "@wastepilot.test", "SUPERVISOR");
    seedUser(jdbc, operator, "op-" + operator + "@wastepilot.test", "OPERATOR");
    jdbc.update(
        "INSERT INTO auth_users (id, full_name, email, password_hash, created_at, updated_at) "
            + "VALUES (UNHEX(REPLACE(?, '-', '')), 'None', ?, 'x', NOW(6), NOW(6))",
        noSettings.toString(), "none-" + noSettings + "@wastepilot.test");

    flyway(ds, "13").migrate();

    assertThat(roleOf(jdbc, supervisor)).isEqualTo("SUPERVISOR");
    assertThat(roleOf(jdbc, operator)).isEqualTo("OPERATOR");
    assertThat(roleOf(jdbc, noSettings)).isEqualTo("OPERATOR");

    flyway(ds, "latest").clean();
    flyway(ds, "latest").migrate();
  }

  @Test
  void v13AbortsInsteadOfSilentlyDowngradingUnmatchedSupervisors() {
    DataSource ds = dataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    flyway(ds, "12").clean();
    flyway(ds, "12").migrate();

    jdbc.update(
        "INSERT INTO user_settings (id, user_id, company, email, role, daily_token_budget, "
            + "notify_anomalies, notify_recommendations, notify_overdue_batches, timezone) "
            + "VALUES (UNHEX(REPLACE(?, '-', '')), 'not-a-uuid', 'Co', 'x@y.z', 'SUPERVISOR', 1000, "
            + "TRUE, TRUE, TRUE, 'UTC')",
        UUID.randomUUID().toString());

    assertThatThrownBy(() -> flyway(ds, "13").migrate()).hasMessageContaining("V13");

    flyway(ds, "latest").clean();
    flyway(ds, "latest").migrate();
  }
}

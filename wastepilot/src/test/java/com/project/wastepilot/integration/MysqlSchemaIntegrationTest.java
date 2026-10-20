package com.project.wastepilot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "WASTEPILOT_MYSQL_TEST_URL", matches = ".+")
class MysqlSchemaIntegrationTest {
  @DynamicPropertySource
  static void configureMysql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("WASTEPILOT_MYSQL_TEST_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("WASTEPILOT_MYSQL_TEST_USER"));
    registry.add("spring.datasource.password", () -> System.getenv("WASTEPILOT_MYSQL_TEST_PASSWORD"));
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.jpa.defer-datasource-initialization", () -> false);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private MockMvc mockMvc;

  @Test
  void appliesMigrationsAndValidatesTheApplicationSchemaOnMysql() {
    Integer successfulMigrations = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
        Integer.class
    );
    Integer plannedInputColumn = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'batches' "
            + "AND column_name = 'planned_input_kg' AND is_nullable = 'NO'",
        Integer.class
    );
    Integer authRoleColumn = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'auth_users' "
            + "AND column_name = 'role' AND is_nullable = 'NO'",
        Integer.class
    );
    Integer settingsRoleColumn = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'user_settings' "
            + "AND column_name = 'role'",
        Integer.class
    );

    assertThat(successfulMigrations).isNotNull().isGreaterThanOrEqualTo(13);
    assertThat(plannedInputColumn).isEqualTo(1);
    assertThat(authRoleColumn).isEqualTo(1);
    assertThat(settingsRoleColumn).isZero();
  }

  @Test
  void signupIntegrationPersistsThroughTheMigratedMysqlSchema() throws Exception {
    String email = "mysql-integration-" + UUID.randomUUID() + "@wastepilot.test";
    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName":"MySQL Integration","email":"%s","password":"SecurePass123!"}
                """.formatted(email)))
        .andExpect(status().isCreated());

    Integer matchingUsers = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM auth_users WHERE email = ?",
        Integer.class,
        email
    );
    String role = jdbcTemplate.queryForObject(
        "SELECT role FROM auth_users WHERE email = ?",
        String.class,
        email
    );
    assertThat(matchingUsers).isEqualTo(1);
    assertThat(role).isEqualTo("OPERATOR");

    // Proves Hibernate's BINARY(16) byte order matches the UNHEX(REPLACE(user_id,'-','')) join used by V13.
    Integer byteOrderMatches = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM auth_users au JOIN user_settings us "
            + "ON UNHEX(REPLACE(us.user_id, '-', '')) = au.id WHERE au.email = ?",
        Integer.class,
        email
    );
    assertThat(byteOrderMatches).isEqualTo(1);
  }
}

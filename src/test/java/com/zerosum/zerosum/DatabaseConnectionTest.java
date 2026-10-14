package com.zerosum.zerosum;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usesTestDatabase() {
        // Tests drop and recreate the schema, so they must never touch the app's database. The default is
        // zerosum_test; TEST_DB_URL may point elsewhere, but always at a *_test database.
        String db = jdbcTemplate.queryForObject("select current_database()", String.class);
        assertThat(db).endsWith("_test");
    }
}
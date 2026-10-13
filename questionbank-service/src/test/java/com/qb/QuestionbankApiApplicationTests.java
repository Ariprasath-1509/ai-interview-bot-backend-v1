package com.qb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Full-context smoke test: needs a real PostgreSQL (Flyway migrations use pg_trgm and the
// questionbank_svc schema). Runs only when DB_QB_URL is set, so CI without a database skips it.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_QB_URL", matches = ".+")
class QuestionbankApiApplicationTests {

	@Test
	void contextLoads() {
	}

}

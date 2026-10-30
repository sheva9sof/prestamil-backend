package com.ignis.prestamil;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Arranque completo del contexto contra H2 (perfil "test"). Sin el perfil tomaba DB_URL del .env:
 * corria Liquibase y el pase de almoneda del arranque sobre la BD de desarrollo en cada mvn test.
 */
@SpringBootTest
@ActiveProfiles("test")
class PrestamilApplicationTests {

	@Test
	void contextLoads() {
	}

}

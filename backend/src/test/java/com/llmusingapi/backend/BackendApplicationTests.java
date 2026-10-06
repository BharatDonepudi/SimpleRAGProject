package com.llmusingapi.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:backend-context;DB_CLOSE_DELAY=-1")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

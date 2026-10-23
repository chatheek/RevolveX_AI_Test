package resolvex_triage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.ai.typesafe.api-key=test-key",
		"app.security.username=tester",
		"app.security.password=a-long-test-password-123"})
class TriageApplicationTests {

	@Test
	void contextLoads() {
	}

}

package mystreak.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "supabase.url=http://localhost:54321",
        "supabase.anon-key=test-anon-key",
        "spring.datasource.url=jdbc:h2:mem:mystreak-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=never"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}

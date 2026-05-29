package mystreak.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "supabase.url=http://localhost:54321",
        "supabase.anon-key=test-anon-key"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}

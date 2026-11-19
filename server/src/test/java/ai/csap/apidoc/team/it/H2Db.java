package ai.csap.apidoc.team.it;

import org.springframework.test.context.DynamicPropertyRegistry;

final class H2Db {
    private H2Db() {
    }

    static void register(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:teamtest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
    }
}

plugins {
    java
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.matoe"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    // Embabel Agent Framework repositories — kept for the day we re-introduce
    // the starter. Not currently resolving any deps (see below).
    maven {
        name = "EmbabelReleases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
    }
    maven {
        name = "EmbabelSnapshots"
        url = uri("https://repo.embabel.com/artifactory/libs-snapshot")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ─── Embabel Agent Framework 0.3.5 — TEMPORARILY DISABLED ────────────────
    //
    // Embabel 0.3.5's `embabel-agent-api.jar` ships
    // `com.embabel.agent.api.event.AgenticEvent.class` with bytecode that
    // Spring Boot 3.2.4's ASM reader (Spring Framework 6.1.5) cannot parse
    // during component scanning. The failure surfaces as
    //   BeanDefinitionStoreException: Failed to read candidate component
    //   class ... Caused by: java.lang.IllegalArgumentException at Enum.java
    // and blocks the Spring ApplicationContext from loading — i.e. both
    // ./gradlew bootRun and the @SpringBootTest contextLoads test fail.
    //
    // Our TravelService already talks to AgentPlatform reflectively with a
    // full virtual-thread fallback, so removing the starter is a no-op at
    // runtime: the fallback path is the actual working execution strategy
    // and mirrors the GOAP plan exactly. Re-introduce these when either (a)
    // Embabel ships a build compatible with Spring 6.1.x ASM, or (b) the
    // project upgrades to Spring Boot 3.3+.
    //
    // implementation("com.embabel.agent:embabel-agent-starter:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-anthropic:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-openai:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-ollama:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-lmstudio:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-openai-custom:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-webmvc:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-observability:0.3.5")

    // JSON & Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
    // Spring Boot 3.2.4 ships flyway-core 9.22.3 (Flyway 9.x), which bundles
    // built-in Postgres support directly in flyway-core. The separate
    // flyway-database-postgresql module only exists in Flyway 10+ (Spring
    // Boot 3.3+), so we do NOT declare it here — that was the CI failure.
    implementation("org.flywaydb:flyway-core")

    // HTTP Client (WebFlux/WebClient for browser-service + LLM calls)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Resilience4j — circuit breakers for browser/LLM services
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")

    // PDF export (iText)
    implementation("com.itextpdf:itext7-core:8.0.2")

    // Logging
    implementation("org.springframework.boot:spring-boot-starter-logging")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    // testImplementation("com.embabel.agent:embabel-agent-test:0.3.5")  // see above
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

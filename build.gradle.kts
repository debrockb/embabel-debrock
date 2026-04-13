plugins {
    java
    // Spring Boot 3.3.5 ships Spring Framework 6.1.14, whose bundled ASM reader
    // can parse Embabel 0.3.5's Kotlin-compiled bytecode. Spring Boot 3.2.x
    // (Spring Framework 6.1.5) crashed during component scanning with
    //   java.lang.IllegalArgumentException at Enum.java:293
    // while reading embabel-agent-api-0.3.5.jar's AgenticEvent.class.
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.matoe"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    // Embabel 0.3.5 is published to Embabel's Artifactory, not Maven Central.
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

    // ─── Embabel Agent Framework 0.3.5 ──────────────────────────────────────
    // Resolved from the Embabel Artifactory (see repositories {} above). These
    // jars are NOT on Maven Central. The Spring Boot 3.3+ ASM reader parses
    // their Kotlin-compiled bytecode correctly; Spring Boot 3.2 could not.
    //
    // Core starter + GOAP planner + AgentPlatform bean:
    implementation("com.embabel.agent:embabel-agent-starter:0.3.5")
    // Local LLM providers (Ollama, LM Studio):
    implementation("com.embabel.agent:embabel-agent-starter-ollama:0.3.5")
    implementation("com.embabel.agent:embabel-agent-starter-lmstudio:0.3.5")
    // Web MVC + observability:
    implementation("com.embabel.agent:embabel-agent-starter-webmvc:0.3.5")
    implementation("com.embabel.agent:embabel-agent-starter-observability:0.3.5")
    // ─── Cloud LLM provider starters (optional) ─────────────────────────────
    // These eagerly validate API keys at startup and crash if keys are empty.
    // Our LlmService already handles Anthropic/OpenAI/OpenRouter routing via
    // direct WebClient calls, so these starters are NOT required. Uncomment
    // if you want Embabel's Ai interface to route cloud LLM calls natively:
    //
    // implementation("com.embabel.agent:embabel-agent-starter-anthropic:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-openai:0.3.5")
    // implementation("com.embabel.agent:embabel-agent-starter-openai-custom:0.3.5")

    // JSON & Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
    // Spring Boot 3.3+ ships Flyway 10.x, which split Postgres support into a
    // separate runtime module. Declaring it is mandatory for Postgres-backed
    // profiles; it has no effect on SQLite.
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

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
    testImplementation("com.embabel.agent:embabel-agent-test:0.3.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

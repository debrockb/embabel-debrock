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
    // Embabel Agent Framework repositories
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

    // ─── Embabel Agent Framework 0.3.5 ────────────────────────────────────────
    // Core agent platform (GOAP planner, @Agent, @Action, AgentPlatform, Ai)
    implementation("com.embabel.agent:embabel-agent-starter:0.3.5")
    // LLM provider starters
    implementation("com.embabel.agent:embabel-agent-starter-anthropic:0.3.5")
    implementation("com.embabel.agent:embabel-agent-starter-openai:0.3.5")
    implementation("com.embabel.agent:embabel-agent-starter-ollama:0.3.5")
    implementation("com.embabel.agent:embabel-agent-starter-lmstudio:0.3.5")
    // OpenAI-compatible custom endpoints (OpenRouter, Groq, etc.)
    implementation("com.embabel.agent:embabel-agent-starter-openai-custom:0.3.5")
    // WebMVC endpoints (agent REST API)
    implementation("com.embabel.agent:embabel-agent-starter-webmvc:0.3.5")
    // Observability (OpenTelemetry tracing, metrics)
    implementation("com.embabel.agent:embabel-agent-starter-observability:0.3.5")

    // JSON & Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

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

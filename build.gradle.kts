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
    // Embabel Agent Framework repository
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

    // ─── Embabel Agent Framework ───────────────────────────────────────────────
    // Core agent platform (GOAP planner, @Agent, @Action, AgentPlatform)
    implementation("com.embabel.agent:embabel-agent-starter:0.3.2")
    // LLM provider starters (Anthropic, OpenAI)
    implementation("com.embabel.agent:embabel-agent-starter-anthropic:0.3.2")
    implementation("com.embabel.agent:embabel-agent-starter-openai:0.3.2")
    // Observability (tracing, metrics)
    implementation("com.embabel.agent:embabel-agent-starter-observability:0.3.2")

    // JSON & Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // HTTP Client (WebFlux/WebClient for browser-service + LLM calls)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Logging
    implementation("org.springframework.boot:spring-boot-starter-logging")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

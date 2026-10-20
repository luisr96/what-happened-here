plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":extractor"))
    implementation(project(":sampler")) // DumpReader
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql") // CopyManager for staging loads

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// One schema and one resolve step for API and importer: share the migrations and SQL, but not the
// API's application.properties (a sourceSet srcDir filter would also apply to our own resources).
tasks.processResources {
    from(rootProject.file("api/src/main/resources")) {
        include("db/**", "sql/**")
    }
}

tasks.test {
    useJUnitPlatform()
}

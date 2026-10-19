plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":extractor"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql") // CopyManager for bulk loads

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // OpenApiContractTest compares against it, so editing it must rerun the tests.
    inputs.files(rootProject.file("frontend/openapi.json")).withPropertyName("openApiContract")
    // -PupdateOpenApi rewrites frontend/openapi.json from the generated /v3/api-docs.
    if (providers.gradleProperty("updateOpenApi").isPresent) {
        systemProperty("updateOpenApi", "true")
        outputs.upToDateWhen { false }
    }
}

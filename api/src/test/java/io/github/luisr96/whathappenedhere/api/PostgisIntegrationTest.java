package io.github.luisr96.whathappenedhere.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests that need the real schema, migrated by Flyway at context startup.
 *
 * <p>Singleton-container pattern: the container is started once per JVM in a static initializer
 * and never stopped explicitly (Ryuk removes it when the JVM exits). Every subclass sees the same
 * container, so Spring's cached application context stays valid across test classes; with
 * {@code @Testcontainers}/{@code @Container} the container would be stopped after the first class
 * while the cached context still pointed at it.
 */
@SpringBootTest
public abstract class PostgisIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.start();
    }
}

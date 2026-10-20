package io.github.luisr96.whathappenedhere.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The job's "report" step over ImportJobTest's fixtures (FIXTURE: 8 entities, SECOND_FIXTURE: 1). */
@SpringBootTest(properties = {
    "import.chunk-size=2",
    "import.max-reconnects=1",
    "import.reconnect-delay=0s",
    "import.threads=4",
})
class ImportReportStepTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    private static final Path REPORT_DIR;
    private static final Pattern EIGHT = Pattern.compile("\\b8\\b");

    static {
        POSTGIS.start();
        try {
            REPORT_DIR = Files.createTempDirectory("import-report");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void reportDir(DynamicPropertyRegistry registry) {
        registry.add("import.report-dir", REPORT_DIR::toString);
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        ImportJobTest.FakeDumpOpener fakeDumpOpener() {
            return new ImportJobTest.FakeDumpOpener();
        }
    }

    @Autowired
    ImportService importService;

    @Autowired
    ImportJobTest.FakeDumpOpener opener;

    @AfterEach
    void cleanUp() throws IOException {
        opener.heal();
        Files.deleteIfExists(reportFile());
    }

    @Test
    void runWritesTheReportIntoTheConfiguredDirectory() throws IOException {
        var execution = importService.run(opener.register(ImportJobTest.FIXTURE), 0);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(reportFile()).exists();
        var markdown = Files.readString(reportFile());
        assertThat(markdown).containsPattern(EIGHT);
        assertThat(markdown.lines()).anyMatch(l -> l.contains("BORN") && l.contains("2"));
        assertThat(markdown.lines()).anyMatch(l -> l.contains("HAPPENED") && l.contains("1"));
        assertThat(markdown.lines()).anyMatch(l -> l.contains("FOUNDED") && l.contains("1"));
        assertThat(markdown).contains("MB");
    }

    @Test
    void reportIsAStepOfTheJob() {
        var execution = importService.run(opener.register(ImportJobTest.FIXTURE), 0);

        assertThat(execution.getStepExecutions()).anySatisfy(step -> {
            assertThat(step.getStepName()).isEqualTo("report");
            assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        });
    }

    @Test
    void secondRunOverwritesTheReport() throws IOException {
        importService.run(opener.register(ImportJobTest.FIXTURE), 0);
        importService.run(opener.register(ImportJobTest.SECOND_FIXTURE), 0);

        var markdown = Files.readString(reportFile());
        assertThat(markdown).contains("HAPPENED").doesNotContain("BORN").doesNotContain("FOUNDED");
        assertThat(markdown).doesNotContainPattern(EIGHT);
    }

    @Test
    void reportAfterRestartCountsEveryEntityReadAcrossExecutions() throws IOException {
        var source = opener.register(ImportJobTest.FIXTURE);
        opener.failAfter(5);
        var failed = importService.run(source, 0);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        Files.deleteIfExists(reportFile());

        opener.heal();
        var restarted = importService.run(source, 0);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var markdown = Files.readString(reportFile());
        // The restarted execution itself reads only the entities after the last committed chunk.
        assertThat(markdown).containsPattern(EIGHT);
        assertThat(markdown.lines()).anyMatch(l -> l.contains("BORN") && l.contains("2"));
    }

    private static Path reportFile() {
        return REPORT_DIR.resolve("import-report.md");
    }
}

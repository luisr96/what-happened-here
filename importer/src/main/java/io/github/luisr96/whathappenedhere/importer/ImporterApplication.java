package io.github.luisr96.whathappenedhere.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Bulk-loads the Wikidata dump into a local Postgres (CLAUDE.md, Importer flow). Usage:
 * {@code ./gradlew :importer:bootRun --args="--import.source=<URL or path> [--import.limit=N]"}
 * plus the {@code spring.datasource.*} settings. Running the same arguments after a failure
 * resumes it.
 */
@SpringBootApplication
public class ImporterApplication {

    private static final Logger log = LoggerFactory.getLogger(ImporterApplication.class);

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ImporterApplication.class, args)));
    }

    /** Does nothing without {@code import.source}, so tests can drive {@link ImportService} directly. */
    @Bean
    CommandLineRunner importOnStartup(ImportService imports,
            @Value("${import.source:}") String source, @Value("${import.limit:0}") long limit) {
        return args -> {
            if (source.isBlank()) {
                return;
            }
            JobExecution execution = imports.run(source, limit);
            log.info("Import of {} finished: {}", source, execution.getStatus());
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("Import " + execution.getStatus()
                        + "; run the same command again to resume it");
            }
        };
    }
}

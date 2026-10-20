package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;

/**
 * The import job (CLAUDE.md, Importer flow), in three steps:
 * <ol>
 *   <li>prepare: create and empty the staging tables. A restart skips it (it already completed),
 *       which is what keeps the rows staged before the failure;</li>
 *   <li>extract: dump entities through the extractor into staging, committed chunk by chunk;</li>
 *   <li>resolve: replace {@code events} and {@code places} from staging with the API's
 *       {@code sql/resolve.sql}, then rebuild {@code timeline_cells}, in one transaction, so a
 *       failed import never touches what is served.</li>
 * </ol>
 */
@Configuration
public class ImportJobConfig {

    static final String NAME = "import";
    static final String SOURCE = "source";
    static final String LIMIT = "limit";

    private static final String PREPARE_SQL = sql("sql/import_stage.sql");
    private static final String RESOLVE_SQL = sql("sql/resolve.sql");
    private static final String TIMELINE_SQL = sql("sql/timeline_cells.sql");

    @Bean
    Job importJob(JobRepository jobs, Step prepare, Step extract, Step resolve) {
        return new JobBuilder(NAME, jobs).start(prepare).next(extract).next(resolve).build();
    }

    @Bean
    Step prepare(JobRepository jobs, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return new StepBuilder("prepare", jobs)
                .tasklet((contribution, context) -> {
                    jdbc.execute(PREPARE_SQL);
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();
    }

    @Bean
    Step extract(JobRepository jobs, PlatformTransactionManager transactions,
            DumpEntityReader reader, DataSource dataSource,
            @Value("${import.chunk-size:1000}") int chunkSize) {
        return new StepBuilder("extract", jobs)
                .<JsonNode, ImportRecord>chunk(chunkSize)
                .transactionManager(transactions)
                .reader(reader)
                .processor(new EntityProcessor(new WikidataExtractor(Clock.systemUTC())))
                .writer(new StagingWriter(dataSource))
                .build();
    }

    @Bean
    Step resolve(JobRepository jobs, PlatformTransactionManager transactions, JdbcTemplate jdbc) {
        return new StepBuilder("resolve", jobs)
                .tasklet((contribution, context) -> {
                    jdbc.execute(RESOLVE_SQL);
                    jdbc.execute(TIMELINE_SQL);
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();
    }

    /** Step-scoped: built per run, from that run's job parameters. */
    @Bean
    @StepScope
    DumpEntityReader dumpEntityReader(DumpOpener opener,
            @Value("#{jobParameters['" + SOURCE + "']}") String source,
            @Value("#{jobParameters['" + LIMIT + "']}") Long limit,
            @Value("${import.max-reconnects:5}") int maxReconnects,
            @Value("${import.reconnect-delay:30s}") Duration reconnectDelay) {
        return new DumpEntityReader(opener, source, limit, maxReconnects, reconnectDelay);
    }

    private static String sql(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

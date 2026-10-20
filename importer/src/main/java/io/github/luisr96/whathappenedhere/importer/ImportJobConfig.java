package io.github.luisr96.whathappenedhere.importer;

import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The import job (CLAUDE.md, Importer flow), in four steps:
 * <ol>
 *   <li>prepare: create and empty the staging tables. A restart skips it (it already completed),
 *       which is what keeps the rows staged before the failure;</li>
 *   <li>extract: dump entities through the extractor into staging, committed chunk by chunk;</li>
 *   <li>resolve: replace {@code events} and {@code places} from staging with the API's
 *       {@code sql/resolve.sql}, then rebuild {@code timeline_cells}, with their secondary
 *       indexes dropped meanwhile (see {@link SecondaryIndexes}), in one transaction, so a
 *       failed import never touches what is served;</li>
 *   <li>report: write {@code import-report.md} (see {@link ImportReport}) to
 *       {@code import.report-dir}.</li>
 * </ol>
 */
@Configuration
public class ImportJobConfig {

    static final String NAME = "import";
    static final String SOURCE = "source";
    static final String LIMIT = "limit";
    private static final List<String> SERVED_TABLES = List.of("events", "places", "timeline_cells");

    private static final Logger log = LoggerFactory.getLogger(ImportJobConfig.class);

    private static final String PREPARE_SQL = sql("sql/import_stage.sql");
    private static final String RESOLVE_SQL = sql("sql/resolve.sql");
    private static final String TIMELINE_SQL = sql("sql/timeline_cells.sql");

    @Bean
    Job importJob(JobRepository jobs, Step prepare, Step extract, Step resolve, Step report) {
        return new JobBuilder(NAME, jobs).start(prepare).next(extract).next(resolve).next(report)
                .build();
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
            DumpEntityReader reader, DataSource dataSource, ThreadPoolTaskExecutor extractThreads,
            @Value("${import.chunk-size:1000}") int chunkSize) {
        return new StepBuilder("extract", jobs)
                .<String, ImportRecord>chunk(chunkSize)
                .transactionManager(transactions)
                .taskExecutor(extractThreads)
                .reader(reader)
                .processor(new EntityProcessor(new WikidataExtractor(Clock.systemUTC())))
                .writer(new StagingWriter(dataSource))
                .build();
    }

    /**
     * Parses and extracts the items of each chunk in parallel. Reading stays on one thread and
     * Spring Batch waits for the whole chunk before writing it, so rows and checkpoint still commit
     * together. Being an Executor bean, it also replaces Spring Boot's default task executor, which
     * nothing else in the importer uses.
     */
    @Bean
    ThreadPoolTaskExecutor extractThreads(
            @Value("${import.threads:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setThreadNamePrefix("extract-");
        return executor;
    }

    @Bean
    Step resolve(JobRepository jobs, PlatformTransactionManager transactions, JdbcTemplate jdbc,
            @Value("${import.maintenance-work-mem:1GB}") String maintenanceWorkMem) {
        return new StepBuilder("resolve", jobs)
                .tasklet((contribution, context) -> {
                    SecondaryIndexes indexes = new SecondaryIndexes(jdbc, SERVED_TABLES);
                    List<String> dropped = indexes.dropAll();
                    jdbc.execute(RESOLVE_SQL);
                    jdbc.execute(TIMELINE_SQL);
                    // Index builds sort in memory up to this budget; transaction-local.
                    jdbc.queryForObject("SELECT set_config('maintenance_work_mem', ?, true)",
                            String.class, maintenanceWorkMem);
                    indexes.recreate(dropped);
                    jdbc.execute("ANALYZE " + String.join(", ", SERVED_TABLES));
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();
    }

    /**
     * Entities read is taken from the extract step's last checkpoint, which a restarted run
     * carries over, rather than from this execution's own read count. A failure here (e.g. an
     * unwritable directory) fails the job but leaves the resolved tables in place; restarting
     * re-runs only this step.
     */
    @Bean
    Step report(JobRepository jobs, PlatformTransactionManager transactions, ImportReporter reporter,
            @Value("${import.report-dir:.}") Path reportDir) {
        return new StepBuilder("report", jobs)
                .tasklet((contribution, context) -> {
                    var instance = context.getStepContext().getStepExecution().getJobExecution().getJobInstance();
                    var extract = jobs.getLastStepExecution(instance, "extract");
                    long entitiesRead = extract == null
                            ? 0 : extract.getExecutionContext().getLong(DumpEntityReader.READ_KEY, 0L);
                    Path file = reportDir.resolve("import-report.md");
                    Files.createDirectories(reportDir);
                    Files.writeString(file, reporter.report(entitiesRead).toMarkdown(), StandardCharsets.UTF_8);
                    log.info("Import report: {}", file.toAbsolutePath());
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

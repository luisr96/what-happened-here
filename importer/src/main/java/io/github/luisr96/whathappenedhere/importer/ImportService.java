package io.github.luisr96.whathappenedhere.importer;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

/**
 * Starts or resumes an import. Running the same (source, limit) again after a failure restarts
 * that run from its last committed chunk; anything else starts a new run, which replaces the
 * previous contents. A {@code run} parameter numbers the runs, because Spring Batch refuses to
 * start a completed instance's exact parameters a second time.
 *
 * <p>A run killed outright (power loss, OOM kill, a rebooted VM) never got to record its failure
 * and still looks STARTED. Assuming one importer per database, nothing can really be running, so
 * that run is marked failed and resumed like any other; otherwise its hours of staged rows would
 * be thrown away by a fresh start.
 */
@Service
public class ImportService {

    private static final String RUN = "run";
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final JobOperator operator;
    private final JobRepository repository;
    private final Job job;

    public ImportService(JobOperator operator, JobRepository repository, Job importJob) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.job = Objects.requireNonNull(importJob, "importJob");
    }

    /**
     * Runs synchronously and returns the finished execution; a failure is reported by its status,
     * not thrown.
     *
     * @param limit entities to read from the start of the dump; {@code <= 0} for all
     */
    public JobExecution run(String source, long limit) {
        Objects.requireNonNull(source, "source");
        JobInstance last = repository.getLastJobInstance(ImportJobConfig.NAME);
        JobExecution previous = last == null ? null : repository.getLastJobExecution(last);
        try {
            if (previous != null && sameImport(previous, source, limit)) {
                switch (previous.getStatus()) {
                    case FAILED, STOPPED -> {
                        return operator.restart(previous);
                    }
                    case STARTING, STARTED, STOPPING, UNKNOWN -> {
                        log.warn("Import execution {} was left {}: it died without recording it."
                                + " Resuming it (only safe with one importer per database).",
                                previous.getId(), previous.getStatus());
                        return operator.restart(operator.recover(previous));
                    }
                    default -> {
                        // COMPLETED or ABANDONED: start a new run below.
                    }
                }
            }
            Long lastRun = previous == null ? null : previous.getJobParameters().getLong(RUN);
            long run = lastRun == null ? 1 : lastRun + 1;
            JobParameters parameters = new JobParametersBuilder()
                    .addString(ImportJobConfig.SOURCE, source)
                    .addLong(ImportJobConfig.LIMIT, limit)
                    .addLong(RUN, run)
                    .toJobParameters();
            return operator.start(job, parameters);
        } catch (Exception e) {
            throw new IllegalStateException("Could not launch the import of " + source
                    + " (previous execution: " + (previous == null ? "none" : previous.getStatus())
                    + ")", e);
        }
    }

    private static boolean sameImport(JobExecution previous, String source, long limit) {
        JobParameters parameters = previous.getJobParameters();
        return source.equals(parameters.getString(ImportJobConfig.SOURCE))
                && Long.valueOf(limit).equals(parameters.getLong(ImportJobConfig.LIMIT));
    }
}

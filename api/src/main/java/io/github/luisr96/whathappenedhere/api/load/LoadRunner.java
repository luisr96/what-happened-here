package io.github.luisr96.whathappenedhere.api.load;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads sampler output at startup when {@code --load.dir=<dir>} is given (e.g.
 * {@code sampler/build/sample}); {@code --load.min-importance=<n>} sets the cutoff (default 0).
 * Without {@code load.dir} it does nothing. Runs after the server starts, so requests during the
 * load see the previous data (the load is one transaction).
 */
@Component
public class LoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadRunner.class);

    private final SampleLoader loader;
    private final String dir;
    private final int minImportance;

    public LoadRunner(SampleLoader loader, @Value("${load.dir:}") String dir,
            @Value("${load.min-importance:0}") int minImportance) {
        this.loader = loader;
        this.dir = dir;
        this.minImportance = minImportance;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dir == null || dir.isBlank()) {
            return;
        }
        LoadResult result = loader.load(Path.of(dir, "items.jsonl"), Path.of(dir, "places.jsonl"),
                minImportance);
        log.info("Loaded {}: {}", dir, result);
    }
}

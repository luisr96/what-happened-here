package io.github.luisr96.whathappenedhere.api.load;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;

class LoadRunnerTest {

    private final SampleLoader loader = mock(SampleLoader.class);

    @Test
    void loadsItemsAndPlacesFromDirWithCutoff() throws Exception {
        new LoadRunner(loader, "build/sample", 10).run(new DefaultApplicationArguments());
        verify(loader).load(Path.of("build/sample", "items.jsonl"), Path.of("build/sample", "places.jsonl"), 10);
    }

    @Test
    void zeroCutoffIsPassedThrough() throws Exception {
        new LoadRunner(loader, "out", 0).run(new DefaultApplicationArguments());
        verify(loader).load(Path.of("out", "items.jsonl"), Path.of("out", "places.jsonl"), 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankDirDoesNothing(String dir) throws Exception {
        new LoadRunner(loader, dir, 5).run(new DefaultApplicationArguments());
        verifyNoInteractions(loader);
    }

    @Test
    void nullDirDoesNothing() throws Exception {
        new LoadRunner(loader, null, 0).run(new DefaultApplicationArguments());
        verifyNoInteractions(loader);
    }
}

package io.github.luisr96.whathappenedhere.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class DumpEntityReaderTest {

    private static final String SOURCE = "https://example.org/latest-all.json.gz";

    /** Answers the n-th open() call with the n-th step; the last step repeats. */
    private static final class ScriptedOpener implements DumpOpener {

        interface Step {
            InputStream open() throws IOException;
        }

        private final List<Step> steps;
        final AtomicInteger calls = new AtomicInteger();
        final List<String> sources = new ArrayList<>();

        ScriptedOpener(Step... steps) {
            this.steps = List.of(steps);
        }

        @Override
        public InputStream open(String source) throws IOException {
            sources.add(source);
            int call = calls.getAndIncrement();
            return steps.get(Math.min(call, steps.size() - 1)).open();
        }
    }

    private static DumpEntityReader reader(DumpOpener opener, long limit, int maxReconnects) {
        return new DumpEntityReader(opener, SOURCE, limit, maxReconnects, Duration.ZERO);
    }

    private static List<String> readAll(DumpEntityReader reader) throws Exception {
        var ids = new ArrayList<String>();
        String line;
        while ((line = reader.read()) != null) {
            ids.add(id(line));
        }
        return ids;
    }

    /** The entity's id, or the raw text for a line that isn't a JSON object. */
    private static String id(String line) {
        try {
            var node = Entities.json(line);
            return node.isObject() ? node.path("id").asString() : line;
        } catch (RuntimeException malformed) {
            return line;
        }
    }

    private static List<String> ids(int from, int to) {
        var ids = new ArrayList<String>();
        for (int i = from; i <= to; i++) {
            ids.add("Q" + i);
        }
        return ids;
    }

    private static ExecutionContext checkpoint(long entitiesRead) {
        var context = new ExecutionContext();
        context.putLong("entities.read", entitiesRead);
        return context;
    }

    @Test
    void readsAllEntitiesInOrderFromTheGivenSource() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(5));
        var opener = new ScriptedOpener(dump::stream);
        var reader = reader(opener, 0, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 5));
        assertThat(reader.read()).as("stays at the end").isNull();
        assertThat(opener.sources).containsExactly(SOURCE);
        reader.close();
    }

    @Test
    void returnsEachEntityLineAsTextWithoutItsTrailingComma() throws Exception {
        var dump = TestDumps.dump(List.of(TestDumps.entity("Q42"), TestDumps.entity("Q43")));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo(TestDumps.entity("Q42"));
        assertThat(reader.read()).isEqualTo(TestDumps.entity("Q43"));
        reader.close();
    }

    @Test
    void returnsMalformedLinesAndNonObjectsAsTextButNotBlankLines() throws Exception {
        // Parsing moved to the processor: the reader hands over every entity line, valid or not.
        var dump = TestDumps.dump(List.of(
                TestDumps.entity("Q1"), "{not json", "", "[1,2]", "\"just a string\"", TestDumps.entity("Q2")));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactly("Q1", "{not json", "[1,2]", "\"just a string\"", "Q2");
        reader.close();
    }

    @Test
    void emptyDumpReadsNothing() throws Exception {
        var reader = reader(new ScriptedOpener(TestDumps.dump(List.of())::stream), 0, 3);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    void limitStopsAfterThatManyEntities() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(5));
        var reader = reader(new ScriptedOpener(dump::stream), 3, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactly("Q1", "Q2", "Q3");
        reader.close();
    }

    @Test
    void limitCountsMalformedLinesToo() throws Exception {
        var dump = TestDumps.dump(List.of(
                TestDumps.entity("Q1"), "{broken", TestDumps.entity("Q2"), TestDumps.entity("Q3")));
        var reader = reader(new ScriptedOpener(dump::stream), 2, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactly("Q1", "{broken");
        reader.close();
    }

    @Test
    void negativeLimitMeansNoLimit() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(4));
        var reader = reader(new ScriptedOpener(dump::stream), -1, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 4));
        reader.close();
    }

    @Test
    void openWithCheckpointSkipsEntitiesAlreadyRead() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        reader.open(checkpoint(4));

        assertThat(readAll(reader)).containsExactly("Q5", "Q6");
        reader.close();
    }

    @Test
    void checkpointSkipCountsMalformedLinesToo() throws Exception {
        var dump = TestDumps.dump(List.of(
                TestDumps.entity("Q1"), "{broken", TestDumps.entity("Q2"), TestDumps.entity("Q3")));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        reader.open(checkpoint(2));

        assertThat(readAll(reader)).containsExactly("Q2", "Q3");
        reader.close();
    }

    @Test
    void entitiesSkippedOnRestartCountTowardTheLimit() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        var reader = reader(new ScriptedOpener(dump::stream), 3, 3);
        reader.open(checkpoint(2));

        assertThat(readAll(reader)).containsExactly("Q3");
        reader.close();
    }

    @Test
    void updateStoresTheNumberOfLinesReturnedMalformedIncluded() throws Exception {
        var dump = TestDumps.dump(List.of(
                TestDumps.entity("Q1"), "{broken", TestDumps.entity("Q2"), TestDumps.entity("Q3")));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        var context = new ExecutionContext();
        reader.open(context);
        reader.read();
        reader.read();
        reader.read();
        reader.update(context);

        assertThat(context.getLong("entities.read")).isEqualTo(3);
        reader.close();
    }

    @Test
    void updateAfterRestartIncludesSkippedEntities() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        var reader = reader(new ScriptedOpener(dump::stream), 0, 3);
        var context = checkpoint(4);
        reader.open(context);
        reader.read();
        reader.update(context);

        assertThat(context.getLong("entities.read")).isEqualTo(5);
        reader.close();
    }

    @Test
    void reconnectSkipsMalformedLinesAlreadyReturned() throws Exception {
        var dump = TestDumps.dump(List.of(
                TestDumps.entity("Q1"), "{broken", TestDumps.entity("Q2"), TestDumps.entity("Q3")));
        var opener = new ScriptedOpener(() -> dump.failingAfter(2), dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactly("Q1", "{broken", "Q2", "Q3");
        assertThat(opener.calls).hasValue(2);
        reader.close();
    }

    @Test
    void ioErrorMidStreamReopensAndContinuesWithoutDuplicatesOrGaps() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(8));
        var opener = new ScriptedOpener(() -> dump.failingAfter(3), dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 8));
        assertThat(opener.calls).hasValue(2);
        assertThat(opener.sources).containsOnly(SOURCE);
        reader.close();
    }

    @Test
    void uncheckedIoErrorFromTheStreamAlsoReconnects() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        var opener = new ScriptedOpener(
                () -> dump.failingAfter(2, () -> new UncheckedIOException(new IOException("reset"))),
                dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 6));
        assertThat(opener.calls).hasValue(2);
        reader.close();
    }

    @Test
    void failureInTheMiddleOfALineYieldsNeitherAPartialNorADuplicateEntity() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        // Cut halfway through the compressed bytes of Q4's line.
        long cut = (dump.bytesAfter(3) + dump.bytesAfter(4)) / 2;
        var opener = new ScriptedOpener(
                () -> new TestDumps.FailingStream(dump.stream(), cut, null), dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 6));
        reader.close();
    }

    @Test
    void checkpointAfterReconnectCountsEachEntityOnce() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        var opener = new ScriptedOpener(() -> dump.failingAfter(2), dump::stream);
        var reader = reader(opener, 0, 2);
        var context = new ExecutionContext();
        reader.open(context);
        for (int i = 0; i < 4; i++) {
            reader.read();
        }
        reader.update(context);

        assertThat(context.getLong("entities.read")).isEqualTo(4);
        reader.close();
    }

    @Test
    void reconnectAfterCheckpointRestartSkipsRestoredAndReadEntities() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(8));
        var opener = new ScriptedOpener(() -> dump.failingAfter(5), dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(checkpoint(3));

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(4, 8));
        reader.close();
    }

    @Test
    void reconnectRespectsTheLimit() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(8));
        var opener = new ScriptedOpener(() -> dump.failingAfter(2), dump::stream);
        var reader = reader(opener, 4, 2);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 4));
        reader.close();
    }

    @Test
    void failureWhileSkippingAfterReopenAlsoRecovers() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(8));
        var opener = new ScriptedOpener(
                () -> dump.failingAfter(4), // dies after Q4 was returned
                () -> dump.failingAfter(2), // dies while skipping Q1..Q4
                dump::stream);
        var reader = reader(opener, 0, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 8));
        assertThat(opener.calls).hasValue(3);
        reader.close();
    }

    @Test
    void openerThrowingOnReopenIsRetried() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(5));
        var opener = new ScriptedOpener(
                () -> dump.failingAfter(2),
                () -> {
                    throw new IOException("503 Service Unavailable");
                },
                dump::stream);
        var reader = reader(opener, 0, 3);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 5));
        assertThat(opener.calls).hasValue(3);
        reader.close();
    }

    @Test
    void givesUpAfterMaxReconnectsWhenTheOpenerKeepsFailing() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(5));
        var opener = new ScriptedOpener(
                () -> dump.failingAfter(2),
                () -> {
                    throw new IOException("503 Service Unavailable");
                });
        var reader = reader(opener, 0, 3);
        reader.open(new ExecutionContext());

        assertThat(id(reader.read())).isEqualTo("Q1");
        assertThat(id(reader.read())).isEqualTo("Q2");
        assertThatThrownBy(reader::read).isInstanceOf(Exception.class);
        // the initial open plus exactly maxReconnects reconnect attempts
        assertThat(opener.calls).hasValue(1 + 3);
        reader.close();
    }

    @Test
    void givesUpWhenEveryReopenFailsBeforeProgress() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(6));
        // Every stream dies after Q3, so each reconnect fails while skipping: no progress.
        var opener = new ScriptedOpener(() -> dump.failingAfter(3));
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());

        var seen = new ArrayList<String>();
        assertThatThrownBy(() -> {
            String line;
            while ((line = reader.read()) != null) {
                seen.add(id(line));
            }
        }).isInstanceOf(Exception.class);
        assertThat(seen).containsExactly("Q1", "Q2", "Q3");
        assertThat(opener.calls).hasValue(1 + 2);
        reader.close();
    }

    @Test
    void reconnectCounterResetsAfterProgress() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(9));
        var opener = new ScriptedOpener(
                () -> dump.failingAfter(3),
                () -> dump.failingAfter(6),
                dump::stream);
        var reader = reader(opener, 0, 1);
        reader.open(new ExecutionContext());

        assertThat(readAll(reader)).containsExactlyElementsOf(ids(1, 9));
        assertThat(opener.calls).hasValue(3);
        reader.close();
    }

    @Test
    void closeClosesTheUnderlyingStream() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(3));
        var closed = new AtomicInteger();
        var opener = new ScriptedOpener(() -> new FilterInputStream(dump.stream()) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        });
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());
        reader.read();
        reader.close();

        assertThat(closed.get()).isPositive();
    }

    @Test
    void reconnectClosesTheFailedStream() throws Exception {
        var dump = TestDumps.dump(TestDumps.entities(4));
        var closed = new AtomicInteger();
        var opener = new ScriptedOpener(
                () -> new FilterInputStream(dump.failingAfter(2)) {
                    @Override
                    public void close() throws IOException {
                        closed.incrementAndGet();
                        super.close();
                    }
                },
                dump::stream);
        var reader = reader(opener, 0, 2);
        reader.open(new ExecutionContext());
        readAll(reader);

        assertThat(closed.get()).as("failed stream closed before reopening").isPositive();
        reader.close();
    }
}

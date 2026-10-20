package io.github.luisr96.whathappenedhere.sampler;

import io.github.luisr96.whathappenedhere.extractor.Event.Coordinates;
import io.github.luisr96.whathappenedhere.extractor.ExtractedItem;
import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * Runs the extractor over the first N entities of a Wikidata dump (CLAUDE.md, Testing strategy
 * step 2) and writes {@code items.jsonl}, {@code places.jsonl} and {@code report.md}.
 *
 * <p>Usage: {@code Sampler <dump URL or path> <N> <output dir>}. Over HTTP only the first N
 * entities are downloaded: closing the stream aborts the transfer.
 */
public final class Sampler {

    private static final String USER_AGENT =
            "what-happened-here-sampler/0.1 (https://github.com/luisr96/what-happened-here)";

    private Sampler() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        long limit = args.length == 3 ? parseLimit(args[1]) : -1;
        if (limit <= 0) {
            System.err.println("Usage: Sampler <dump URL or path> <N > 0> <output dir>");
            System.exit(2);
        }
        Path outDir = Files.createDirectories(Path.of(args[2]));
        WikidataExtractor extractor = new WikidataExtractor(Clock.systemUTC());
        SampleReport report = new SampleReport();
        long started = System.nanoTime();

        DumpReader reader = new DumpReader(open(args[0]), limit);
        try (Stream<JsonNode> entities = reader.entities();
                Writer items = Files.newBufferedWriter(outDir.resolve("items.jsonl"), StandardCharsets.UTF_8);
                Writer places = Files.newBufferedWriter(outDir.resolve("places.jsonl"), StandardCharsets.UTF_8)) {
            entities.forEach(entity -> {
                Optional<ExtractedItem> extracted = extractor.extract(entity);
                report.add(entity, extracted);
                extracted.ifPresent(item -> SampleWriter.writeItem(items, item));
                Coordinates coords = WikidataExtractor.placeCoordinates(entity);
                if (coords != null) {
                    SampleWriter.writePlace(places, entity.path("id").asString(""), coords);
                }
                if (report.entitiesRead() % 10_000 == 0) {
                    System.err.printf("%,d entities...%n", report.entitiesRead());
                }
            });
        }
        String markdown = report.toMarkdown()
                + String.format("%n- Malformed lines skipped: %d%n- Seconds: %d%n",
                        reader.malformedLines(), (System.nanoTime() - started) / 1_000_000_000);
        Files.writeString(outDir.resolve("report.md"), markdown, StandardCharsets.UTF_8);
        System.err.printf("Done: %,d entities, %,d items with events -> %s%n",
                report.entitiesRead(), report.itemsWithEvents(), outDir.toAbsolutePath());
    }

    private static InputStream open(String source) throws IOException, InterruptedException {
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            return Files.newInputStream(Path.of(source));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                .header("User-Agent", USER_AGENT)
                .build();
        HttpResponse<InputStream> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + source);
        }
        return response.body();
    }

    private static long parseLimit(String n) {
        try {
            return Long.parseLong(n);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}

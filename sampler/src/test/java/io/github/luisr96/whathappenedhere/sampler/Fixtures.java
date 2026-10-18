package io.github.luisr96.whathappenedhere.sampler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Shared test helpers: saved entities, synthetic entities and in-memory dumps. */
final class Fixtures {

    static final ObjectMapper MAPPER = JsonMapper.builder().build();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);
    private static final Path ENTITIES = Path.of("../extractor/src/test/resources/entities");

    private Fixtures() {}

    static String line(String name) {
        try {
            return Files.readString(ENTITIES.resolve(name + ".json"), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode entity(String name) {
        return MAPPER.readTree(line(name));
    }

    static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    /** A synthetic item; {@code sitelinks} and {@code claims} are the inner JSON of those objects. */
    static JsonNode synthetic(String id, String sitelinks, String claims) {
        return json("""
                {"type":"item","id":"%s","labels":{"en":{"language":"en","value":"%s"}},\
                "sitelinks":{%s},"claims":{%s}}""".formatted(id, id, sitelinks, claims));
    }

    static String enwiki(String title) {
        return sitelink("enwiki", title);
    }

    static String sitelink(String site, String title) {
        return "\"%s\":{\"site\":\"%s\",\"title\":\"%s\",\"badges\":[]}".formatted(site, site, title);
    }

    static String time(String pid, String time, int precision, String rank) {
        return """
                {"mainsnak":{"snaktype":"value","property":"%s","datavalue":{"value":{"time":"%s",\
                "timezone":0,"before":0,"after":0,"precision":%d,\
                "calendarmodel":"http://www.wikidata.org/entity/Q1985727"},"type":"time"},\
                "datatype":"time"},"type":"statement","rank":"%s"}""".formatted(pid, time, precision, rank);
    }

    static String item(String pid, String qid) {
        return """
                {"mainsnak":{"snaktype":"value","property":"%s","datavalue":{"value":{"entity-type":"item",\
                "numeric-id":%s,"id":"%s"},"type":"wikibase-entityid"},"datatype":"wikibase-item"},\
                "type":"statement","rank":"normal"}""".formatted(pid, qid.substring(1), qid);
    }

    static String coords(double lat, double lon) {
        return """
                {"mainsnak":{"snaktype":"value","property":"P625","datavalue":{"value":{"latitude":%s,\
                "longitude":%s,"altitude":null,"precision":0.0001,\
                "globe":"http://www.wikidata.org/entity/Q2"},"type":"globecoordinate"},\
                "datatype":"globe-coordinate"},"type":"statement","rank":"normal"}""".formatted(lat, lon);
    }

    static String claim(String pid, String... statements) {
        return "\"" + pid + "\":[" + String.join(",", statements) + "]";
    }

    /** Gzipped dump in Wikidata's layout: "[" line, one entity per line with trailing commas but the last, "]". */
    static byte[] gzippedDump(List<String> entityLines, String newline) {
        var text = new StringBuilder("[").append(newline);
        for (int i = 0; i < entityLines.size(); i++) {
            text.append(entityLines.get(i));
            if (i < entityLines.size() - 1) {
                text.append(',');
            }
            text.append(newline);
        }
        text.append(']').append(newline);
        return gzip(text.toString());
    }

    static byte[] gzip(String text) {
        var bytes = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(bytes)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}

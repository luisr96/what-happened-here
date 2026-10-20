package io.github.luisr96.whathappenedhere.importer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic Wikidata entities, one dump line each (mirrors the sampler's test Fixtures). */
final class Entities {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Entities() {}

    static JsonNode json(String line) {
        return MAPPER.readTree(line);
    }

    /** {@code sitelinks} and {@code claims} are the inner JSON of those objects. */
    static String item(String id, String sitelinks, String claims) {
        return """
                {"type":"item","id":"%s","labels":{"en":{"language":"en","value":"Label %s"}},\
                "sitelinks":{%s},"claims":{%s}}""".formatted(id, id, sitelinks, claims);
    }

    static String enwiki(String title) {
        return "\"enwiki\":{\"site\":\"enwiki\",\"title\":\"%s\",\"badges\":[]}".formatted(title);
    }

    static String time(String pid, String time, int precision) {
        return """
                {"mainsnak":{"snaktype":"value","property":"%s","datavalue":{"value":{"time":"%s",\
                "timezone":0,"before":0,"after":0,"precision":%d,\
                "calendarmodel":"http://www.wikidata.org/entity/Q1985727"},"type":"time"},\
                "datatype":"time"},"type":"statement","rank":"normal"}""".formatted(pid, time, precision);
    }

    static String ref(String pid, String qid) {
        return """
                {"mainsnak":{"snaktype":"value","property":"%s","datavalue":{"value":{"entity-type":"item",\
                "numeric-id":%s,"id":"%s"},"type":"wikibase-entityid"},"datatype":"wikibase-item"},\
                "type":"statement","rank":"normal"}""".formatted(pid, qid.substring(1), qid);
    }

    static String coords(double lat, double lon, double precision) {
        return """
                {"mainsnak":{"snaktype":"value","property":"P625","datavalue":{"value":{"latitude":%s,\
                "longitude":%s,"altitude":null,"precision":%s,\
                "globe":"http://www.wikidata.org/entity/Q2"},"type":"globecoordinate"},\
                "datatype":"globe-coordinate"},"type":"statement","rank":"normal"}"""
                .formatted(lat, lon, precision);
    }

    static String claim(String pid, String... statements) {
        return "\"" + pid + "\":[" + String.join(",", statements) + "]";
    }

    static String claims(String... claims) {
        return String.join(",", claims);
    }
}

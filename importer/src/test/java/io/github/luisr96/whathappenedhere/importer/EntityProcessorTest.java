package io.github.luisr96.whathappenedhere.importer;

import static io.github.luisr96.whathappenedhere.importer.Entities.claim;
import static io.github.luisr96.whathappenedhere.importer.Entities.claims;
import static io.github.luisr96.whathappenedhere.importer.Entities.coords;
import static io.github.luisr96.whathappenedhere.importer.Entities.enwiki;
import static io.github.luisr96.whathappenedhere.importer.Entities.item;
import static io.github.luisr96.whathappenedhere.importer.Entities.json;
import static io.github.luisr96.whathappenedhere.importer.Entities.ref;
import static io.github.luisr96.whathappenedhere.importer.Entities.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.luisr96.whathappenedhere.extractor.Event;
import io.github.luisr96.whathappenedhere.extractor.WikidataExtractor;
import org.junit.jupiter.api.Test;

class EntityProcessorTest {

    private final EntityProcessor processor = new EntityProcessor(new WikidataExtractor(Entities.CLOCK));

    @Test
    void itemWithEventsButNoCoordinatesYieldsOnlyTheItem() throws Exception {
        var entity = json(item("Q2", enwiki("Someone"), claims(
                claim("P569", time("P569", "+1850-03-04T00:00:00Z", 11)),
                claim("P19", ref("P19", "Q90")))));

        var record = processor.process(entity);

        assertThat(record).isNotNull();
        assertThat(record.item()).isNotNull();
        assertThat(record.item().itemId()).isEqualTo("Q2");
        assertThat(record.item().events()).extracting(Event::kind).containsExactly(Event.Kind.BORN);
        assertThat(record.placeQid()).isNull();
        assertThat(record.placeCoordinates()).isNull();
    }

    @Test
    void entityWithCoordinatesButNoSitelinkYieldsAPlaceOnlyRecord() throws Exception {
        var entity = json(item("Q90", "", claim("P625", coords(48.8566, 2.3522, 0.0001))));

        var record = processor.process(entity);

        assertThat(record).isNotNull();
        assertThat(record.item()).isNull();
        assertThat(record.placeQid()).isEqualTo("Q90");
        assertThat(record.placeCoordinates().latitude()).isCloseTo(48.8566, within(1e-9));
        assertThat(record.placeCoordinates().longitude()).isCloseTo(2.3522, within(1e-9));
        assertThat(record.placeCoordinates().precisionDegrees()).isCloseTo(0.0001, within(1e-12));
    }

    @Test
    void sitelinkedPlaceWithoutDatesYieldsAPlaceOnlyRecord() throws Exception {
        var entity = json(item("Q91", enwiki("Some town"), claim("P625", coords(10.0, 20.0, 0.01))));

        var record = processor.process(entity);

        assertThat(record).isNotNull();
        assertThat(record.item()).isNull();
        assertThat(record.placeQid()).isEqualTo("Q91");
    }

    @Test
    void itemWithEventsAndCoordinatesYieldsBoth() throws Exception {
        var entity = json(item("Q1", enwiki("Some building"), claims(
                claim("P571", time("P571", "+1900-01-01T00:00:00Z", 11)),
                claim("P625", coords(48.5, 2.25, 0.0001)))));

        var record = processor.process(entity);

        assertThat(record).isNotNull();
        assertThat(record.item().itemId()).isEqualTo("Q1");
        assertThat(record.item().events()).extracting(Event::kind).containsExactly(Event.Kind.FOUNDED);
        assertThat(record.placeQid()).isEqualTo("Q1");
        assertThat(record.placeCoordinates().latitude()).isCloseTo(48.5, within(1e-9));
        assertThat(record.placeCoordinates().longitude()).isCloseTo(2.25, within(1e-9));
    }

    @Test
    void entityWithNeitherEventsNorCoordinatesIsFilteredOut() throws Exception {
        var entity = json(item("Q4", enwiki("Abstract idea"), ""));

        assertThat(processor.process(entity)).isNull();
    }

    @Test
    void unsitelinkedEntityWithDatesButNoCoordinatesIsFilteredOut() throws Exception {
        var entity = json(item("Q6", "", claim("P569", time("P569", "+1850-03-04T00:00:00Z", 11))));

        assertThat(processor.process(entity)).isNull();
    }

    @Test
    void futureOnlyDatesWithoutCoordinatesAreFilteredOut() throws Exception {
        // Fixed clock: 2026-10-18, so a 2030 inception is a future date and yields no event.
        var entity = json(item("Q7", enwiki("Planned thing"), claim("P571", time("P571", "+2030-01-01T00:00:00Z", 11))));

        assertThat(processor.process(entity)).isNull();
    }

    @Test
    void offEarthCoordinatesDoNotMakeAPlace() throws Exception {
        var lunar = """
                {"mainsnak":{"snaktype":"value","property":"P625","datavalue":{"value":{"latitude":-43.3,\
                "longitude":-11.2,"altitude":null,"precision":0.01,\
                "globe":"http://www.wikidata.org/entity/Q405"},"type":"globecoordinate"},\
                "datatype":"globe-coordinate"},"type":"statement","rank":"normal"}""";
        var entity = json(item("Q8", "", claim("P625", lunar)));

        assertThat(processor.process(entity)).isNull();
    }

    @Test
    void propertyEntitiesAreFilteredOut() throws Exception {
        assertThat(processor.process(json("{\"type\":\"property\",\"id\":\"P31\"}"))).isNull();
    }
}

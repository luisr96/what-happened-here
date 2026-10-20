package io.github.luisr96.whathappenedhere.importer;

import static io.github.luisr96.whathappenedhere.importer.Entities.claim;
import static io.github.luisr96.whathappenedhere.importer.Entities.claims;
import static io.github.luisr96.whathappenedhere.importer.Entities.coords;
import static io.github.luisr96.whathappenedhere.importer.Entities.enwiki;
import static io.github.luisr96.whathappenedhere.importer.Entities.item;
import static io.github.luisr96.whathappenedhere.importer.Entities.ref;
import static io.github.luisr96.whathappenedhere.importer.Entities.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fixture: ImportJobTest's FIXTURE with P31 types, plus Q6 (HAPPENED 490 BC, own coordinates,
 * typed battle and siege) and Q8 (HAPPENED 1066, own coordinates, no P31). Staged events:
 * Q1:FOUNDED, Q2:BORN, Q2:DIED (place Q404 missing, so dropped), Q3:HAPPENED, Q5:BORN,
 * Q6:HAPPENED, Q8:HAPPENED. Staged places: Q1, Q90, Q999, Q3, Q91, Q6, Q8; loaded: Q90, Q91.
 */
@SpringBootTest(properties = {
    "import.chunk-size=2",
    "import.max-reconnects=1",
    "import.reconnect-delay=0s",
})
class ImportReporterTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.start();
    }

    private static final String HUMAN = "Q5";
    private static final String BUILDING = "Q41176";
    private static final String BATTLE = "Q178561";
    private static final String SIEGE = "Q188055";

    static final List<String> FIXTURE = List.of(
            item("Q1", enwiki("Building"), claims(
                    claim("P31", ref("P31", BUILDING)),
                    claim("P571", time("P571", "+1900-01-01T00:00:00Z", 11)),
                    claim("P625", coords(48.5, 2.25, 0.0001)))),
            item("Q2", enwiki("Person"), claims(
                    claim("P31", ref("P31", HUMAN)),
                    claim("P569", time("P569", "+1850-03-04T00:00:00Z", 11)),
                    claim("P19", ref("P19", "Q90")),
                    claim("P570", time("P570", "+1900-05-06T00:00:00Z", 11)),
                    claim("P20", ref("P20", "Q404")))),
            item("Q90", "", claim("P625", coords(48.8566, 2.3522, 0.0001))),
            item("Q999", "", claim("P625", coords(1.0, 1.0, 1.0))),
            item("Q3", enwiki("Battle"), claims(
                    claim("P31", ref("P31", BATTLE)),
                    claim("P585", time("P585", "+1500-06-01T00:00:00Z", 11)),
                    claim("P625", coords(-10.5, -20.25, 0.01)))),
            item("Q4", enwiki("Concept"), ""),
            item("Q5", enwiki("Other person"), claims(
                    claim("P31", ref("P31", HUMAN)),
                    claim("P569", time("P569", "+1700-01-01T00:00:00Z", 9)),
                    claim("P19", ref("P19", "Q91")))),
            item("Q91", "", claim("P625", coords(41.9, 12.5, 0.001))),
            item("Q6", enwiki("Marathon"), claims(
                    claim("P31", ref("P31", BATTLE), ref("P31", SIEGE)),
                    claim("P585", time("P585", "-0490-09-12T00:00:00Z", 11)),
                    claim("P625", coords(38.1, 23.97, 0.01)))),
            item("Q8", enwiki("Untyped"), claims(
                    claim("P585", time("P585", "+1066-10-14T00:00:00Z", 11)),
                    claim("P625", coords(50.9, 0.49, 0.001)))));

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        ImportJobTest.FakeDumpOpener fakeDumpOpener() {
            return new ImportJobTest.FakeDumpOpener();
        }
    }

    @Autowired
    ImportService importService;

    @Autowired
    ImportJobTest.FakeDumpOpener opener;

    @Autowired
    ImportReporter reporter;

    private ImportReport report;

    @BeforeEach
    void runImport() {
        var execution = importService.run(opener.register(FIXTURE), 0);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        report = reporter.report(FIXTURE.size());
    }

    @Test
    void entitiesReadIsThePassedCount() {
        assertThat(report.entitiesRead()).isEqualTo(10);
    }

    @Test
    void countsStagedLoadedAndDroppedEvents() {
        assertThat(report.stagedEvents()).isEqualTo(7);
        assertThat(report.loadedEvents()).isEqualTo(6);
        assertThat(report.droppedEvents()).isEqualTo(1);
    }

    @Test
    void countsStagedAndLoadedPlaces() {
        assertThat(report.stagedPlaces()).isEqualTo(7);
        assertThat(report.loadedPlaces()).isEqualTo(2);
    }

    @Test
    void kindsAreOnlyThoseStagedInDeclarationOrder() {
        assertThat(report.kinds().keySet()).containsExactly("HAPPENED", "BORN", "DIED", "FOUNDED");
    }

    @Test
    void happenedHasOnlyOwnCoordinates() {
        var happened = report.kinds().get("HAPPENED");
        assertThat(happened.staged()).isEqualTo(3);
        assertThat(happened.loaded()).isEqualTo(3);
        assertThat(happened.ownCoordinates()).isEqualTo(3);
        assertThat(happened.placeReferences()).isZero();
        assertThat(happened.placeReferencesResolved()).isZero();
        assertThat(happened.resolvedPercent()).isEqualTo(100.0);
    }

    @Test
    void bornHasAllReferencesResolved() {
        var born = report.kinds().get("BORN");
        assertThat(born.staged()).isEqualTo(2);
        assertThat(born.loaded()).isEqualTo(2);
        assertThat(born.ownCoordinates()).isZero();
        assertThat(born.placeReferences()).isEqualTo(2);
        assertThat(born.placeReferencesResolved()).isEqualTo(2);
        assertThat(born.resolvedPercent()).isEqualTo(100.0);
    }

    @Test
    void diedHasAnUnresolvedReference() {
        var died = report.kinds().get("DIED");
        assertThat(died.staged()).isEqualTo(1);
        assertThat(died.loaded()).isZero();
        assertThat(died.ownCoordinates()).isZero();
        assertThat(died.placeReferences()).isEqualTo(1);
        assertThat(died.placeReferencesResolved()).isZero();
        assertThat(died.resolvedPercent()).isLessThan(100.0).isEqualTo(0.0);
        assertThat(died.topTypes()).as("types count loaded events only").isEmpty();
    }

    @Test
    void foundedCountsItsOwnCoordinates() {
        var founded = report.kinds().get("FOUNDED");
        assertThat(founded.staged()).isEqualTo(1);
        assertThat(founded.loaded()).isEqualTo(1);
        assertThat(founded.ownCoordinates()).isEqualTo(1);
        assertThat(founded.placeReferences()).isZero();
    }

    @Test
    void topTypesCountEveryP31OfAnItemAndSkipUntypedItems() {
        assertThat(report.kinds().get("HAPPENED").topTypes()).containsExactly(
                new ImportReport.TypeCount(BATTLE, 2),
                new ImportReport.TypeCount(SIEGE, 1));
        assertThat(report.kinds().get("BORN").topTypes())
                .containsExactly(new ImportReport.TypeCount(HUMAN, 2));
        assertThat(report.kinds().get("FOUNDED").topTypes())
                .containsExactly(new ImportReport.TypeCount(BUILDING, 1));
    }

    @Test
    void precisionsCountLoadedEventsInPrecisionOrder() {
        assertThat(report.precisions()).containsExactly(Map.entry("DAY", 5L), Map.entry("YEAR", 1L));
    }

    @Test
    void millenniaUseHistoricalNumberingOldestFirst() {
        assertThat(report.millennia())
                .containsExactly(Map.entry("-1000..-1", 1L), Map.entry("1001..2000", 5L));
    }

    @Test
    void tableAndDatabaseSizesArePositive() {
        assertThat(report.tableBytes().keySet())
                .containsExactlyInAnyOrder("events", "places", "timeline_cells");
        assertThat(report.tableBytes().values()).allSatisfy(bytes -> assertThat(bytes).isPositive());
        assertThat(report.databaseBytes()).isPositive();
    }

    @Test
    void markdownMentionsTheKeyNumbers() {
        var markdown = report.toMarkdown();
        assertThat(markdown).contains("10");
        assertThat(markdown.lines()).anyMatch(l -> l.contains("HAPPENED") && l.contains("3"));
        assertThat(markdown.lines()).anyMatch(l -> l.contains("BORN") && l.contains("2"));
        assertThat(markdown.lines()).anyMatch(l -> l.contains("FOUNDED") && l.contains("1"));
        assertThat(markdown).contains(BATTLE, SIEGE, HUMAN, BUILDING, "DAY", "YEAR");
        long mb = Math.round(report.databaseBytes() / (1024.0 * 1024.0));
        assertThat(markdown).contains(mb + " MB");
    }

    @Test
    void resolvedPercentIsHundredWithoutReferences() {
        var stats = new ImportReport.KindStats(3, 3, 3, 0, 0, List.of());
        assertThat(stats.resolvedPercent()).isEqualTo(100.0);
    }

    @Test
    void resolvedPercentIsTheResolvedShareOfReferences() {
        var stats = new ImportReport.KindStats(4, 3, 0, 4, 3, List.of());
        assertThat(stats.resolvedPercent()).isCloseTo(75.0, within(1e-9));
    }
}

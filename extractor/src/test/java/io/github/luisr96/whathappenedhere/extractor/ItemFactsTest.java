package io.github.luisr96.whathappenedhere.extractor;

import static io.github.luisr96.whathappenedhere.extractor.TimeValues.MAPPER;
import static io.github.luisr96.whathappenedhere.extractor.TimeValues.entity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class ItemFactsTest {

    /** Parses JSON written with single quotes to keep the inline fixtures readable. */
    private static JsonNode json(String text) {
        return MAPPER.readTree(text.replace('\'', '"'));
    }

    private static JsonNode sitelinks(String... keys) {
        ObjectNode node = MAPPER.createObjectNode();
        for (String key : keys) {
            node.putObject(key).put("site", key).put("title", "x");
        }
        return node;
    }

    private static String p31(String rank, String qid) {
        return "{'mainsnak':{'snaktype':'value','property':'P31','datavalue':{'value':"
                + "{'entity-type':'item','id':'" + qid + "'},'type':'wikibase-entityid'}},"
                + "'type':'statement','rank':'" + rank + "'}";
    }

    @Test
    void romeCountsOnlyWikipedias() {
        assertEquals(273, ItemFacts.importance(entity("Q220").path("sitelinks")));
    }

    @Test
    void ivanovkaHasThreeWikipedias() {
        assertEquals(3, ItemFacts.importance(entity("Q50829").path("sitelinks")));
    }

    @Test
    void entityWithoutSitelinksHasZeroImportance() {
        assertEquals(0, ItemFacts.importance(entity("synthetic-no-sitelinks").path("sitelinks")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"commonswiki", "specieswiki", "wikidatawiki", "metawiki", "mediawikiwiki",
            "sourceswiki", "outreachwiki", "incubatorwiki", "wikimaniawiki", "foundationwiki",
            "wikifunctionswiki", "abstractwiki"})
    void projectWikisDoNotCount(String key) {
        assertEquals(0, ItemFacts.importance(sitelinks(key)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"enwikivoyage", "enwikiquote", "enwikisource", "enwiktionary",
            "enwikibooks", "enwikinews", "enwikiversity"})
    void sisterProjectsDoNotCount(String key) {
        assertEquals(0, ItemFacts.importance(sitelinks(key)));
    }

    @Test
    void languageCodesWithUnderscoresCount() {
        assertEquals(3, ItemFacts.importance(sitelinks("zh_yuewiki", "be_x_oldwiki", "enwiki")));
    }

    @Test
    void mixedSitelinksCountOnlyWikipedias() {
        assertEquals(2, ItemFacts.importance(
                sitelinks("enwiki", "dewiki", "commonswiki", "enwikiquote", "wikidatawiki")));
    }

    @Test
    void missingOrMalformedSitelinksGiveZero() {
        assertEquals(0, ItemFacts.importance(null));
        assertEquals(0, ItemFacts.importance(MAPPER.missingNode()));
        assertEquals(0, ItemFacts.importance(MAPPER.nullNode()));
        assertEquals(0, ItemFacts.importance(json("['enwiki']")));
        assertEquals(0, ItemFacts.importance(MAPPER.createObjectNode()));
    }

    @Test
    void romeUsesEnglishLabel() {
        assertEquals("Rome", ItemFacts.label(entity("Q220").path("labels")));
    }

    @Test
    void marieCurieFallsBackToMul() {
        assertEquals("Marie Curie", ItemFacts.label(entity("Q7186").path("labels")));
    }

    @Test
    void macronFallsBackToMul() {
        assertEquals("Emmanuel Macron", ItemFacts.label(entity("Q3052772").path("labels")));
    }

    @Test
    void englishWinsOverMul() {
        assertEquals("English", ItemFacts.label(json(
                "{'mul':{'language':'mul','value':'Mul'},'en':{'language':'en','value':'English'}}")));
    }

    @Test
    void mulWinsOverOtherLanguages() {
        assertEquals("Mul", ItemFacts.label(json(
                "{'de':{'language':'de','value':'Deutsch'},'mul':{'language':'mul','value':'Mul'}}")));
    }

    @Test
    void otherwiseAlphabeticallyFirstLanguageCode() {
        assertEquals("Deutsch", ItemFacts.label(json(
                "{'fr':{'language':'fr','value':'Francais'},'de':{'language':'de','value':'Deutsch'},"
                        + "'es':{'language':'es','value':'Espanol'}}")));
    }

    @Test
    void emptyEnglishLabelFallsThroughToMul() {
        assertEquals("Mul", ItemFacts.label(json(
                "{'en':{'language':'en','value':''},'mul':{'language':'mul','value':'Mul'}}")));
    }

    @Test
    void unusableLabelsAreSkippedInFallback() {
        assertEquals("Francais", ItemFacts.label(json(
                "{'ar':{'language':'ar','value':''},'bg':{'language':'bg'},"
                        + "'ca':{'language':'ca','value':42},'fr':{'language':'fr','value':'Francais'}}")));
    }

    @Test
    void noUsableLabelGivesNull() {
        assertNull(ItemFacts.label(json("{'en':{'language':'en','value':''}}")));
        assertNull(ItemFacts.label(MAPPER.createObjectNode()));
        assertNull(ItemFacts.label(null));
        assertNull(ItemFacts.label(MAPPER.missingNode()));
        assertNull(ItemFacts.label(MAPPER.nullNode()));
        assertNull(ItemFacts.label(json("['Rome']")));
    }

    @Test
    void mozartIsHuman() {
        assertTrue(ItemFacts.instanceOf(entity("Q254").path("claims")).contains("Q5"));
    }

    @Test
    void sherlockIsFictionalHuman() {
        assertTrue(ItemFacts.instanceOf(entity("Q4653").path("claims")).contains("Q15632617"));
    }

    @Test
    void keepsPreferredAndNormalInStatementOrderWithoutDuplicates() {
        JsonNode claims = json("{'P31':[" + p31("normal", "Q1") + "," + p31("preferred", "Q2") + ","
                + p31("normal", "Q1") + "," + p31("normal", "Q3") + "]}");
        assertEquals(List.of("Q1", "Q2", "Q3"), ItemFacts.instanceOf(claims));
    }

    @Test
    void skipsDeprecatedValues() {
        JsonNode claims = json("{'P31':[" + p31("deprecated", "Q1") + "," + p31("normal", "Q2") + "]}");
        assertEquals(List.of("Q2"), ItemFacts.instanceOf(claims));
    }

    @Test
    void skipsStatementsWithoutRecognisedRank() {
        String noRank = p31("normal", "Q1").replace(",'rank':'normal'", "");
        JsonNode claims = json("{'P31':[" + noRank + "," + p31("bogus", "Q2") + ","
                + p31("normal", "Q3") + "," + p31("preferred", "Q4") + "]}");
        assertEquals(List.of("Q3", "Q4"), ItemFacts.instanceOf(claims));
    }

    @Test
    void skipsSomevalueNovalueAndMalformed() {
        JsonNode claims = json("{'P31':["
                + "{'mainsnak':{'snaktype':'somevalue','property':'P31'},'rank':'normal'},"
                + "{'mainsnak':{'snaktype':'novalue','property':'P31'},'rank':'normal'},"
                + "{'mainsnak':{'snaktype':'value','property':'P31'},'rank':'normal'},"
                + "{'rank':'normal'},"
                + p31("normal", "Q5") + "]}");
        assertEquals(List.of("Q5"), ItemFacts.instanceOf(claims));
    }

    @Test
    void missingClaimsGiveEmptyList() {
        assertEquals(List.of(), ItemFacts.instanceOf(null));
        assertEquals(List.of(), ItemFacts.instanceOf(MAPPER.missingNode()));
        assertEquals(List.of(), ItemFacts.instanceOf(MAPPER.createObjectNode()));
        assertEquals(List.of(), ItemFacts.instanceOf(json("{'P569':[]}")));
    }

    @Test
    void sherlockIsFictional() {
        assertTrue(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q4653").path("claims"))));
    }

    @Test
    void battleOfTheHornburgIsFictional() {
        assertTrue(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q1764229").path("claims"))));
    }

    @Test
    void syntheticFictionalIsFictional() {
        assertTrue(ItemFacts.isFictional(
                ItemFacts.instanceOf(entity("synthetic-fictional").path("claims"))));
    }

    @Test
    void mozartIsNotFictional() {
        assertFalse(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q254").path("claims"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q15632617", "Q95074", "Q15707521", "Q3658341",
            "Q4271324", "Q13002315", "Q178885", "Q15773347"})
    void eachDenylistedTypeAloneIsFictional(String qid) {
        assertTrue(ItemFacts.isFictional(List.of(qid)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q4271324", "Q13002315", "Q178885", "Q15773347"})
    void denylistedTypesAmongOtherTypesAreFictional(String qid) {
        assertTrue(ItemFacts.isFictional(List.of("Q515", qid)));
    }

    @Test
    void zeusIsFictionalThroughDeitySubclasses() {
        assertTrue(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q34201").path("claims"))));
    }

    // Zeus's P31 values are all subclasses of deity (Q178885), none is a denylisted root itself.
    @ParameterizedTest
    @ValueSource(strings = {"Q842697", "Q22989102", "Q1589492", "Q113103481"})
    void eachZeusTypeAloneIsFictional(String qid) {
        assertTrue(ItemFacts.isFictional(List.of(qid)));
    }

    @Test
    void humanTypeOverridesDenylistedSubclass() {
        assertFalse(ItemFacts.isFictional(List.of("Q22989102", "Q5")));
    }

    // Wikidata classes human biblical figure under a denylisted class; the keep-list must still win.
    @Test
    void biblicalHumanAloneIsNotFictionalDespiteSubclassing() {
        assertFalse(ItemFacts.isFictional(List.of("Q20643955")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q515", "Q178561", "Q5", "Q11424", "Q4830453", "Q41176"})
    void realWorldTypesAreNotFictional(String qid) {
        assertFalse(ItemFacts.isFictional(List.of(qid)));
    }

    @Test
    void humanTypeOverridesFilmCharacter() {
        assertFalse(ItemFacts.isFictional(List.of("Q5", "Q15773347")));
    }

    @Test
    void disputedHumanOverridesMythicalCharacter() {
        assertFalse(ItemFacts.isFictional(List.of("Q4271324", "Q21070568")));
    }

    @Test
    void biblicalHumanOverridesFilmCharacter() {
        assertFalse(ItemFacts.isFictional(List.of("Q20643955", "Q15773347")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q5", "Q21070568", "Q20643955"})
    void humanTypeOverridesEveryDenylistedType(String human) {
        assertFalse(ItemFacts.isFictional(List.of("Q15632617", "Q95074", "Q15707521", "Q3658341",
                "Q4271324", "Q13002315", "Q178885", "Q15773347", human)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q21070568", "Q20643955"})
    void humanTypesAloneAreNotFictional(String human) {
        assertFalse(ItemFacts.isFictional(List.of(human)));
    }

    @Test
    void fictionalHumanAloneIsFictional() {
        assertTrue(ItemFacts.isFictional(List.of("Q15632617")));
    }

    @Test
    void kingArthurIsNotFictional() {
        assertFalse(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q45792").path("claims"))));
    }

    @Test
    void hagarIsNotFictional() {
        assertFalse(ItemFacts.isFictional(ItemFacts.instanceOf(entity("Q214617").path("claims"))));
    }

    @Test
    void syntheticMythicalIsFictional() {
        assertTrue(ItemFacts.isFictional(
                ItemFacts.instanceOf(entity("synthetic-mythical").path("claims"))));
    }

    @Test
    void syntheticHumanFilmCharacterIsNotFictional() {
        assertFalse(ItemFacts.isFictional(
                ItemFacts.instanceOf(entity("synthetic-human-film-character").path("claims"))));
    }

    @Test
    void emptyOrRealTypesAreNotFictional() {
        assertFalse(ItemFacts.isFictional(List.of()));
        assertFalse(ItemFacts.isFictional(List.of("Q5", "Q515")));
    }

    @Test
    void zancleanIsGeologic() {
        assertTrue(ItemFacts.isGeologic(ItemFacts.instanceOf(entity("Q146094").path("claims"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q4005761", "Q4005689", "Q108256", "Q630830", "Q392928", "Q754897",
            "Q568683", "Q2669627", "Q2464485", "Q403523", "Q312468", "Q1123245"})
    void eachGeologicUnitTypeAloneIsGeologic(String qid) {
        assertTrue(ItemFacts.isGeologic(List.of(qid)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q11514315", "Q15401633", "Q15401699", "Q178561", "Q5", "Q515"})
    void historicalAndRealWorldTypesAreNotGeologic(String qid) {
        assertFalse(ItemFacts.isGeologic(List.of(qid)));
    }

    @Test
    void emptyTypesAreNotGeologic() {
        assertFalse(ItemFacts.isGeologic(List.of()));
    }

    // The human keep-list only overrides fictional; a human type must not rescue a geologic one.
    @Test
    void humanTypeDoesNotOverrideGeologic() {
        assertTrue(ItemFacts.isGeologic(List.of("Q5", "Q1123245")));
    }

    @Test
    void geologicTypeIsNotFictional() {
        assertFalse(ItemFacts.isFictional(List.of("Q1123245")));
    }

    @Test
    void fictionalTypeIsNotGeologic() {
        assertFalse(ItemFacts.isGeologic(List.of("Q178885")));
    }

    // Q144644 (2011-12 Pro12) is typed only as a United Rugby Championship season.
    @Test
    void pro12SeasonIsSeason() {
        assertTrue(ItemFacts.isSeason(ItemFacts.instanceOf(entity("Q144644").path("claims"))));
    }

    // Q284163 (2022 FIFA World Cup) is a tournament also typed as the generic sports season.
    @Test
    void worldCupIsNotSeason() {
        assertFalse(ItemFacts.isSeason(ItemFacts.instanceOf(entity("Q284163").path("claims"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q1539532", "Q131459694", "Q26897352", "Q5290181", "Q47465437",
            "Q137387240", "Q76941636", "Q106562461", "Q116621413", "Q26887310", "Q26894053"})
    void eachSeasonTypeAloneIsSeason(String qid) {
        assertTrue(ItemFacts.isSeason(List.of(qid)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q27020041", "Q500834", "Q1478437", "Q5", "Q515"})
    void genericSeasonTournamentsAndOtherTypesAreNotSeason(String qid) {
        assertFalse(ItemFacts.isSeason(List.of(qid)));
    }

    @Test
    void emptyTypesAreNotSeason() {
        assertFalse(ItemFacts.isSeason(List.of()));
    }

    @Test
    void humanTypeDoesNotOverrideSeason() {
        assertTrue(ItemFacts.isSeason(List.of("Q5", "Q116621413")));
    }

    @Test
    void seasonTypeIsNotFictionalOrGeologic() {
        assertFalse(ItemFacts.isFictional(List.of("Q116621413")));
        assertFalse(ItemFacts.isGeologic(List.of("Q116621413")));
    }

    @Test
    void geologicTypeIsNotSeason() {
        assertFalse(ItemFacts.isSeason(List.of("Q1123245")));
    }
}

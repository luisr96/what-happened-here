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
    void villanovaHasTwoWikipedias() {
        assertEquals(2, ItemFacts.importance(entity("Q4012882").path("sitelinks")));
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
    @ValueSource(strings = {"Q15632617", "Q95074", "Q15707521", "Q3658341"})
    void eachDenylistedTypeIsFictional(String qid) {
        assertTrue(ItemFacts.isFictional(List.of("Q5", qid)));
    }

    @Test
    void emptyOrRealTypesAreNotFictional() {
        assertFalse(ItemFacts.isFictional(List.of()));
        assertFalse(ItemFacts.isFictional(List.of("Q5", "Q515")));
    }
}

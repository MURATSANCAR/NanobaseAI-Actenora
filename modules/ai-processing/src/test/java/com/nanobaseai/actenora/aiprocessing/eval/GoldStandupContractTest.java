package com.nanobaseai.actenora.aiprocessing.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gold Standard v0.1 contract checks for the 15dk daily standup fixture.
 * Does not invoke the extraction pipeline.
 */
class GoldStandupContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CUE_ID = Pattern.compile("(?m)^(\\d+)\\s*$");

    private static JsonNode gold;
    private static Set<Integer> fixtureCueIds;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = GoldStandupContractTest.class.getResourceAsStream(
                "/aiprocessing/eval/gold/01_15dk_daily_standup.gold.v1.json")) {
            gold = MAPPER.readTree(in);
        }
        String vtt;
        try (InputStream in = GoldStandupContractTest.class.getResourceAsStream(
                "/aiprocessing/eval/01_15dk_daily_standup.vtt")) {
            vtt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        fixtureCueIds = new HashSet<>();
        Matcher m = CUE_ID.matcher(vtt);
        while (m.find()) {
            fixtureCueIds.add(Integer.parseInt(m.group(1)));
        }
    }

    @Test
    void goldJsonParsesWithExpectedMetadata() {
        assertEquals("1.0", gold.path("schemaVersion").asText());
        assertEquals("0.1", gold.path("goldVersion").asText());
        assertEquals("01_15dk_daily_standup.vtt", gold.path("fixture").asText());
        assertEquals("tr", gold.path("language").asText());
        assertEquals("DAILY_STANDUP", gold.path("meetingType").asText());
        assertEquals("f9c699f", gold.path("baselineCommits").path("control").asText());
        assertEquals("472172a", gold.path("baselineCommits").path("candidate").asText());
    }

    @Test
    void goldIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (String section : new String[]{
                "topics", "issues", "decisions", "actionItems", "risks",
                "importantFacts", "openQuestions", "proposals", "optionalItems"
        }) {
            for (JsonNode n : gold.path(section)) {
                String id = n.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                assertTrue(ids.add(id), "duplicate gold id: " + id);
            }
        }
        for (JsonNode n : gold.path("compoundActionAssertions")) {
            String id = n.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                assertTrue(ids.add(id), "duplicate compound id: " + id);
            }
        }
    }

    @Test
    void allEvidenceCueIdsExistInFixture() {
        Set<Integer> referenced = new HashSet<>();
        collectCueIds(gold, referenced);
        for (int cue : referenced) {
            assertTrue(fixtureCueIds.contains(cue), "missing fixture cue: " + cue);
        }
        assertFalse(referenced.isEmpty());
    }

    @Test
    void requiredCountsMatchProtocol() {
        assertEquals(2, countRequired("decisions"));
        assertEquals(7, countRequired("actionItems"));
        assertEquals(2, countRequired("risks"));
        assertEquals(2, countRequired("importantFacts"));
        assertEquals(12, countRequired("openQuestions"));
    }

    @Test
    void compoundActionAssertionsRequireSplitOnCues27And51() {
        JsonNode assertions = gold.path("compoundActionAssertions");
        assertTrue(assertions.isArray() && assertions.size() >= 2);
        boolean found27 = false;
        boolean found51 = false;
        for (JsonNode a : assertions) {
            int cue = a.path("cueId").asInt();
            if (cue == 27) {
                found27 = true;
                assertEquals(2, a.path("expectedActionCount").asInt());
                assertTrue(a.path("mustSplit").asBoolean());
                assertEquals(Set.of("A-03", "A-04"), setOf(a.path("expectedActionIds")));
            }
            if (cue == 51) {
                found51 = true;
                assertEquals(2, a.path("expectedActionCount").asInt());
                assertTrue(a.path("mustSplit").asBoolean());
                assertEquals(Set.of("A-06", "A-07"), setOf(a.path("expectedActionIds")));
            }
        }
        assertTrue(found27);
        assertTrue(found51);
    }

    @Test
    void forbiddenStatusQuoCues19And43AreDefined() {
        boolean cue19 = false;
        boolean cue43 = false;
        for (JsonNode f : gold.path("forbiddenExtractions")) {
            int cue = f.path("cueId").asInt();
            if (cue == 19 || cue == 43) {
                assertEquals("STATUS_QUO_DECISION_FALSE_POSITIVE", f.path("reasonCode").asText());
                assertTrue(f.path("criticalIfExtractedAsDecision").asBoolean(false)
                        || f.path("critical").asBoolean(false)
                        || listContains(f.path("forbiddenTypes"), "decision"));
                if (cue == 19) {
                    cue19 = true;
                } else {
                    cue43 = true;
                }
            }
        }
        assertTrue(cue19);
        assertTrue(cue43);
    }

    @Test
    void dateCrossoverAssertionsPresent() {
        boolean a03a04 = false;
        boolean a06a07 = false;
        for (JsonNode a : gold.path("criticalAssertions")) {
            String type = a.path("type").asText("");
            if (!"DATE_OWNER_BINDING".equals(type) && !"DATE_CROSSOVER".equals(type)) {
                // also accept mustNotInheritDateFrom / mustNotTransferDateTo on action items
                continue;
            }
            String dated = a.path("datedActionId").asText("");
            String undated = a.path("undatedActionId").asText("");
            if (("A-03".equals(dated) && "A-04".equals(undated))
                    || ("A-03".equals(undated) && "A-04".equals(dated))) {
                a03a04 = true;
            }
            if (("A-07".equals(dated) && "A-06".equals(undated))
                    || ("A-07".equals(undated) && "A-06".equals(dated))) {
                a06a07 = true;
            }
        }
        // Fallback: action-item level annotations
        for (JsonNode a : gold.path("actionItems")) {
            String id = a.path("id").asText();
            if ("A-04".equals(id) && a.has("mustNotInheritDateFrom")) {
                a03a04 = a03a04 || setOf(a.path("mustNotInheritDateFrom")).contains("A-03")
                        || "A-03".equals(a.path("mustNotInheritDateFrom").asText());
            }
            if ("A-03".equals(id) && a.has("mustNotTransferDateTo")) {
                a03a04 = a03a04 || setOf(a.path("mustNotTransferDateTo")).contains("A-04")
                        || "A-04".equals(a.path("mustNotTransferDateTo").asText());
            }
            if ("A-06".equals(id) && a.has("mustNotInheritDateFrom")) {
                a06a07 = a06a07 || setOf(a.path("mustNotInheritDateFrom")).contains("A-07")
                        || "A-07".equals(a.path("mustNotInheritDateFrom").asText());
            }
            if ("A-07".equals(id) && a.has("mustNotTransferDateTo")) {
                a06a07 = a06a07 || setOf(a.path("mustNotTransferDateTo")).contains("A-06")
                        || "A-06".equals(a.path("mustNotTransferDateTo").asText());
            }
        }
        assertTrue(a03a04, "A-03/A-04 date crossover assertion missing");
        assertTrue(a06a07, "A-06/A-07 date crossover assertion missing");
    }

    @Test
    void optionalItemsDoNotCountAsRecallFailures() {
        for (JsonNode o : gold.path("optionalItems")) {
            assertFalse(o.path("required").asBoolean(true));
            assertFalse(o.path("missingIsRecallFailure").asBoolean(true));
        }
    }

    @Test
    void acceptanceGatesPresent() {
        JsonNode g = gold.path("acceptanceGates");
        assertEquals(85, g.path("overallScoreMinimum").asInt());
        assertEquals(0.95, g.path("criticalDecisionRecallMinimum").asDouble(), 1e-9);
        assertEquals(0.90, g.path("actionRecallMinimum").asDouble(), 1e-9);
        assertEquals(0, g.path("hallucinatedDecisionMaximum").asInt());
        assertEquals(0, g.path("statusQuoDecisionFalsePositiveMaximum").asInt());
        assertEquals(0.95, g.path("criticalGatePassRateMinimum").asDouble(), 1e-9);
    }

    private static int countRequired(String section) {
        int n = 0;
        for (JsonNode item : gold.path(section)) {
            if (item.path("required").asBoolean(false)) {
                n++;
            }
        }
        return n;
    }

    private static void collectCueIds(JsonNode node, Set<Integer> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (node.has("cueId") && node.get("cueId").canConvertToInt()) {
                out.add(node.get("cueId").asInt());
            }
            if (node.has("evidenceCueIds") && node.get("evidenceCueIds").isArray()) {
                for (JsonNode c : node.get("evidenceCueIds")) {
                    if (c.canConvertToInt()) {
                        out.add(c.asInt());
                    }
                }
            }
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                collectCueIds(node.get(names.next()), out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectCueIds(child, out);
            }
        }
    }

    private static Set<String> setOf(JsonNode arr) {
        if (arr == null || arr.isMissingNode() || arr.isNull()) {
            return Set.of();
        }
        if (arr.isTextual()) {
            return Set.of(arr.asText());
        }
        if (!arr.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(arr.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }

    private static boolean listContains(JsonNode arr, String value) {
        if (arr == null || !arr.isArray()) {
            return false;
        }
        for (JsonNode n : arr) {
            if (value.equals(n.asText())) {
                return true;
            }
        }
        return false;
    }
}

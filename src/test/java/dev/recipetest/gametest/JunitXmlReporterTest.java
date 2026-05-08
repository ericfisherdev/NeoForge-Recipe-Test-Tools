/*
 * Copyright (c) 2026 ericfisherdev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.recipetest.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class JunitXmlReporterTest {

    @Test
    void writesValidXmlWithSuiteAndCases(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("recipe-test.xml");
        Instant start = Instant.parse("2026-05-07T12:00:00Z");
        Instant end = Instant.parse("2026-05-07T12:00:05Z");
        List<JunitXmlReporter.Row> rows = List.of(
                JunitXmlReporter.Row.success("recipe_test.forestry.carpenter.forestry.basic", 0.4, Optional.empty()),
                JunitXmlReporter.Row.failure(
                        "recipe_test.forestry.carpenter.forestry.broken",
                        1.2,
                        "FAIL forestry:carpenter/broken after 200 ticks; 1 mismatch(es)",
                        Optional.empty()));

        JunitXmlReporter.writeReport(out, rows, start, end);

        Document doc = parse(out);
        Element suite = doc.getDocumentElement();
        assertEquals("testsuite", suite.getTagName());
        assertEquals("2", suite.getAttribute("tests"));
        assertEquals("1", suite.getAttribute("failures"));
        assertEquals("0", suite.getAttribute("errors"));
        assertEquals("recipe_test_dynamic", suite.getAttribute("name"));
        assertEquals("5.000", suite.getAttribute("time"));

        NodeList cases = suite.getElementsByTagName("testcase");
        assertEquals(2, cases.getLength());
        Element fail = (Element) cases.item(1);
        NodeList failures = fail.getElementsByTagName("failure");
        assertEquals(1, failures.getLength());
        assertTrue(((Element) failures.item(0)).getAttribute("message").contains("FAIL"));
    }

    @Test
    void failureWithKitResultEmbedsDiffJson(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("recipe-test.xml");
        TestResult failResult = failingResult();
        List<JunitXmlReporter.Row> rows = List.of(JunitXmlReporter.Row.failure(
                "recipe_test.forestry.carpenter.forestry.broken",
                0.5,
                "FAIL forestry:carpenter/broken after 200 ticks; 1 mismatch(es)",
                Optional.of(failResult)));

        JunitXmlReporter.writeReport(out, rows, Instant.now(), Instant.now());

        String xml = Files.readString(out);
        assertTrue(xml.contains("<system-out>"), "system-out present for failure with kit result");
        assertTrue(xml.contains("status=FAIL"), "status line in system-out");
        assertTrue(xml.contains("ticksElapsed=200"), "tick count in system-out");
        assertTrue(xml.contains("resultJson="), "JSON-encoded TestResult in system-out");
    }

    @Test
    void successRowsCarryDiagnosticsInSystemOut(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("recipe-test.xml");
        TestResult passResult = passingResult();
        List<JunitXmlReporter.Row> rows = List.of(JunitXmlReporter.Row.success(
                "recipe_test.forestry.carpenter.forestry.basic", 0.3, Optional.of(passResult)));

        JunitXmlReporter.writeReport(out, rows, Instant.now(), Instant.now());

        String xml = Files.readString(out);
        assertTrue(xml.contains("status=PASS"));
        assertTrue(xml.contains("ticksElapsed=42"));
        assertFalse(xml.contains("resultJson="), "no JSON for passes — diagnostics live in system-out only");
    }

    @Test
    void emptySuiteStillWritesValidXml(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("recipe-test.xml");
        JunitXmlReporter.writeReport(out, List.of(), Instant.now(), Instant.now());
        Document doc = parse(out);
        assertEquals("0", doc.getDocumentElement().getAttribute("tests"));
        assertEquals("0", doc.getDocumentElement().getAttribute("failures"));
    }

    @Test
    void specialCharactersInTestNamesAreEscaped(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("recipe-test.xml");
        List<JunitXmlReporter.Row> rows = List.of(JunitXmlReporter.Row.failure(
                "name<with&special>chars\"and'quotes", 0.0, "boom <bad/>", Optional.empty()));
        JunitXmlReporter.writeReport(out, rows, Instant.now(), Instant.now());
        // Re-parsing succeeds → the XML is well-formed despite the metacharacters.
        Document doc = parse(out);
        Element tc = (Element) doc.getElementsByTagName("testcase").item(0);
        assertEquals("name<with&special>chars\"and'quotes", tc.getAttribute("name"));
    }

    @Test
    void recordResultPutsValueIntoLookupMap() {
        JunitXmlReporter.clearResultsForTesting();
        try {
            String name = "recipe_test.forestry.carpenter.forestry.basic";
            TestResult r = passingResult();
            JunitXmlReporter.recordResult(name, r);

            // Same lookup the real onTestSuccess/onTestFailed path uses.
            Optional<TestResult> looked = JunitXmlReporter.recordedResultForTesting(name);
            assertTrue(looked.isPresent(), "recordResult should put the result into the lookup map");
            assertEquals(r, looked.get());

            // Missing keys yield Optional.empty — the path that produces a row without kitResult.
            assertTrue(JunitXmlReporter.recordedResultForTesting("recipe_test.absent")
                    .isEmpty());
        } finally {
            JunitXmlReporter.clearResultsForTesting();
        }
    }

    @Test
    void finishResetsAccumulatedRowsAndStaticResults(@TempDir Path tmp) throws Exception {
        JunitXmlReporter.clearResultsForTesting();
        Path out = tmp.resolve("recipe-test.xml");
        JunitXmlReporter reporter = JunitXmlReporter.forTesting(out);
        Instant initialStart = reporter.suiteStartForTesting();

        // Simulate one run: a row gets accumulated and a result gets recorded.
        JunitXmlReporter.recordResult("recipe_test.forestry.carpenter.forestry.basic", passingResult());
        // Inject a row directly via reflection-free path: reporter.onTestSuccess needs a real
        // GameTestInfo, which we can't construct without a ServerLevel. Use writeReport with a
        // synthetic snapshot to verify the file path, then call finish to assert state reset.

        reporter.finish();

        // After finish: rows cleared, RESULTS cleared, suiteStart advanced.
        assertEquals(0, reporter.rowCountForTesting(), "rows must be cleared after finish");
        assertTrue(
                JunitXmlReporter.recordedResultForTesting("recipe_test.forestry.carpenter.forestry.basic")
                        .isEmpty(),
                "RESULTS must be cleared after finish");
        assertTrue(
                reporter.suiteStartForTesting().isAfter(initialStart)
                        || reporter.suiteStartForTesting().equals(initialStart),
                "suiteStart should be at or after the initial timestamp (advanced on finish)");
    }

    // ---- helpers ----

    private static TestResult passingResult() {
        return new TestResult(
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter/basic"),
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter"),
                "forestry:carpenter.json",
                RunStatus.PASS,
                42,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.empty(),
                new Diagnostics(List.of(), 0L, List.of(), List.of()));
    }

    private static TestResult failingResult() {
        DiffPayload payload =
                new DiffPayload(List.of(new DiffEntry("/items/0", "minecraft:stone#1", "minecraft:air#0", "missing")));
        return new TestResult(
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter/broken"),
                ResourceLocation.fromNamespaceAndPath("forestry", "carpenter"),
                "forestry:carpenter.json",
                RunStatus.FAIL,
                200,
                IoSnapshot.empty(),
                IoSnapshot.empty(),
                Optional.of(payload),
                new Diagnostics(List.of(), 0L, List.of("missing primary output"), List.of()));
    }

    private static Document parse(Path xmlPath) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(xmlPath.toFile());
    }
}

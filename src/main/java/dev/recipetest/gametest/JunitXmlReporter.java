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

import com.mojang.logging.LogUtils;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import dev.recipetest.command.ReportFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Writes a JUnit-format XML report to {@code build/gametest/results/recipe-test.xml} so
 * GitHub Actions test reporters (and any Surefire-compatible consumer) render dynamic
 * recipe-test outcomes alongside regular unit tests.
 *
 * <p><b>How it plugs in.</b> Vanilla's {@link GlobalTestReporter} delegates each pass / fail to a
 * single {@link TestReporter}. We replace that reporter at GameTest registration time with a
 * wrapping instance that records its observations into in-memory rows and forwards every call to
 * the previously installed reporter — the vanilla {@code LogTestReporter} keeps logging to the
 * console. {@link #finish()} then writes the assembled XML.
 *
 * <p><b>How it gets recipe-level detail.</b> {@link DynamicGameTestGenerator} captures the
 * harness's {@link TestResult} for each test and posts it to {@link #recordResult}. The reporter
 * looks up the matching result by {@link GameTestInfo#getTestName()} when serialising, so failures
 * carry the diff JSON and passes carry diagnostics in {@code <system-out>}.
 *
 * <p><b>One-shot install.</b> Replacing {@code GlobalTestReporter} is a static, non-idempotent
 * mutation. {@link #install} guards against double-install — the GameTest server only boots once
 * per JVM, but {@code RegisterGameTestsEvent} can fire multiple times during datapack reloads in
 * the dev client.
 */
public final class JunitXmlReporter implements TestReporter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Default output path. Resolved against the current working dir, which Gradle sets to the
     *  module root when invoking {@code runGameTestServer}. */
    static final String OUTPUT_RELATIVE_PATH = "build/gametest/results/recipe-test.xml";

    /** Suite name shown in the rolled-up GHA summary. */
    private static final String SUITE_NAME = "recipe_test_dynamic";

    /** Class name placed on every {@code <testcase>}. Most reporters group by classname; using
     *  one bucket keeps the dynamic suite collapsed neatly. */
    private static final String TESTCASE_CLASS = "dev.recipetest.gametest.DynamicGameTestGenerator";

    private static @Nullable JunitXmlReporter installedInstance;

    /** Side channel keyed by GameTest name; populated by {@link DynamicGameTestGenerator} after
     *  the harness publishes a {@link TestResult}. {@link ConcurrentHashMap} because the GameTest
     *  framework drives tests on the server thread but JVM-level shutdown might race with the
     *  finish call. */
    private static final Map<String, TestResult> RESULTS = new ConcurrentHashMap<>();

    private final TestReporter delegate;
    private final Path outputPath;
    private final List<Row> rows = new ArrayList<>();
    private final Instant suiteStart = Instant.now();

    JunitXmlReporter(TestReporter delegate, Path outputPath) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath");
    }

    /**
     * Install the reporter, replacing the current {@link GlobalTestReporter} delegate. Idempotent
     * — calling twice keeps the first install and logs a warning. Invoked from
     * {@code RecipeTestMod.onRegisterGameTests}.
     */
    public static synchronized void install() {
        if (installedInstance != null) {
            LOGGER.debug("recipe_test: JunitXmlReporter already installed, skipping");
            return;
        }
        TestReporter previous = currentDelegate();
        Path output = Path.of(OUTPUT_RELATIVE_PATH);
        JunitXmlReporter reporter = new JunitXmlReporter(previous, output);
        GlobalTestReporter.replaceWith(reporter);
        installedInstance = reporter;
        LOGGER.info("recipe_test: installed JunitXmlReporter → {}", output);
    }

    /** Posts the live {@link TestResult} for {@code testName}. {@link DynamicGameTestGenerator}
     *  calls this before throwing on fail (or after asserting success) so XML rows can carry the
     *  full harness payload. */
    public static void recordResult(String testName, TestResult result) {
        RESULTS.put(testName, result);
    }

    /** Test seam — rebuilds an empty reporter so unit tests can exercise {@link #finish()}
     *  without poking the global. */
    static JunitXmlReporter forTesting(Path outputPath) {
        return new JunitXmlReporter(new NoOpReporter(), outputPath);
    }

    /** Test seam — clears recorded results between unit-test runs. */
    static void clearResultsForTesting() {
        RESULTS.clear();
    }

    @Override
    public void onTestFailed(GameTestInfo testInfo) {
        String name = testInfo.getTestName();
        Throwable error = testInfo.getError();
        rows.add(Row.failure(
                name, runtimeSeconds(testInfo), describeError(error), Optional.ofNullable(RESULTS.get(name))));
        delegate.onTestFailed(testInfo);
    }

    @Override
    public void onTestSuccess(GameTestInfo testInfo) {
        String name = testInfo.getTestName();
        rows.add(Row.success(name, runtimeSeconds(testInfo), Optional.ofNullable(RESULTS.get(name))));
        delegate.onTestSuccess(testInfo);
    }

    @Override
    public void finish() {
        try {
            writeReport(outputPath, rows, suiteStart, Instant.now());
        } catch (IOException e) {
            LOGGER.error("recipe_test: failed to write JUnit XML to {}: {}", outputPath, e.toString());
        }
        delegate.finish();
    }

    // ---- XML serialisation ----

    static void writeReport(Path output, List<Row> rows, Instant start, Instant end) throws IOException {
        Files.createDirectories(output.getParent() != null ? output.getParent() : Path.of("."));
        StringBuilder sb = new StringBuilder();
        long failures = rows.stream().filter(Row::isFailure).count();
        double totalSeconds = Math.max(0.0, (end.toEpochMilli() - start.toEpochMilli()) / 1000.0);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"")
                .append(escape(SUITE_NAME))
                .append("\"")
                .append(" tests=\"")
                .append(rows.size())
                .append("\"")
                .append(" failures=\"")
                .append(failures)
                .append("\"")
                .append(" errors=\"0\"")
                .append(" skipped=\"0\"")
                .append(" timestamp=\"")
                .append(escape(start.toString()))
                .append("\"")
                .append(" time=\"")
                .append(formatSeconds(totalSeconds))
                .append("\">\n");
        for (Row row : rows) {
            appendTestcase(sb, row);
        }
        sb.append("</testsuite>\n");
        Files.writeString(output, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void appendTestcase(StringBuilder sb, Row row) {
        sb.append("  <testcase classname=\"")
                .append(escape(TESTCASE_CLASS))
                .append("\" name=\"")
                .append(escape(row.name()))
                .append("\" time=\"")
                .append(formatSeconds(row.runtimeSeconds()))
                .append("\">\n");
        if (row.isFailure()) {
            sb.append("    <failure message=\"")
                    .append(escape(truncateAttribute(row.failureMessage())))
                    .append("\" type=\"")
                    .append(escape("dev.recipetest.gametest.RecipeTestFailure"))
                    .append("\">")
                    .append(escape(row.failureMessage()))
                    .append("</failure>\n");
        }
        String systemOut = renderSystemOut(row);
        if (!systemOut.isEmpty()) {
            sb.append("    <system-out>").append(escape(systemOut)).append("</system-out>\n");
        }
        sb.append("  </testcase>\n");
    }

    private static String renderSystemOut(Row row) {
        Optional<TestResult> opt = row.harnessResult();
        if (opt.isEmpty()) {
            return "";
        }
        TestResult r = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("recipeId=").append(r.recipeId());
        sb.append("\nrecipeType=").append(r.recipeType());
        sb.append("\nspecSource=").append(r.specSource());
        sb.append("\nstatus=").append(r.status());
        sb.append("\nticksElapsed=").append(r.ticksElapsed());
        if (!r.diagnostics().warnings().isEmpty()) {
            sb.append("\nwarnings=").append(r.diagnostics().warnings());
        }
        if (r.status() == RunStatus.FAIL && r.diff().isPresent()) {
            sb.append("\nresultJson=").append(safeToJson(r));
        }
        return sb.toString();
    }

    private static String safeToJson(TestResult r) {
        try {
            return ReportFormatter.toJson(r);
        } catch (RuntimeException e) {
            return "<encode-error: " + e.getClass().getSimpleName() + ">";
        }
    }

    private static String describeError(@Nullable Throwable error) {
        if (error == null) {
            return "(no error captured)";
        }
        String msg = error.getMessage();
        return error.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }

    private static String truncateAttribute(String s) {
        // XML attributes accept arbitrary length, but JUnit reporters often truncate at ~256.
        // Keep our message in that ballpark so summary tables stay readable.
        if (s.length() <= 200) {
            return s;
        }
        return s.substring(0, 197) + "...";
    }

    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    // Strip control chars (0x00-0x1F) except tab/newline/CR — XML rejects them.
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        sb.append('?');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String formatSeconds(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.0, seconds));
    }

    private static double runtimeSeconds(GameTestInfo info) {
        // GameTestInfo.getRunTime returns wall-clock millis; tickCount also tracks ticks. Use
        // millis if available; fall back to tick conversion.
        long millis = info.getRunTime();
        if (millis > 0) {
            return millis / 1000.0;
        }
        // Fallback: attempt to derive from ticks via reflection — but the simple path is just 0.
        return 0.0;
    }

    private static TestReporter currentDelegate() {
        // GlobalTestReporter doesn't expose its current delegate, so we substitute a fresh
        // LogTestReporter here. That matches the default install state — LogTestReporter is a
        // public class with a no-arg constructor.
        return new net.minecraft.gametest.framework.LogTestReporter();
    }

    /** In-memory row carrying everything {@link #writeReport} needs about a single test outcome. */
    record Row(
            String name,
            double runtimeSeconds,
            boolean isFailure,
            String failureMessage,
            Optional<TestResult> harnessResult) {
        static Row success(String name, double runtimeSeconds, Optional<TestResult> harnessResult) {
            return new Row(name, runtimeSeconds, false, "", harnessResult);
        }

        static Row failure(String name, double runtimeSeconds, String message, Optional<TestResult> harnessResult) {
            return new Row(name, runtimeSeconds, true, message, harnessResult);
        }
    }

    /** No-op reporter used by {@link #forTesting}; keeps the delegate non-null without touching
     *  Minecraft's {@code LogTestReporter} (which logs noisily to stderr in unit tests). */
    private static final class NoOpReporter implements TestReporter {
        @Override
        public void onTestFailed(GameTestInfo testInfo) {}

        @Override
        public void onTestSuccess(GameTestInfo testInfo) {}
    }
}

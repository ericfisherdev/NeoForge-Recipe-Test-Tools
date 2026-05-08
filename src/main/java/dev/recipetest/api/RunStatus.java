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
package dev.recipetest.api;

/**
 * Final outcome of a single recipe-test run.
 *
 * <ul>
 *   <li>{@link #PASS} — actual output matched expected within {@link ValidationPolicy} tolerances.
 *   <li>{@link #FAIL} — actual output diverged from expected; {@code TestResult.diff} populated.
 *   <li>{@link #TIMEOUT} — tick budget exhausted before output appeared.
 *   <li>{@link #ERROR} — placement, capability resolution, or injection failed; kit-level
 *       problem rather than a recipe disagreement.
 *   <li>{@link #SKIPPED} — runner deliberately did not execute (e.g. no recipe matched, optional
 *       mod absent, distribution mode pending sample collection).
 *   <li>{@link #CANCELLED} — bulk-runner cancellation aborted the run before it completed; the
 *       result records whatever partial actual snapshot was last observed.
 * </ul>
 */
public enum RunStatus {
    PASS,
    FAIL,
    TIMEOUT,
    ERROR,
    SKIPPED,
    CANCELLED
}

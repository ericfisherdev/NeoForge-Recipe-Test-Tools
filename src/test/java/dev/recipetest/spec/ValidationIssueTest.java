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
package dev.recipetest.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationIssueTest {

    @Test
    @DisplayName("error factory produces ERROR severity")
    void errorFactory() {
        ValidationIssue issue = ValidationIssue.error("/x", "msg", "hint");
        assertEquals(ValidationIssue.Severity.ERROR, issue.severity());
        assertEquals("/x", issue.jsonPath());
        assertEquals("msg", issue.message());
        assertEquals("hint", issue.fixHint());
    }

    @Test
    @DisplayName("warn factory produces WARN severity")
    void warnFactory() {
        ValidationIssue issue = ValidationIssue.warn("/y", "msg", "hint");
        assertEquals(ValidationIssue.Severity.WARN, issue.severity());
    }

    @Test
    @DisplayName("empty jsonPath is allowed (root-level issue)")
    void emptyJsonPathOk() {
        ValidationIssue issue = ValidationIssue.error("", "msg", "hint");
        assertEquals("", issue.jsonPath());
    }

    @Test
    @DisplayName("non-pointer jsonPath is rejected")
    void nonPointerJsonPathRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValidationIssue.error("inputs.items", "msg", "hint"));
    }

    @Test
    @DisplayName("RFC 6901 escapes ~0 and ~1 are accepted")
    void rfc6901EscapesAccepted() {
        // ~0 escapes ~, ~1 escapes /
        ValidationIssue a = ValidationIssue.error("/foo~0bar", "m", "h");
        ValidationIssue b = ValidationIssue.error("/foo~1bar", "m", "h");
        assertEquals("/foo~0bar", a.jsonPath());
        assertEquals("/foo~1bar", b.jsonPath());
    }

    @Test
    @DisplayName("trailing bare '~' is rejected")
    void trailingTildeRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValidationIssue.error("/foo~", "m", "h"));
    }

    @Test
    @DisplayName("'~' followed by something other than '0' or '1' is rejected")
    void invalidTildeEscapeRejected() {
        assertThrows(IllegalArgumentException.class, () -> ValidationIssue.error("/foo~2bar", "m", "h"));
        assertThrows(IllegalArgumentException.class, () -> ValidationIssue.error("/foo~xbar", "m", "h"));
        assertThrows(IllegalArgumentException.class, () -> ValidationIssue.error("/foo~~bar", "m", "h"));
    }

    @Test
    @DisplayName("any null component is rejected")
    void nullComponentsRejected() {
        assertThrows(NullPointerException.class, () -> new ValidationIssue(null, "/x", "m", "h"));
        assertThrows(
                NullPointerException.class, () -> new ValidationIssue(ValidationIssue.Severity.ERROR, null, "m", "h"));
        assertThrows(
                NullPointerException.class, () -> new ValidationIssue(ValidationIssue.Severity.ERROR, "/x", null, "h"));
        assertThrows(
                NullPointerException.class, () -> new ValidationIssue(ValidationIssue.Severity.ERROR, "/x", "m", null));
    }
}

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

import java.util.Objects;

/**
 * A single problem the {@link SpecValidator} found in a {@code MachineSpec}.
 *
 * @param severity {@link Severity#WARN} (spec loads, behavior may be off) or
 *     {@link Severity#ERROR} (spec is rejected from the registry)
 * @param jsonPath JSON Pointer (RFC 6901) into the offending field, e.g.
 *     {@code "/inputs/items/slots"}
 * @param message human-readable description of what is wrong
 * @param fixHint one-line actionable hint, e.g. {@code "expected 9 slots for shaped3x3 layout, got 3"}
 */
public record ValidationIssue(Severity severity, String jsonPath, String message, String fixHint) {

    public ValidationIssue {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(jsonPath, "jsonPath must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(fixHint, "fixHint must not be null");
        validateJsonPointer(jsonPath);
    }

    /**
     * Validate {@code jsonPath} as an RFC 6901 JSON Pointer:
     *
     * <ul>
     *   <li>empty string is valid (root reference)
     *   <li>otherwise must start with {@code /}
     *   <li>{@code ~} is an escape character; the only legal sequences are {@code ~0} (escaped
     *       {@code ~}) and {@code ~1} (escaped {@code /}). A trailing bare {@code ~} or
     *       {@code ~} followed by anything else is rejected.
     * </ul>
     */
    private static void validateJsonPointer(String jsonPath) {
        if (jsonPath.isEmpty()) {
            return;
        }
        if (jsonPath.charAt(0) != '/') {
            throw new IllegalArgumentException(
                    "jsonPath must be empty or a JSON Pointer (start with '/'), got: " + jsonPath);
        }
        int len = jsonPath.length();
        for (int i = 0; i < len; i++) {
            if (jsonPath.charAt(i) != '~') {
                continue;
            }
            if (i + 1 >= len) {
                throw new IllegalArgumentException(
                        "jsonPath has trailing '~' which must be followed by '0' or '1': " + jsonPath);
            }
            char next = jsonPath.charAt(i + 1);
            if (next != '0' && next != '1') {
                throw new IllegalArgumentException(
                        "jsonPath '~' must be followed by '0' or '1' per RFC 6901, got '~" + next + "': " + jsonPath);
            }
        }
    }

    public enum Severity {
        WARN,
        ERROR
    }

    public static ValidationIssue error(String jsonPath, String message, String fixHint) {
        return new ValidationIssue(Severity.ERROR, jsonPath, message, fixHint);
    }

    public static ValidationIssue warn(String jsonPath, String message, String fixHint) {
        return new ValidationIssue(Severity.WARN, jsonPath, message, fixHint);
    }
}

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

import java.util.List;
import java.util.Objects;

/**
 * Populated {@code TestResult.diff} field — the structured list of mismatches between actual and
 * expected output. Always contains at least one entry; an empty diff is represented by
 * {@code Optional.empty()} on {@link TestResult#diff()}.
 */
public record DiffPayload(List<DiffEntry> mismatches) {

    public DiffPayload {
        Objects.requireNonNull(mismatches, "DiffPayload.mismatches must not be null");
        if (mismatches.isEmpty()) {
            throw new IllegalArgumentException(
                    "DiffPayload.mismatches must not be empty; use Optional.empty() for matching outputs");
        }
        mismatches = List.copyOf(mismatches);
    }
}

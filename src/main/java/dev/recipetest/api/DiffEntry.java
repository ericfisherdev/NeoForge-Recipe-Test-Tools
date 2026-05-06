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

import java.util.Objects;

/**
 * One row of a {@link DiffPayload}: where actual diverged from expected, with both sides rendered
 * to short strings suitable for chat / JSON output.
 *
 * @param path JSON-pointer-style location (e.g. {@code "/items/0"} or {@code "/fluids/1"})
 * @param expected pre-rendered expected value (empty string for "absent")
 * @param actual pre-rendered actual value (empty string for "absent")
 * @param reason short human-readable cause (e.g. {@code "count mismatch"}, {@code "id mismatch"})
 */
public record DiffEntry(String path, String expected, String actual, String reason) {

    public DiffEntry {
        Objects.requireNonNull(path, "DiffEntry.path must not be null");
        Objects.requireNonNull(expected, "DiffEntry.expected must not be null");
        Objects.requireNonNull(actual, "DiffEntry.actual must not be null");
        Objects.requireNonNull(reason, "DiffEntry.reason must not be null");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("DiffEntry.path must not be empty");
        }
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("DiffEntry.reason must not be empty");
        }
    }
}

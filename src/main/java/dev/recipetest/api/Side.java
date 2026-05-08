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
 * Where on a block a capability is queried from. Mirrors the six cardinal directions plus two
 * synthetic values used by the kit:
 *
 * <ul>
 *   <li>{@link #ANY} — query every direction in turn until one returns a capability;
 *   <li>{@link #INTERNAL} — query the block's internal (null-side) capability.
 * </ul>
 */
public enum Side {
    TOP,
    BOTTOM,
    NORTH,
    SOUTH,
    EAST,
    WEST,
    ANY,
    INTERNAL;
}

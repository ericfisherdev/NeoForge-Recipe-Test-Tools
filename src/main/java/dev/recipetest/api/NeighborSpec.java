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

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A block to place at a given offset relative to the machine origin during harness setup.
 *
 * @param offset relative position from the machine origin (machine sits at {@code [0,0,0]})
 * @param block the block to place
 */
public record NeighborSpec(BlockPos offset, ResourceLocation block) {}

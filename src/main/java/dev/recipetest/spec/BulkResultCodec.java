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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.recipetest.api.BulkProgress;
import dev.recipetest.api.BulkResult;
import dev.recipetest.api.RunStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Codecs for {@link BulkProgress} (NDJSON streamed during a run) and {@link BulkResult} (final
 * aggregate). Status counts serialise as a string-keyed map so JSON consumers don't need to know
 * about the {@link RunStatus} enum's wire format.
 */
public final class BulkResultCodec {

    private BulkResultCodec() {}

    private static final Codec<Map<RunStatus, Integer>> STATUS_COUNTS_CODEC =
            Codec.unboundedMap(TestResultCodec.STATUS_CODEC, Codec.INT);

    public static final Codec<BulkProgress> PROGRESS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("runId").forGetter(BulkProgress::runId),
                    Codec.STRING.fieldOf("recipeType").forGetter(BulkProgress::recipeTypeLabel),
                    Codec.INT.fieldOf("total").forGetter(BulkProgress::total),
                    Codec.INT.fieldOf("completed").forGetter(BulkProgress::completed),
                    STATUS_COUNTS_CODEC
                            .optionalFieldOf("countsByStatus", Map.of())
                            .forGetter(BulkProgress::countsByStatus),
                    ResourceLocation.CODEC
                            .optionalFieldOf("inFlightRecipeId")
                            .forGetter(BulkProgress::inFlightRecipeId),
                    Codec.INT.optionalFieldOf("etaTicks", 0).forGetter(BulkProgress::etaTicks))
            .apply(instance, BulkProgress::new));

    public static final Codec<BulkResult> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("runId").forGetter(BulkResult::runId),
                    Codec.STRING.fieldOf("recipeType").forGetter(BulkResult::recipeTypeLabel),
                    STATUS_COUNTS_CODEC
                            .optionalFieldOf("countsByStatus", Map.of())
                            .forGetter(BulkResult::countsByStatus),
                    Codec.LONG.fieldOf("wallClockMs").forGetter(BulkResult::wallClockMs),
                    Codec.INT.fieldOf("totalEngineTicks").forGetter(BulkResult::totalEngineTicks),
                    Codec.LONG.fieldOf("peakMsptBudgetUsedMs").forGetter(BulkResult::peakMsptBudgetUsedMs),
                    Codec.BOOL.optionalFieldOf("cancelled", false).forGetter(BulkResult::cancelled),
                    TestResultCodec.CODEC
                            .listOf()
                            .optionalFieldOf("results", List.of())
                            .forGetter(BulkResult::results))
            .apply(instance, BulkResult::new));
}

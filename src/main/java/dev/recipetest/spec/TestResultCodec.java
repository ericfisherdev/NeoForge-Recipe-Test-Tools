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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.recipetest.api.Diagnostics;
import dev.recipetest.api.DiffEntry;
import dev.recipetest.api.DiffPayload;
import dev.recipetest.api.FluidSnapshot;
import dev.recipetest.api.IoSnapshot;
import dev.recipetest.api.ItemSnapshot;
import dev.recipetest.api.RunStatus;
import dev.recipetest.api.TestResult;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * Codecs for {@link TestResult} and its nested record types. Kept in {@code spec/} so the
 * {@code api/} package stays free of DataFixerUpper.
 */
public final class TestResultCodec {

    private TestResultCodec() {}

    public static final Codec<RunStatus> STATUS_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(RunStatus.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(() ->
                            "Unknown status '" + name + "' (expected one of: PASS, FAIL, TIMEOUT, ERROR, SKIPPED)");
                }
            },
            status -> DataResult.success(status.name()));

    public static final Codec<ItemSnapshot> ITEM_SNAPSHOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(ItemSnapshot::id),
                    Codec.INT.fieldOf("count").forGetter(ItemSnapshot::count),
                    Codec.STRING.optionalFieldOf("nbt").forGetter(ItemSnapshot::nbt))
            .apply(instance, ItemSnapshot::new));

    public static final Codec<FluidSnapshot> FLUID_SNAPSHOT_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("id").forGetter(FluidSnapshot::id),
                            Codec.INT.fieldOf("amount").forGetter(FluidSnapshot::amount),
                            Codec.STRING.optionalFieldOf("nbt").forGetter(FluidSnapshot::nbt))
                    .apply(instance, FluidSnapshot::new));

    public static final Codec<IoSnapshot> IO_SNAPSHOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ITEM_SNAPSHOT_CODEC
                            .listOf()
                            .optionalFieldOf("items", List.of())
                            .forGetter(IoSnapshot::items),
                    FLUID_SNAPSHOT_CODEC
                            .listOf()
                            .optionalFieldOf("fluids", List.of())
                            .forGetter(IoSnapshot::fluids))
            .apply(instance, IoSnapshot::new));

    public static final Codec<DiffEntry> DIFF_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("path").forGetter(DiffEntry::path),
                    Codec.STRING.fieldOf("expected").forGetter(DiffEntry::expected),
                    Codec.STRING.fieldOf("actual").forGetter(DiffEntry::actual),
                    Codec.STRING.fieldOf("reason").forGetter(DiffEntry::reason))
            .apply(instance, DiffEntry::new));

    public static final Codec<DiffPayload> DIFF_PAYLOAD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    DIFF_ENTRY_CODEC.listOf().fieldOf("mismatches").forGetter(DiffPayload::mismatches))
            .apply(instance, DiffPayload::new));

    public static final Codec<Diagnostics> DIAGNOSTICS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    FLUID_SNAPSHOT_CODEC
                            .listOf()
                            .optionalFieldOf("fluidConsumed", List.of())
                            .forGetter(Diagnostics::fluidConsumed),
                    Codec.LONG.optionalFieldOf("energyConsumed", 0L).forGetter(Diagnostics::energyConsumed),
                    Codec.STRING.listOf().optionalFieldOf("warnings", List.of()).forGetter(Diagnostics::warnings),
                    Codec.STRING.listOf().optionalFieldOf("logs", List.of()).forGetter(Diagnostics::logs))
            .apply(instance, Diagnostics::new));

    public static final Codec<TestResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("recipeId").forGetter(TestResult::recipeId),
                    ResourceLocation.CODEC.fieldOf("recipeType").forGetter(TestResult::recipeType),
                    Codec.STRING.fieldOf("specSource").forGetter(TestResult::specSource),
                    STATUS_CODEC.fieldOf("status").forGetter(TestResult::status),
                    Codec.INT.fieldOf("ticksElapsed").forGetter(TestResult::ticksElapsed),
                    IO_SNAPSHOT_CODEC.fieldOf("expected").forGetter(TestResult::expected),
                    IO_SNAPSHOT_CODEC.fieldOf("actual").forGetter(TestResult::actual),
                    DIFF_PAYLOAD_CODEC.optionalFieldOf("diff").forGetter(TestResult::diff),
                    DIAGNOSTICS_CODEC.fieldOf("diagnostics").forGetter(TestResult::diagnostics))
            .apply(instance, TestResult::new));
}

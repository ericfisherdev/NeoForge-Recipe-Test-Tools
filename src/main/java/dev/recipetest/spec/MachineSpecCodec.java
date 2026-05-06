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

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.EnergySpec;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.InputBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.Layout;
import dev.recipetest.api.LifecycleHooks;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.NeighborSpec;
import dev.recipetest.api.OutputBinding;
import dev.recipetest.api.Side;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * All codecs for {@link MachineSpec} and its nested record types. Centralized here so the
 * {@code dev.recipetest.api} package stays free of serialization concerns; L2 extensions can
 * depend on the records without pulling in DataFixerUpper.
 */
public final class MachineSpecCodec {

    private MachineSpecCodec() {}

    public static final Codec<Side> SIDE_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(Side.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(() -> "Unknown side '" + name + "' (expected one of: TOP, BOTTOM, NORTH, "
                            + "SOUTH, EAST, WEST, ANY, INTERNAL)");
                }
            },
            side -> DataResult.success(side.name()));

    public static final Codec<Layout> LAYOUT_CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(Layout.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(
                            () -> "Unknown layout '" + name + "' (expected: shapeless | shaped3x3 | ordered)");
                }
            },
            layout -> DataResult.success(layout.name().toLowerCase(Locale.ROOT)));

    public static final Codec<TickBudget> TICK_BUDGET_CODEC = Codec.either(Codec.STRING, Codec.INT)
            .flatXmap(
                    either -> either.map(
                            str -> "auto".equalsIgnoreCase(str)
                                    ? DataResult.<TickBudget>success(TickBudget.AUTO)
                                    : DataResult.<TickBudget>error(
                                            () -> "tickBudget must be \"auto\" or an integer, got \"" + str + "\""),
                            i -> {
                                if (i <= 0) {
                                    return DataResult.<TickBudget>error(() -> "tickBudget must be > 0, got " + i);
                                }
                                return DataResult.<TickBudget>success(new TickBudget.Fixed(i));
                            }),
                    budget -> switch (budget) {
                        case TickBudget.Auto a -> DataResult.success(Either.left("auto"));
                        case TickBudget.Fixed f -> DataResult.success(Either.right(f.ticks()));
                    });

    public static final Codec<NeighborSpec> NEIGHBOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .listOf()
                            .fieldOf("offset")
                            .flatXmap(
                                    list -> list.size() == 3
                                            ? DataResult.success(new BlockPos(list.get(0), list.get(1), list.get(2)))
                                            : DataResult.<BlockPos>error(() ->
                                                    "neighbor offset must have exactly 3 elements, got " + list.size()),
                                    pos -> DataResult.success(List.of(pos.getX(), pos.getY(), pos.getZ())))
                            .forGetter(NeighborSpec::offset),
                    ResourceLocation.CODEC.fieldOf("block").forGetter(NeighborSpec::block))
            .apply(instance, NeighborSpec::new));

    public static final Codec<ItemBinding> ITEM_BINDING_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("capability").forGetter(ItemBinding::capability),
                    SIDE_CODEC.optionalFieldOf("side", Side.INTERNAL).forGetter(ItemBinding::side),
                    Codec.INT.listOf().fieldOf("slots").forGetter(ItemBinding::slots),
                    LAYOUT_CODEC.optionalFieldOf("layout").forGetter(ItemBinding::layout),
                    Codec.INT.optionalFieldOf("primary").forGetter(ItemBinding::primary))
            .apply(instance, ItemBinding::new));

    /**
     * Custom MapCodec for {@link FluidBinding}: the JSON form accepts <em>either</em>
     * {@code "tank": <int>} or {@code "tanks": [int...]}, both normalized to a non-empty
     * {@code List<Integer>}. Errors when both fields are present or neither is.
     */
    public static final MapCodec<FluidBinding> FLUID_BINDING_MAP_CODEC = new MapCodec<>() {
        private static final String F_CAPABILITY = "capability";
        private static final String F_SIDE = "side";
        private static final String F_TANK = "tank";
        private static final String F_TANKS = "tanks";

        @Override
        public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) {
            return java.util.stream.Stream.of(
                    ops.createString(F_CAPABILITY),
                    ops.createString(F_SIDE),
                    ops.createString(F_TANK),
                    ops.createString(F_TANKS));
        }

        @Override
        public <T> DataResult<FluidBinding> decode(
                com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.MapLike<T> input) {
            DataResult<String> capabilityResult = parseRequired(ops, input, F_CAPABILITY, Codec.STRING);
            DataResult<Side> sideResult = parseOptional(ops, input, F_SIDE, SIDE_CODEC, Side.INTERNAL);
            T tankNode = input.get(F_TANK);
            T tanksNode = input.get(F_TANKS);
            DataResult<List<Integer>> tanksResult;
            if (tankNode != null && tanksNode != null) {
                tanksResult = DataResult.error(() -> "FluidBinding: specify either \"tank\" or \"tanks\", not both");
            } else if (tankNode != null) {
                tanksResult = Codec.INT.parse(ops, tankNode).map(List::of);
            } else if (tanksNode != null) {
                tanksResult = Codec.INT.listOf().parse(ops, tanksNode);
            } else {
                tanksResult = DataResult.error(() -> "FluidBinding requires either \"tank\" or \"tanks\"");
            }
            return capabilityResult.flatMap(
                    cap -> sideResult.flatMap(side -> tanksResult.map(tanks -> new FluidBinding(cap, side, tanks))));
        }

        @Override
        public <T> com.mojang.serialization.RecordBuilder<T> encode(
                FluidBinding input,
                com.mojang.serialization.DynamicOps<T> ops,
                com.mojang.serialization.RecordBuilder<T> prefix) {
            prefix.add(F_CAPABILITY, ops.createString(input.capability()));
            prefix.add(F_SIDE, SIDE_CODEC.encodeStart(ops, input.side()));
            if (input.tanks().size() == 1) {
                prefix.add(F_TANK, ops.createInt(input.tanks().get(0)));
            } else {
                prefix.add(F_TANKS, Codec.INT.listOf().encodeStart(ops, input.tanks()));
            }
            return prefix;
        }
    };

    public static final Codec<FluidBinding> FLUID_BINDING_CODEC = FLUID_BINDING_MAP_CODEC.codec();

    public static final Codec<CustomBinding> CUSTOM_BINDING_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("kind").forGetter(CustomBinding::kind),
                            Codec.STRING.fieldOf("ref").forGetter(CustomBinding::ref))
                    .apply(instance, CustomBinding::new));

    public static final Codec<EnergySpec> ENERGY_SPEC_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("capability").forGetter(EnergySpec::capability),
                    SIDE_CODEC.optionalFieldOf("side", Side.ANY).forGetter(EnergySpec::side),
                    Codec.LONG.optionalFieldOf("preFill", 0L).forGetter(EnergySpec::preFill),
                    Codec.BOOL.optionalFieldOf("trackConsumption", false).forGetter(EnergySpec::trackConsumption))
            .apply(instance, EnergySpec::new));

    private static final Codec<ValidationPolicy.Mode> VALIDATION_MODE_CODEC =
            enumCodec(ValidationPolicy.Mode.class, "validation.mode", "exact | distribution | subset");

    private static final Codec<ValidationPolicy.NbtCompare> NBT_COMPARE_CODEC =
            enumCodec(ValidationPolicy.NbtCompare.class, "validation.nbtCompare", "structural | ignore | exact");

    private static final Codec<ValidationPolicy.ItemTolerance> ITEM_TOLERANCE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            Codec.INT.optionalFieldOf("count", 0).forGetter(ValidationPolicy.ItemTolerance::count))
                    .apply(instance, ValidationPolicy.ItemTolerance::new));

    private static final Codec<ValidationPolicy.FluidTolerance> FLUID_TOLERANCE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            Codec.INT.optionalFieldOf("amount", 0).forGetter(ValidationPolicy.FluidTolerance::amount))
                    .apply(instance, ValidationPolicy.FluidTolerance::new));

    public static final Codec<ValidationPolicy> VALIDATION_POLICY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            VALIDATION_MODE_CODEC
                                    .optionalFieldOf("mode", ValidationPolicy.Mode.EXACT)
                                    .forGetter(ValidationPolicy::mode),
                            ITEM_TOLERANCE_CODEC
                                    .optionalFieldOf("itemTolerance", ValidationPolicy.ItemTolerance.EXACT)
                                    .forGetter(ValidationPolicy::itemTolerance),
                            FLUID_TOLERANCE_CODEC
                                    .optionalFieldOf("fluidTolerance", ValidationPolicy.FluidTolerance.EXACT)
                                    .forGetter(ValidationPolicy::fluidTolerance),
                            NBT_COMPARE_CODEC
                                    .optionalFieldOf("nbtCompare", ValidationPolicy.NbtCompare.STRUCTURAL)
                                    .forGetter(ValidationPolicy::nbtCompare),
                            Codec.INT.optionalFieldOf("samples", 1).forGetter(ValidationPolicy::samples),
                            Codec.DOUBLE
                                    .optionalFieldOf("distributionTolerance", 0.05)
                                    .forGetter(ValidationPolicy::distributionTolerance))
                    .apply(instance, ValidationPolicy::new));

    public static final Codec<LifecycleHooks> LIFECYCLE_HOOKS_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            Codec.STRING
                                    .listOf()
                                    .optionalFieldOf("preTickCommands", List.of())
                                    .forGetter(LifecycleHooks::preTickCommands),
                            Codec.STRING
                                    .listOf()
                                    .optionalFieldOf("postRunCommands", List.of())
                                    .forGetter(LifecycleHooks::postRunCommands),
                            Codec.INT.optionalFieldOf("warmupTicks", 0).forGetter(LifecycleHooks::warmupTicks))
                    .apply(instance, LifecycleHooks::new));

    public static final Codec<InputBinding> INPUT_BINDING_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ITEM_BINDING_CODEC.optionalFieldOf("items").forGetter(InputBinding::items),
                    FLUID_BINDING_CODEC.optionalFieldOf("fluids").forGetter(InputBinding::fluids),
                    CUSTOM_BINDING_CODEC
                            .listOf()
                            .optionalFieldOf("custom", List.of())
                            .forGetter(InputBinding::custom))
            .apply(instance, InputBinding::new));

    public static final Codec<OutputBinding> OUTPUT_BINDING_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            ITEM_BINDING_CODEC.optionalFieldOf("items").forGetter(OutputBinding::items),
                            FLUID_BINDING_CODEC.optionalFieldOf("fluids").forGetter(OutputBinding::fluids),
                            CUSTOM_BINDING_CODEC
                                    .listOf()
                                    .optionalFieldOf("custom", List.of())
                                    .forGetter(OutputBinding::custom))
                    .apply(instance, OutputBinding::new));

    public static final Codec<MachineSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("version").forGetter(MachineSpec::version),
                    ResourceLocation.CODEC.fieldOf("recipeType").forGetter(MachineSpec::recipeType),
                    ResourceLocation.CODEC.fieldOf("block").forGetter(MachineSpec::block),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("blockState")
                            .forGetter(MachineSpec::blockState),
                    NEIGHBOR_CODEC
                            .listOf()
                            .optionalFieldOf("neighbors", List.of())
                            .forGetter(MachineSpec::neighbors),
                    INPUT_BINDING_CODEC.fieldOf("inputs").forGetter(MachineSpec::inputs),
                    OUTPUT_BINDING_CODEC.fieldOf("outputs").forGetter(MachineSpec::outputs),
                    // Phase 1: omit "energy" field for no-energy machines. Explicit JSON null is
                    // documented in the spec but not yet supported; will be added in Phase 2.
                    ENERGY_SPEC_CODEC.optionalFieldOf("energy").forGetter(MachineSpec::energy),
                    TICK_BUDGET_CODEC.fieldOf("tickBudget").forGetter(MachineSpec::tickBudget),
                    VALIDATION_POLICY_CODEC
                            .optionalFieldOf("validation", ValidationPolicy.DEFAULT)
                            .forGetter(MachineSpec::validation),
                    LIFECYCLE_HOOKS_CODEC.optionalFieldOf("lifecycle").forGetter(MachineSpec::lifecycle))
            .apply(instance, MachineSpec::new));

    // ---- helpers ----

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, String fieldName, String allowed) {
        return Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        return DataResult.error(
                                () -> "Unknown " + fieldName + " '" + name + "' (expected: " + allowed + ")");
                    }
                },
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }

    private static <T> DataResult<T> parseRequired(
            com.mojang.serialization.DynamicOps<?> ops,
            com.mojang.serialization.MapLike<?> input,
            String field,
            Codec<T> codec) {
        @SuppressWarnings("unchecked")
        com.mojang.serialization.DynamicOps<Object> opsObj = (com.mojang.serialization.DynamicOps<Object>) ops;
        @SuppressWarnings("unchecked")
        com.mojang.serialization.MapLike<Object> inputObj = (com.mojang.serialization.MapLike<Object>) input;
        Object node = inputObj.get(field);
        if (node == null) {
            return DataResult.error(() -> "Missing required field \"" + field + "\"");
        }
        return codec.parse(opsObj, node);
    }

    private static <T> DataResult<T> parseOptional(
            com.mojang.serialization.DynamicOps<?> ops,
            com.mojang.serialization.MapLike<?> input,
            String field,
            Codec<T> codec,
            T fallback) {
        @SuppressWarnings("unchecked")
        com.mojang.serialization.DynamicOps<Object> opsObj = (com.mojang.serialization.DynamicOps<Object>) ops;
        @SuppressWarnings("unchecked")
        com.mojang.serialization.MapLike<Object> inputObj = (com.mojang.serialization.MapLike<Object>) input;
        Object node = inputObj.get(field);
        if (node == null) {
            return DataResult.success(fallback);
        }
        return codec.parse(opsObj, node);
    }
}

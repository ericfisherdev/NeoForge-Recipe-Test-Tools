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
package dev.recipetest.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.recipetest.api.CustomBinding;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.InputBinding;
import dev.recipetest.api.ItemBinding;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.OutputBinding;
import dev.recipetest.api.Side;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListFormatterTest {

    @Test
    @DisplayName("summarizeInputs reports items + fluids + custom counts")
    void inputsSummary() {
        MachineSpec spec = specWithInputs(
                Optional.of(items(0, 1, 2, 3)),
                Optional.of(fluids(0, 1)),
                List.of(new CustomBinding(rl("mek:gas"), "left"), new CustomBinding(rl("mek:gas"), "right")));
        String s = ListFormatter.summarizeInputs(spec);
        assertEquals("items[4 slots] fluids[2 tanks] custom[2]", s);
    }

    @Test
    @DisplayName("summarizeInputs handles items-only spec")
    void inputsItemsOnly() {
        MachineSpec spec = specWithInputs(Optional.of(items(0)), Optional.empty(), List.of());
        assertEquals("items[1 slots]", ListFormatter.summarizeInputs(spec));
    }

    @Test
    @DisplayName("summarizeOutputs handles fluids-only spec")
    void outputsFluidsOnly() {
        MachineSpec spec = specWithOutputs(Optional.empty(), Optional.of(fluids(7)), List.of());
        assertEquals("fluids[1 tanks]", ListFormatter.summarizeOutputs(spec));
    }

    @Test
    @DisplayName("summarizeOutputs returns empty string for fully-empty bindings")
    void outputsEmpty() {
        MachineSpec spec = specWithOutputs(Optional.empty(), Optional.empty(), List.of());
        assertEquals("", ListFormatter.summarizeOutputs(spec));
    }

    @Test
    @DisplayName("summarizeInputs trailing whitespace stripped")
    void inputsTrailingStripped() {
        MachineSpec spec = specWithInputs(Optional.of(items(0)), Optional.empty(), List.of());
        String s = ListFormatter.summarizeInputs(spec);
        assertTrue(s.equals(s.stripTrailing()), "trailing whitespace must be stripped");
    }

    // ---- builders ----

    private static MachineSpec specWithInputs(
            Optional<ItemBinding> items, Optional<FluidBinding> fluids, List<CustomBinding> custom) {
        return new MachineSpec(
                1,
                rl("ex:r"),
                rl("ex:b"),
                Optional.empty(),
                List.of(),
                new InputBinding(items, fluids, custom),
                emptyOutputs(),
                Optional.empty(),
                TickBudget.AUTO,
                ValidationPolicy.DEFAULT,
                Optional.empty());
    }

    private static MachineSpec specWithOutputs(
            Optional<ItemBinding> items, Optional<FluidBinding> fluids, List<CustomBinding> custom) {
        return new MachineSpec(
                1,
                rl("ex:r"),
                rl("ex:b"),
                Optional.empty(),
                List.of(),
                emptyInputs(),
                new OutputBinding(items, fluids, custom),
                Optional.empty(),
                TickBudget.AUTO,
                ValidationPolicy.DEFAULT,
                Optional.empty());
    }

    private static InputBinding emptyInputs() {
        return new InputBinding(Optional.empty(), Optional.empty(), List.<CustomBinding>of());
    }

    private static OutputBinding emptyOutputs() {
        return new OutputBinding(Optional.empty(), Optional.empty(), List.<CustomBinding>of());
    }

    private static ItemBinding items(int... slots) {
        java.util.List<Integer> list = new java.util.ArrayList<>(slots.length);
        for (int s : slots) {
            list.add(s);
        }
        return new ItemBinding("ItemHandler", Side.INTERNAL, list, Optional.empty(), Optional.empty());
    }

    private static FluidBinding fluids(int... tanks) {
        java.util.List<Integer> list = new java.util.ArrayList<>(tanks.length);
        for (int t : tanks) {
            list.add(t);
        }
        return new FluidBinding("FluidHandler", Side.INTERNAL, list);
    }

    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }
}

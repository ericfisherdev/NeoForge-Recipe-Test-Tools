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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.recipetest.api.FluidBinding;
import dev.recipetest.api.MachineSpec;
import dev.recipetest.api.Side;
import dev.recipetest.api.TickBudget;
import dev.recipetest.api.ValidationPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CodecTest {

    @ParameterizedTest(name = "round-trip {0}")
    @ValueSource(
            strings = {
                "fixtures/01_grinder.json",
                "fixtures/02_squeezer.json",
                "fixtures/03_centrifuge.json",
                "fixtures/04_chemical_infuser.json"
            })
    @DisplayName("Worked example codec round-trip is lossless")
    void workedExampleRoundTrip(String fixture) throws IOException {
        JsonElement original = readJson(fixture);

        DataResult<MachineSpec> firstParse = MachineSpecCodec.CODEC.parse(JsonOps.INSTANCE, original);
        assertTrue(
                firstParse.result().isPresent(),
                () -> "first parse failed: "
                        + firstParse.error().map(Object::toString).orElse("?"));
        MachineSpec spec = firstParse.result().orElseThrow();

        DataResult<JsonElement> encoded = MachineSpecCodec.CODEC.encodeStart(JsonOps.INSTANCE, spec);
        assertTrue(
                encoded.result().isPresent(),
                () -> "encode failed: " + encoded.error().map(Object::toString).orElse("?"));

        DataResult<MachineSpec> secondParse =
                MachineSpecCodec.CODEC.parse(JsonOps.INSTANCE, encoded.result().orElseThrow());
        assertTrue(
                secondParse.result().isPresent(),
                () -> "second parse failed: "
                        + secondParse.error().map(Object::toString).orElse("?"));

        assertEquals(spec, secondParse.result().orElseThrow(), "round-trip changed spec");
    }

    @Test
    @DisplayName("tickBudget=\"auto\" decodes to TickBudget.AUTO singleton")
    void tickBudgetAutoString() {
        DataResult<TickBudget> r =
                MachineSpecCodec.TICK_BUDGET_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"auto\""));
        assertEquals(TickBudget.AUTO, r.result().orElseThrow());
    }

    @Test
    @DisplayName("tickBudget=200 decodes to Fixed(200)")
    void tickBudgetIntegerLiteral() {
        DataResult<TickBudget> r =
                MachineSpecCodec.TICK_BUDGET_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("200"));
        assertEquals(new TickBudget.Fixed(200), r.result().orElseThrow());
    }

    @Test
    @DisplayName("tickBudget=0 fails parsing")
    void tickBudgetZeroRejected() {
        DataResult<TickBudget> r =
                MachineSpecCodec.TICK_BUDGET_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("0"));
        assertTrue(r.error().isPresent(), "expected error for tickBudget=0");
    }

    @Test
    @DisplayName("tickBudget=\"slow\" fails parsing")
    void tickBudgetUnknownStringRejected() {
        DataResult<TickBudget> r =
                MachineSpecCodec.TICK_BUDGET_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"slow\""));
        assertTrue(r.error().isPresent(), "expected error for tickBudget=\"slow\"");
    }

    @Test
    @DisplayName("FluidBinding accepts singular \"tank\" form")
    void fluidBindingSingularTank() {
        JsonElement json = JsonParser.parseString("{\"capability\":\"FluidHandler\",\"side\":\"NORTH\",\"tank\":3}");
        FluidBinding binding = MachineSpecCodec.FLUID_BINDING_CODEC
                .parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow();
        assertAll(
                () -> assertEquals("FluidHandler", binding.capability()),
                () -> assertEquals(Side.NORTH, binding.side()),
                () -> assertEquals(3, binding.tank()),
                () -> assertEquals(1, binding.tanks().size()));
    }

    @Test
    @DisplayName("FluidBinding accepts plural \"tanks\" form")
    void fluidBindingPluralTanks() {
        JsonElement json = JsonParser.parseString("{\"capability\":\"FluidHandler\",\"tanks\":[0,1,2]}");
        FluidBinding binding = MachineSpecCodec.FLUID_BINDING_CODEC
                .parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow();
        assertAll(
                () -> assertEquals(Side.INTERNAL, binding.side(), "side defaults to INTERNAL"),
                () -> assertEquals(java.util.List.of(0, 1, 2), binding.tanks()));
    }

    @Test
    @DisplayName("FluidBinding rejects both \"tank\" and \"tanks\" together")
    void fluidBindingBothFormsRejected() {
        JsonElement json = JsonParser.parseString("{\"capability\":\"FluidHandler\",\"tank\":0,\"tanks\":[1,2]}");
        DataResult<FluidBinding> r = MachineSpecCodec.FLUID_BINDING_CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(r.error().isPresent(), "expected error when both tank and tanks present");
    }

    @Test
    @DisplayName("FluidBinding rejects neither \"tank\" nor \"tanks\"")
    void fluidBindingMissingTanksRejected() {
        JsonElement json = JsonParser.parseString("{\"capability\":\"FluidHandler\"}");
        DataResult<FluidBinding> r = MachineSpecCodec.FLUID_BINDING_CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(r.error().isPresent(), "expected error when neither tank nor tanks present");
    }

    @Test
    @DisplayName("Side codec is case-insensitive on input, uppercase on output")
    void sideCaseInsensitive() {
        DataResult<Side> r = MachineSpecCodec.SIDE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"top\""));
        assertEquals(Side.TOP, r.result().orElseThrow());
        JsonElement out = MachineSpecCodec.SIDE_CODEC
                .encodeStart(JsonOps.INSTANCE, Side.TOP)
                .result()
                .orElseThrow();
        assertEquals("\"TOP\"", out.toString());
    }

    @Test
    @DisplayName("Unknown side string fails with descriptive error")
    void sideUnknownRejected() {
        DataResult<Side> r =
                MachineSpecCodec.SIDE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"diagonal\""));
        assertTrue(r.error().isPresent());
        assertTrue(r.error().orElseThrow().message().contains("diagonal"));
    }

    @Test
    @DisplayName("ValidationPolicy defaults populate when validation block omitted")
    void validationPolicyDefaultsApplied() throws IOException {
        MachineSpec spec = MachineSpecCodec.CODEC
                .parse(JsonOps.INSTANCE, readJson("fixtures/01_grinder.json"))
                .result()
                .orElseThrow();
        assertEquals(ValidationPolicy.DEFAULT, spec.validation());
    }

    @Test
    @DisplayName("Centrifuge validation block decodes to distribution mode + 1000 samples")
    void distributionValidationDecoded() throws IOException {
        MachineSpec spec = MachineSpecCodec.CODEC
                .parse(JsonOps.INSTANCE, readJson("fixtures/03_centrifuge.json"))
                .result()
                .orElseThrow();
        assertAll(
                () -> assertEquals(
                        ValidationPolicy.Mode.DISTRIBUTION, spec.validation().mode()),
                () -> assertEquals(1000, spec.validation().samples()),
                () -> assertEquals(0.05, spec.validation().distributionTolerance(), 1e-9));
    }

    @Test
    @DisplayName("Energy field can be omitted (no-energy machines)")
    void energyOmitted() throws IOException {
        MachineSpec spec = MachineSpecCodec.CODEC
                .parse(JsonOps.INSTANCE, readJson("fixtures/02_squeezer.json"))
                .result()
                .orElseThrow();
        assertFalse(spec.energy().isPresent(), "squeezer fixture omits energy");
    }

    @Test
    @DisplayName("Custom binding parses kind + ref pairs in declaration order")
    void customBindingsParsed() throws IOException {
        MachineSpec spec = MachineSpecCodec.CODEC
                .parse(JsonOps.INSTANCE, readJson("fixtures/04_chemical_infuser.json"))
                .result()
                .orElseThrow();
        assertAll(
                () -> assertEquals(2, spec.inputs().custom().size()),
                () -> assertEquals("left_tank", spec.inputs().custom().get(0).ref()),
                () -> assertEquals("right_tank", spec.inputs().custom().get(1).ref()),
                () -> assertEquals(1, spec.outputs().custom().size()),
                () -> assertEquals("output_tank", spec.outputs().custom().get(0).ref()));
    }

    @Test
    @DisplayName("Missing required field surfaces an error")
    void missingRequiredField() {
        JsonElement json = JsonParser.parseString("{\"version\":1,\"recipeType\":\"x:y\"}");
        DataResult<MachineSpec> r = MachineSpecCodec.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(r.error().isPresent(), "expected error when required fields missing");
    }

    private static JsonElement readJson(String resourcePath) throws IOException {
        try (InputStream in = CodecTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                fail("Test fixture not on classpath: " + resourcePath);
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

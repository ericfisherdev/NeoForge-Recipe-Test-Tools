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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CustomHandlerTest {

    @Test
    void acceptedIsSingleton() {
        assertSame(CustomHandler.InjectResult.accepted(), CustomHandler.InjectResult.accepted());
    }

    @Test
    void refusedCarriesReason() {
        CustomHandler.InjectResult r = CustomHandler.InjectResult.refused("tank full");
        var refused = assertInstanceOf(CustomHandler.InjectResult.Refused.class, r);
        assertEquals("tank full", refused.reason());
    }

    @Test
    void refusedRejectsBlankReason() {
        assertThrows(NullPointerException.class, () -> CustomHandler.InjectResult.refused(null));
        assertThrows(IllegalArgumentException.class, () -> CustomHandler.InjectResult.refused(""));
        assertThrows(IllegalArgumentException.class, () -> CustomHandler.InjectResult.refused("   "));
    }

    @Test
    void snapshotEqualityIsStructural() {
        ResourceLocation kind = ResourceLocation.fromNamespaceAndPath("mekanism", "gas");
        CustomHandler.Snapshot a = new CustomHandler.Snapshot(kind, "hydrogen:1000");
        CustomHandler.Snapshot b = new CustomHandler.Snapshot(kind, "hydrogen:1000");
        CustomHandler.Snapshot differentPayload = new CustomHandler.Snapshot(kind, "hydrogen:500");
        CustomHandler.Snapshot differentKind = new CustomHandler.Snapshot(
                ResourceLocation.fromNamespaceAndPath("mekanism", "slurry"), "hydrogen:1000");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, differentPayload, "payload differs → not equal");
        assertNotEquals(a, differentKind, "kind differs → not equal even when payload matches");
    }

    @Test
    void snapshotRejectsNulls() {
        ResourceLocation kind = ResourceLocation.fromNamespaceAndPath("mekanism", "gas");
        assertThrows(NullPointerException.class, () -> new CustomHandler.Snapshot(null, "x"));
        assertThrows(NullPointerException.class, () -> new CustomHandler.Snapshot(kind, null));
    }
}

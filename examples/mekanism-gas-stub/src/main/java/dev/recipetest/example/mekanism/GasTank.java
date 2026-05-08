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
package dev.recipetest.example.mekanism;

import java.util.Objects;

/**
 * Single-slot gas storage. Holds at most {@link #capacity} millibuckets of one gas at a time.
 * The harness's L2 extension wraps an instance of this class in a
 * {@code dev.recipetest.api.CustomHandler} to inject test inputs and read outputs.
 *
 * <p>This is intentionally simpler than any real mod's gas storage — no flow rates, no
 * priorities, no concurrent fills. The point is to demonstrate the SPI shape, not to
 * recreate Mekanism. A real consumer's tank would expose the same {@code inject}/{@code read}
 * surface but back it with their own internals.
 */
public final class GasTank {

    private final int capacity;
    private GasStack contents = GasStack.EMPTY;

    public GasTank(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
    }

    /** Add {@code stack} to this tank, replacing whatever was there if the gas type differs.
     *  Returns the actual amount accepted (capped at {@link #capacity}). */
    public int fill(GasStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return 0;
        }
        if (!contents.isEmpty() && !contents.gas().equals(stack.gas())) {
            // Replacement semantics — simpler than Mekanism's "refuse mismatched gas" but
            // good enough for the example.
            contents = GasStack.EMPTY;
        }
        int accepted = Math.min(stack.amount(), capacity - contents.amount());
        contents = new GasStack(stack.gas(), contents.amount() + accepted);
        return accepted;
    }

    /** Snapshot the current contents — used by the harness's read path. */
    public GasStack snapshot() {
        return contents;
    }

    /** Reset to empty. Tests use this between runs. */
    public void clear() {
        contents = GasStack.EMPTY;
    }

    public int capacity() {
        return capacity;
    }
}

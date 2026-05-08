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
 * Abstraction over non-item, non-fluid, non-energy storage on a machine block entity. Returned
 * by {@link RecipeTestExtension#resolveCustomBinding} for each {@link CustomBinding} declared on
 * a spec. The kit uses it to inject test inputs and read actual outputs without knowing
 * anything about the underlying capability shape (gas, heat, mana, etc.).
 *
 * <p>{@code CustomHandler} is intentionally small. Inputs are encoded as opaque payloads —
 * usually parsed by the extension from the recipe — and outputs come back as
 * {@link Snapshot}s that the differ compares structurally. Extensions own the semantics; the
 * runner just plumbs values in and out.
 *
 * <p><b>Contract.</b> A handler is constructed for one {@code (kind, ref, BlockEntity)} triple
 * and is expected to be used once per run. Implementations should be defensive against the
 * underlying BE going stale (e.g. tear-down fired between resolve and inject) — return
 * {@link InjectResult#refused(String)} rather than throwing on a missing tank.
 */
public interface CustomHandler {

    /**
     * Inject the given payload into whatever storage this handler wraps. The payload format is
     * defined by the extension that produced the handler — it might be a serialised gas stack,
     * a heat amount, etc. The kit passes the payload through transparently.
     *
     * @param payload extension-defined input value
     * @return {@link InjectResult#accepted()} on full success; {@link InjectResult#refused(String)}
     *     with a human-readable reason if the storage couldn't accept the payload
     */
    InjectResult inject(Object payload);

    /**
     * Snapshot the current state of the underlying storage. Returned by the runner after the
     * recipe's tick budget elapses; compared against the extension's expected snapshot via
     * {@link Snapshot#equals(Object)}.
     */
    Snapshot read();

    /** Outcome of an injection attempt. */
    sealed interface InjectResult permits InjectResult.Accepted, InjectResult.Refused {
        static InjectResult accepted() {
            return Accepted.INSTANCE;
        }

        static InjectResult refused(String reason) {
            return new Refused(reason);
        }

        /** Singleton accept marker. */
        final class Accepted implements InjectResult {
            private static final Accepted INSTANCE = new Accepted();

            private Accepted() {}
        }

        /** Wraps a human-readable refusal reason — the runner surfaces this as a warning. */
        record Refused(String reason) implements InjectResult {
            public Refused {
                Objects.requireNonNull(reason, "Refused.reason must not be null");
                if (reason.isBlank()) {
                    throw new IllegalArgumentException("Refused.reason must not be blank");
                }
            }
        }
    }

    /**
     * Opaque, equality-comparable snapshot of custom storage. Extensions choose their own
     * representation; the differ only invokes {@link Object#equals} and {@link Object#hashCode}.
     * Implementations should be value-types (records) so equality is structural.
     *
     * @param kind the same {@link CustomBinding#kind} that produced this handler — kept on the
     *     snapshot so the differ can render meaningful per-kind diff entries
     * @param payload the extension-defined value (e.g. a gas stack, a heat amount)
     */
    record Snapshot(net.minecraft.resources.ResourceLocation kind, Object payload) {
        public Snapshot {
            Objects.requireNonNull(kind, "Snapshot.kind must not be null");
            Objects.requireNonNull(payload, "Snapshot.payload must not be null");
        }
    }
}

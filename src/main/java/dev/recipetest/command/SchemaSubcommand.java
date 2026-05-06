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

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import dev.recipetest.RecipeTestMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * {@code /recipe_test schema}: copy the bundled {@code machine_spec.schema.json} asset out
 * to {@code <world>/recipe_test/schema/machine_spec.schema.json} and chat the path back so
 * spec authors can wire it into their IDE.
 */
final class SchemaSubcommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource path of the bundled schema asset. */
    static final String SCHEMA_RESOURCE = "/data/" + RecipeTestMod.MODID + "/schemas/machine_spec.schema.json";

    /** Where the schema is written under the world directory. */
    private static final String OUTPUT_SUBPATH = "recipe_test/schema/machine_spec.schema.json";

    private static final SimpleCommandExceptionType MISSING_ASSET = new SimpleCommandExceptionType(
            Component.literal("schema asset is missing from the mod jar — please report a bug"));

    private static final SimpleCommandExceptionType WRITE_FAILED =
            new SimpleCommandExceptionType(Component.literal("could not write schema to disk; check server logs"));

    private SchemaSubcommand() {}

    static int dump(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path target = worldRoot.resolve(OUTPUT_SUBPATH);

        try (InputStream in = SchemaSubcommand.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                LOGGER.error(
                        "[{}] /recipe_test schema: bundled asset {} is not on the classpath; expected to write to {}",
                        RecipeTestMod.MODID,
                        SCHEMA_RESOURCE,
                        target);
                throw MISSING_ASSET.create();
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LOGGER.error(
                    "[{}] /recipe_test schema: failed to write {} to {}: {}",
                    RecipeTestMod.MODID,
                    SCHEMA_RESOURCE,
                    target,
                    ex.getMessage(),
                    ex);
            throw WRITE_FAILED.create();
        }

        Path absolute = target.toAbsolutePath();
        ctx.getSource()
                .sendSuccess(
                        () -> Component.literal(
                                "Schema written to: " + absolute + System.lineSeparator()
                                        + "Add this to your IDE's JSON Schema settings to get completion on machine_spec files."),
                        false);
        return 1;
    }
}

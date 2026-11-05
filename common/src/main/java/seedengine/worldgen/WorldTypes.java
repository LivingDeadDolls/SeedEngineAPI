package seedengine.worldgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The world type (world preset) a server's world was made with. A world keeps its generators, not the preset's name,
 * so it is the preset whose dimensions have the same generators (as their saved form), in the server's own registry
 * of presets (with its data packs').
 */
public final class WorldTypes {
    private WorldTypes() {
    }

    /** Empty for a world whose generators no preset has (a customized flat world, a mod's world type). */
    public static Optional<Identifier> of(MinecraftServer server) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Optional<Identifier> found = Optional.empty();
        for (Holder.Reference<WorldPreset> preset : server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET).listElements().toList()) {
            if (!sameGenerators(server, preset.value(), ops)) continue;
            // Two presets with the same generators: the normal one, which is what a player would have chosen.
            if (found.isEmpty() || preset.is(WorldPresets.NORMAL)) found = Optional.of(preset.key().identifier());
        }
        return found;
    }

    private static boolean sameGenerators(MinecraftServer server, WorldPreset preset, RegistryOps<JsonElement> ops) {
        Map<ResourceKey<LevelStem>, LevelStem> dimensions = preset.createWorldDimensions().dimensions();
        if (dimensions.isEmpty()) return false;
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> dimension : dimensions.entrySet()) {
            ServerLevel level = server.getLevel(Registries.levelStemToLevel(dimension.getKey()));
            if (level == null) return false;
            Optional<JsonElement> expected = encode(dimension.getValue().generator(), ops);
            if (expected.isEmpty() || !expected.equals(encode(level.getChunkSource().getGenerator(), ops))) return false;
        }
        return true;
    }

    private static Optional<JsonElement> encode(ChunkGenerator generator, RegistryOps<JsonElement> ops) {
        return ChunkGenerator.CODEC.encodeStart(ops, generator).result();
    }
}

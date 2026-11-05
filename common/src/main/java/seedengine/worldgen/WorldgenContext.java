package seedengine.worldgen;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Everything vanilla world generation needs to decide where structures go, for one seed and one dimension.
 * The objects are vanilla's own; they are safe to use from worker threads, as the server's world generation does.
 *
 * @param environmentAttributes the dimension's attributes, which the stand-in levels answer with (lava next to a feature's
 *                              blocks reads whether it flows fast)
 * @param generatesStructures false for a world created with structures turned off (known in singleplayer only)
 * @param source              where the generator came from, for logs and for telling contexts apart
 * @param featureSeeds        by feature, the seed a server places it with in place of the world seed (see {@code ServerSeeds})
 */
public record WorldgenContext(
        ResourceKey<Level> dimension,
        long seed,
        RegistryAccess registries,
        ChunkGenerator generator,
        RandomState randomState,
        ChunkGeneratorStructureState structureState,
        StructureTemplateManager templates,
        LevelHeightAccessor height,
        EnvironmentAttributeReader environmentAttributes,
        boolean generatesStructures,
        String source,
        Map<Identifier, Long> featureSeeds
) {
    /** A context whose features are all placed with the world seed. */
    public WorldgenContext(ResourceKey<Level> dimension, long seed, RegistryAccess registries, ChunkGenerator generator, RandomState randomState,
                           ChunkGeneratorStructureState structureState, StructureTemplateManager templates, LevelHeightAccessor height,
                           EnvironmentAttributeReader environmentAttributes, boolean generatesStructures, String source) {
        this(dimension, seed, registries, generator, randomState, structureState, templates, height, environmentAttributes, generatesStructures,
                source, Map.of());
    }

    /** The seed a feature is placed with in place of the world seed (a Paper server's {@code feature-seeds}), or null. */
    public Long featureSeed(PlacedFeature feature) {
        if (featureSeeds.isEmpty()) return null;
        return feature.feature().unwrapKey().map(key -> featureSeeds.get(key.identifier())).orElse(null);
    }

    /** The server's own generator for a level: singleplayer, where data packs and every setting are known. */
    public static WorldgenContext of(ServerLevel level) {
        ServerChunkCache chunks = level.getChunkSource();
        return new WorldgenContext(level.dimension(), level.getSeed(), level.registryAccess(), chunks.getGenerator(),
                chunks.randomState(), chunks.getGeneratorState(), level.getStructureTemplateManager(),
                LevelHeightAccessor.create(level.getMinY(), level.getHeight()), level.environmentAttributes(),
                level.getServer().getWorldGenSettings().options().generateStructures(), "integrated server");
    }
}

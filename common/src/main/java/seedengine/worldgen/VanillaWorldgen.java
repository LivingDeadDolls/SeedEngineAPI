package seedengine.worldgen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import seedengine.Constants;
import seedengine.api.ServerSeeds;
import seedengine.mixin.NamedHolderSetInvoker;
import seedengine.platform.Services;

/**
 * World generation built from the vanilla data pack inside the game jar, for servers whose generator the client
 * cannot see (worldgen registries are not sent to clients). Matches a server running vanilla generation with the
 * chosen world preset; data packs and generator mods on the server are not known.
 * <p>
 * Loads the registries the way {@code WorldLoader} does for a new world, without the reloadable resources.
 */
public final class VanillaWorldgen {
    private static Loaded loaded;

    private record Loaded(CloseableResourceManager resources, RegistryAccess.Frozen registries, LevelStorageSource.LevelStorageAccess storage,
                          StructureTemplateManager templates) {
    }

    private VanillaWorldgen() {
    }

    /**
     * A new context for a seed, dimension and world preset (e.g. {@code minecraft:normal}). Slow the first time (the
     * data is loaded once). An unknown preset, or one without the dimension, is an {@link IllegalArgumentException}.
     */
    public static WorldgenContext context(long seed, ResourceKey<Level> dimension, Identifier preset) {
        return context(seed, dimension, preset, ServerSeeds.NONE);
    }

    /** {@link #context(long, ResourceKey, Identifier)} for a server that places some structures and features with seeds of its own. */
    public static WorldgenContext context(long seed, ResourceKey<Level> dimension, Identifier preset, ServerSeeds seeds) {
        requireGameTags();
        Loaded data = load();
        RegistryAccess registries = data.registries();
        WorldPreset worldPreset = registries.lookupOrThrow(Registries.WORLD_PRESET).get(ResourceKey.create(Registries.WORLD_PRESET, preset))
                .orElseThrow(() -> new IllegalArgumentException("Unknown world type " + preset)).value();
        LevelStem stem = worldPreset.createWorldDimensions().get(Registries.levelToLevelStem(dimension))
                .orElseThrow(() -> new IllegalArgumentException("World type " + preset + " has no " + dimension.identifier()));
        ChunkGenerator generator = stem.generator();
        // Same as ChunkMap: the noise settings of a noise generator, or a flat stand-in for anything else.
        RandomState randomState = generator instanceof NoiseBasedChunkGenerator noise
                ? RandomState.create(noise.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), seed)
                : RandomState.create(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.dummy(), registries.lookupOrThrow(Registries.NOISE), seed);
        ChunkGeneratorStructureState structureState = ServerSeedStructures.apply(
                generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed), registries, seeds);
        // The rings of strongholds and the placements by structure, which the state otherwise works out on first use,
        // into maps that are not for threads: here, before any thread asks.
        ChunkSteps.onThisThread(() -> {
            structureState.ensureStructuresGenerated();
            return null;
        });
        DimensionType type = stem.type().value();
        return new WorldgenContext(dimension, seed, registries, generator, randomState, structureState,
                data.templates(), LevelHeightAccessor.create(type.minY(), type.height()),
                // The dimension's own values only: a server's level also has those of the biomes and of the time of day.
                EnvironmentAttributeSystem.builder().addConstantLayer(type.attributes()).build(), true,
                "vanilla data, " + preset + (seeds.equals(ServerSeeds.NONE) ? "" : ", a server's own seeds"), seeds.featureSeeds());
    }

    /** The registries of the vanilla data (world generation, enchantments, trades and the rest a new world loads). */
    public static RegistryAccess registries() {
        requireGameTags();
        return load().registries();
    }

    /**
     * The game's own tags of blocks and the like (which features and carvers place by) are the world's: until a world is
     * joined they are empty, and generation built on them would be wrong. Refused then.
     */
    private static void requireGameTags() {
        boolean empty = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.tags.BlockTags.FEATURES_CANNOT_REPLACE)
                .map(tag -> tag.size() == 0).orElse(true);
        if (empty) {
            throw new IllegalStateException("The game's tags are not loaded yet (no world was joined): the vanilla data cannot be worked with");
        }
    }

    /**
     * The same context with templates loaded afresh, none of them used yet (tests: the caches templates fill on first
     * use are where threads would meet).
     */
    public static WorldgenContext withFreshTemplates(WorldgenContext context) {
        Loaded data = load();
        StructureTemplateManager templates = new StructureTemplateManager(data.resources(), data.storage(), DataFixers.getDataFixer(),
                data.registries().lookupOrThrow(Registries.BLOCK));
        return new WorldgenContext(context.dimension(), context.seed(), context.registries(), context.generator(), context.randomState(),
                context.structureState(), templates, context.height(), context.environmentAttributes(), context.generatesStructures(), context.source(),
                context.featureSeeds());
    }

    private static synchronized Loaded load() {
        if (loaded != null) return loaded;
        long start = System.nanoTime();
        PackRepository packs = ServerPacksSource.createVanillaTrustedRepository();
        packs.reload();
        packs.setSelected(List.of("vanilla"));
        CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected());
        LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
        List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
        bindDataOnlyTags(resources, layers.getLayer(RegistryLayer.STATIC), staticTags);
        List<HolderLookup.RegistryLookup<?>> worldContext = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), staticTags);
        RegistryAccess.Frozen world = RegistryDataLoader.load(resources, worldContext, RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
        RegistryAccess.Frozen registries = layers.replaceFrom(RegistryLayer.WORLDGEN, world).compositeAccess();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        LevelStorageSource.LevelStorageAccess storage = templateStorage();
        StructureTemplateManager templates = new StructureTemplateManager(resources, storage, DataFixers.getDataFixer(), blocks);
        loaded = new Loaded(resources, registries, storage, templates);
        Constants.LOG.info("Loaded vanilla world generation data in {} ms", (System.nanoTime() - start) / 1_000_000);
        return loaded;
    }

    /**
     * The tags of the game's own registries (blocks, potions, ...) that only the data names (a potion tag a trade draws
     * from) are made anew for the vanilla data and stay empty, since the game's registries are not reloaded here. Fill
     * those with the vanilla data's contents. The tags the game itself knows are the game's, bound by the world being
     * played, and are left alone.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void bindDataOnlyTags(CloseableResourceManager resources, RegistryAccess staticLayer, List<Registry.PendingTags<?>> pending) {
        for (Registry.PendingTags<?> tags : pending) {
            Registry registry = staticLayer.lookupOrThrow((ResourceKey) tags.key());
            Map<TagKey<?>, List<Holder<?>>> contents = null;
            for (HolderSet.Named<?> tag : tags.lookup().listTags().toList()) {
                if (tag.isBound() || registry.get(tag.key()).filter(own -> own == tag).isPresent()) continue;
                if (contents == null) {
                    contents = (Map) TagLoader.loadTagsForRegistry(resources, (ResourceKey) tags.key(), (TagLoader.ElementLookup) TagLoader.ElementLookup.fromFrozenRegistry(registry));
                }
                ((NamedHolderSetInvoker) tag).seedengine$bind(contents.getOrDefault(tag.key(), List.of()));
            }
        }
    }

    /** The empty template folders of game instances no longer running ({@code templates-<pid>}). */
    private static void removeFoldersOfEndedProcesses(Path cache) {
        try (java.util.stream.Stream<Path> folders = java.nio.file.Files.list(cache)) {
            for (Path folder : folders.toList()) {
                String name = folder.getFileName().toString();
                if (!name.matches("templates-[0-9]{1,18}")) continue;
                if (ProcessHandle.of(Long.parseLong(name.substring("templates-".length()))).isPresent()) continue;
                // One at a time: a folder that cannot be removed (a file still open) leaves the others to be.
                try (java.util.stream.Stream<Path> inside = java.nio.file.Files.walk(folder)) {
                    for (Path path : inside.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
                } catch (IOException | RuntimeException e) {
                    Constants.LOG.debug("Could not clear the old template folder {}: {}", folder, e.toString());
                }
            }
        } catch (IOException | RuntimeException e) {
            Constants.LOG.debug("Could not clear old template folders in {}: {}", cache, e.toString());
        }
    }

    /**
     * StructureTemplateManager also reads templates saved in a world; give it an empty folder of our own. It holds a
     * session lock that is released when the game exits.
     */
    private static LevelStorageSource.LevelStorageAccess templateStorage() {
        Path cache = Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve("cache");
        LevelStorageSource source = LevelStorageSource.createDefault(cache);
        try {
            return source.createAccess("templates");
        } catch (IOException | RuntimeException locked) {
            // Another game instance holds the lock; use a folder of this process, and clear those of processes gone.
            removeFoldersOfEndedProcesses(cache);
            try {
                return source.createAccess("templates-" + ProcessHandle.current().pid());
            } catch (IOException e) {
                throw new IllegalStateException("Could not prepare " + cache, e);
            }
        }
    }
}

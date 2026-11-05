package seedengine.worldgen;

import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/** The biome a generator puts at a block, in one place because the lookup differs between Minecraft versions. */
public final class GeneratedBiome {
    /** Biomes looked up one after another on one thread. */
    public interface Sampler {
        Holder<Biome> at(int x, int y, int z);
    }

    private GeneratedBiome() {
    }

    public static Holder<Biome> at(WorldgenContext context, int x, int y, int z) {
        return context.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                context.randomState().sampler());
    }

    /** A sampler for many lookups, with the climate sampler {@code /locate biome} uses: one thread only. */
    public static Sampler sampler(WorldgenContext context) {
        return (x, y, z) -> at(context, x, y, z);
    }

    /** The nearest biome allowed and where, by the game's own search for {@code /locate biome}; null if none. */
    public static Pair<BlockPos, Holder<Biome>> closest(WorldgenContext context, BlockPos origin, int radius, int horizontalStep,
                                                        int verticalStep, Predicate<Holder<Biome>> allowed) {
        return context.generator().getBiomeSource().findClosestBiome3d(origin, radius, horizontalStep, verticalStep, allowed,
                context.randomState().sampler(), heightOnly(context.height()));
    }

    /** A level for the search, which only reads the dimension's height from it (the heights it samples between). */
    private static LevelReader heightOnly(LevelHeightAccessor height) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getMinY" -> height.getMinY();
                    case "getMaxY" -> height.getMaxY();
                    case "getHeight" -> height.getHeight();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "the height of a dimension";
                    default -> throw new UnsupportedOperationException("A search for a biome read " + method.getName() + " of the level");
                });
    }
}

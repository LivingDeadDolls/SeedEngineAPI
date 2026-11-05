package seedengine.locate;


import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.DimensionOriginStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import seedengine.worldgen.WorldgenContext;

/**
 * Lists the chunks in an area where a structure may start, using only each structure set's placement (cheap: no
 * terrain or biomes, except for the rings of strongholds, which vanilla works out once per world).
 */
public final class CandidateScanner {
    private CandidateScanner() {
    }

    /** Candidates in the square from (minX, minZ) to (maxX, maxZ) in chunk coordinates, inclusive. */
    public static List<Candidate> scan(WorldgenContext context, int minX, int minZ, int maxX, int maxZ) {
        List<Candidate> found = new ArrayList<>();
        if (!context.generatesStructures()) return found;
        ChunkGeneratorStructureState state = context.structureState();
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                scanSpread(state, set, spread, minX, minZ, maxX, maxZ, found);
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                for (ChunkPos pos : state.getRingPositionsFor(rings)) {
                    addIfStarts(state, set, pos.x(), pos.z(), minX, minZ, maxX, maxZ, found);
                }
            } else if (placement instanceof DimensionOriginStructurePlacement) {
                ChunkPos origin = state.getDimensionOrigin();
                addIfStarts(state, set, origin.x(), origin.z(), minX, minZ, maxX, maxZ, found);
            } else {
                // A kind of placement this does not know (from a mod): every chunk of the area is asked.
                // Counted in long: an area reaching the end of the int range still ends.
                for (long z = minZ; z <= maxZ; z++) {
                    for (long x = minX; x <= maxX; x++) {
                        addIfStarts(state, set, (int) x, (int) z, minX, minZ, maxX, maxZ, found);
                    }
                }
            }
        }
        return found;
    }

    /** One potential chunk per spacing×spacing region; only regions touching the area can have one inside it. */
    private static void scanSpread(ChunkGeneratorStructureState state, Holder<StructureSet> set, RandomSpreadStructurePlacement spread,
                                   int minX, int minZ, int maxX, int maxZ, List<Candidate> found) {
        int spacing = spread.spacing();
        for (int regionZ = Math.floorDiv(minZ, spacing); regionZ <= Math.floorDiv(maxZ, spacing); regionZ++) {
            for (int regionX = Math.floorDiv(minX, spacing); regionX <= Math.floorDiv(maxX, spacing); regionX++) {
                ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(), regionX * spacing, regionZ * spacing);
                addIfStarts(state, set, potential.x(), potential.z(), minX, minZ, maxX, maxZ, found);
            }
        }
    }

    /** {@code isStructureChunk} also applies the set's frequency and its distance from other structure sets. */
    private static void addIfStarts(ChunkGeneratorStructureState state, Holder<StructureSet> set, int x, int z,
                                    int minX, int minZ, int maxX, int maxZ, List<Candidate> found) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return;
        if (set.value().placement().isStructureChunk(state, x, z)) {
            found.add(new Candidate(set, new ChunkPos(x, z)));
        }
    }
}

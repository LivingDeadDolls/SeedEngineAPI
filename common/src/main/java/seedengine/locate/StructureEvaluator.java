package seedengine.locate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import seedengine.worldgen.WorldgenContext;

/**
 * Decides whether a structure really starts in a candidate chunk, with vanilla's {@link Structure#generate} (biome,
 * terrain height and every other check of the structure itself).
 * <p>
 * Picking which structure of a set to try follows {@code ChunkGenerator.createStructures} line by line (it is
 * private there): with several structures, a random one by weight, then the next until one generates.
 */
public final class StructureEvaluator {
    private StructureEvaluator() {
    }

    public static Optional<Located> evaluate(WorldgenContext context, Candidate candidate) {
        StructureSet set = candidate.set().value();
        List<StructureSet.StructureSelectionEntry> structures = set.structures();
        ChunkPos chunk = candidate.chunk();
        if (structures.size() == 1) {
            return tryGenerate(context, candidate, structures.get(0));
        }
        List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(structures);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.structureState().getLevelSeed(), chunk.x(), chunk.z());
        int total = 0;
        for (StructureSet.StructureSelectionEntry option : options) {
            total += option.weight();
        }
        while (!options.isEmpty()) {
            int choice = random.nextInt(total);
            int index = 0;
            for (StructureSet.StructureSelectionEntry option : options) {
                choice -= option.weight();
                if (choice < 0) break;
                index++;
            }
            StructureSet.StructureSelectionEntry selected = options.get(index);
            Optional<Located> located = tryGenerate(context, candidate, selected);
            if (located.isPresent()) return located;
            options.remove(index);
            total -= selected.weight();
        }
        return Optional.empty();
    }

    /** The start of a structure found before, generated again: one of its own, not shared with the one kept. */
    public static StructureStart generateAgain(WorldgenContext context, Located located) {
        Holder<Structure> holder = located.structure();
        return holder.value().generate(holder, context.dimension(), context.registries(), context.generator(),
                context.generator().getBiomeSource(), context.randomState(), context.templates(),
                context.structureState().getLevelSeed(), located.chunk(), 0, context.height(), holder.value().biomes()::contains);
    }

    private static Optional<Located> tryGenerate(WorldgenContext context, Candidate candidate, StructureSet.StructureSelectionEntry selected) {
        Holder<Structure> holder = selected.structure();
        Structure structure = holder.value();
        StructureStart start = structure.generate(holder, context.dimension(), context.registries(), context.generator(),
                context.generator().getBiomeSource(), context.randomState(), context.templates(),
                context.structureState().getLevelSeed(), candidate.chunk(), 0, context.height(), structure.biomes()::contains);
        if (!start.isValid()) return Optional.empty();
        List<BoundingBox> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            pieces.add(piece.getBoundingBox());
        }
        return Optional.of(new Located(idOf(holder), idOf(candidate.set()), holder, candidate.chunk(), start.getBoundingBox(), List.copyOf(pieces), start));
    }

    private static Identifier idOf(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier()).orElse(Identifier.fromNamespaceAndPath("seedengine", "unnamed"));
    }
}

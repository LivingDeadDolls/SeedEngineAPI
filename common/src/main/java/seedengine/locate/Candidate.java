package seedengine.locate;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** A chunk where a structure set's placement allows a structure to start. Whether one really does is decided later. */
public record Candidate(Holder<StructureSet> set, ChunkPos chunk) {
    public long distanceSquared(ChunkPos from) {
        long dx = chunk.x() - from.x();
        long dz = chunk.z() - from.z();
        return dx * dx + dz * dz;
    }
}

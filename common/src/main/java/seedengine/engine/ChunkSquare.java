package seedengine.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/** A square of chunks between two corners given in any order, both included. */
record ChunkSquare(int minX, int minZ, int maxX, int maxZ) {
    static ChunkSquare of(ChunkPos a, ChunkPos b) {
        return new ChunkSquare(Math.min(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), Math.max(a.z(), b.z()));
    }

    /**
     * Its chunks, nearest the middle first (ring by ring, by the larger of the two distances), and row by row within a
     * ring. The middle of an even size is rounded down.
     */
    List<ChunkPos> nearestMiddleFirst() {
        int middleX = Math.floorDiv(minX + maxX, 2);
        int middleZ = Math.floorDiv(minZ + maxZ, 2);
        List<ChunkPos> chunks = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) chunks.add(new ChunkPos(x, z));
        }
        chunks.sort(Comparator.comparingInt(chunk -> Math.max(Math.abs(chunk.x() - middleX), Math.abs(chunk.z() - middleZ))));
        return chunks;
    }
}

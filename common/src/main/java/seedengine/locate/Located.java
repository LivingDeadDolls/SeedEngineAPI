package seedengine.locate;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * A structure that world generation places, worked out from the seed.
 *
 * @param id     the structure's registry id, e.g. {@code minecraft:village_plains}
 * @param set    the id of the structure set it was picked from, e.g. {@code minecraft:villages}
 * @param chunk  the chunk it starts in
 * @param box    the box around all of it
 * @param pieces the box of each piece (a house of a village, a room of a stronghold)
 * @param start  vanilla's start, kept for the block-by-block view
 */
public record Located(
        Identifier id,
        Identifier set,
        Holder<Structure> structure,
        ChunkPos chunk,
        BoundingBox box,
        List<BoundingBox> pieces,
        StructureStart start
) {
}

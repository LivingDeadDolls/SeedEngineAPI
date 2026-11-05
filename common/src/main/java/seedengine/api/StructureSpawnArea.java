package seedengine.api;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a structure decides which mobs of a category spawn, instead of the biome (a fortress's wither skeletons, an
 * outpost's pillagers). Inside the boxes, a mob of the category that spawns naturally is one of these (none if the list
 * is empty: an ocean monument keeps out axolotls). Whether one spawns on a given block (light, the block under it,
 * room, difficulty) is not known from the seed.
 *
 * @param structure the structure
 * @param category  the category of mobs it decides
 * @param pieces    whether the mobs spawn only inside its pieces (a fortress's rooms and bridges) rather than anywhere
 *                  in the box around all of it
 * @param boxes     where they spawn: the box of each piece, or the box around all of the structure
 * @param mobs      the mobs that may spawn there, with their weights
 */
public record StructureSpawnArea(FoundStructure structure, MobCategory category, boolean pieces, List<BoundingBox> boxes, List<Mob> mobs) {
    /**
     * A mob that may spawn, and how many in a group.
     *
     * @param type     the mob, e.g. {@code minecraft:wither_skeleton}
     * @param weight   how likely it is picked against the others of the list
     * @param minCount the fewest in a group
     * @param maxCount the most in a group
     */
    public record Mob(Identifier type, int weight, int minCount, int maxCount) {
    }
}

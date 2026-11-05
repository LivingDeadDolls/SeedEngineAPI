package seedengine.api;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A piece of a structure, with what it is: the box {@link SeedEngineApi#pieces} gives, and its kind and template.
 * An end city's ship is the piece with the template {@code minecraft:end_city/ship}.
 *
 * @param type     the kind of piece, e.g. {@code minecraft:ecp} (an end city's), {@code minecraft:shpr} (a stronghold's
 *                 portal room), {@code minecraft:jigsaw} (a village's and other jigsaw structures')
 * @param template the structure template it places, e.g. {@code minecraft:end_city/ship} or
 *                 {@code minecraft:village/plains/houses/plains_small_house_1}; empty for a piece built block by block
 *                 (a stronghold's room, a mineshaft's corridor) or from a template without an id
 * @param box      the box around the piece, where it is placed (as {@link SeedEngineApi#pieces} gives it)
 */
public record FoundPiece(Identifier type, Optional<Identifier> template, BoundingBox box) {
}

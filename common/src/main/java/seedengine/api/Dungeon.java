package seedengine.api;

/**
 * A dungeon (monster room) and whether it is there whichever order the chunks around were generated in. An uncertain
 * one is there in one order and not in another (a neighbour's mineshaft corridor cuts through the room, say); the
 * world generated them in the order players made them, which the seed does not tell.
 *
 * @param spawner the dungeon's spawner (its position is the middle of the room's floor)
 */
public record Dungeon(Spawner spawner, boolean certain) {
}

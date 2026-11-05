package seedengine.api;

/**
 * A spawner a structure may place, and whether it places it whichever order the world generated the structure's chunks
 * in. An uncertain one is there in one order and not in another (a mineshaft corridor puts its cave spider spawner in
 * the first of its chunks generated whose draw lands inside it); the world generated them in the order players made
 * them, which the seed does not tell.
 */
public record StructureSpawner(Spawner spawner, boolean certain) {
}

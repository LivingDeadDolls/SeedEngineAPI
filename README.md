# Seed Engine API

English | [日本語](README.ja.md)

A library Fabric mod that works out what a world generates from its seed, using the game's own code, for other mods to use.
On its own it only adds the `/setseed` command. Install it alongside a mod that requires it, on the same side as that mod (client, server, or both).

## Features

For a seed, a world type (Default, Large Biomes, Amplified and so on) and a dimension, or for a server's own world, it works out:

- Structures: where each structure starts and the box around it, as world generation places them, biome and terrain checks included. The boxes of its pieces (the houses of a village, the rooms of a stronghold) too, and what each piece is: its kind and the template it places (so an end city's ship can be told apart).
- Structure blocks and spawners: the blocks a structure places, and its spawners with their mobs (mineshafts, strongholds, fortresses, trial chambers). Where the order the world generated the chunks in changes them (where a mineshaft corridor puts its cave spider spawner), it tells which blocks and spawners are not certain, and what they may be.
- Dungeons: the dungeons in an area and their mobs, with the terrain and caves built the way world generation builds them.
- Terrain height and biomes: at any place. Also the nearest place of a biome (the way `/locate biome` finds it), the biomes over an area at a set spacing, and every biome a dimension can generate.
- Where structures spawn their own mobs: for each structure (a pillager outpost, an ocean monument, a swamp hut, a nether fortress), the boxes where its mobs spawn instead of the biome's, and which mobs.

From the seed alone it also works out villager trades: for each trade set (a librarian's first level, say), what the first villager in the world to take up the job (or reach the level) gets, what the second gets, and so on. How far a world has got is not known from the seed. Cartographers' explorer maps are not worked out, since they search the world.

With a seed, it uses the vanilla data inside the game, so it works on any server whose world generation and trades are vanilla. With a server's own world, the server's data packs are included.

Spigot and Paper servers may place some structures and features with seeds of their own instead of the world seed (`seed-village` and the rest in `spigot.yml`, Paper's `feature-seeds`). Clients are not sent them. If you know them from the server's settings, pass them, and the answers are that server's.

### The seed of the world you are in

On the client, it keeps the seed of the world you are in, for the mods that use it:

- In singleplayer, and in a world you opened to LAN, the seed is known as soon as you join.
- On a server, enter it with `/setseed <seed>` (a number, or text, read the way the world creation screen reads it), or run the vanilla `/seed` if the server lets you. `/setseed clear` removes the seed you entered, and `/setseed` alone shows the seed in use and where it came from.
- A seed you entered and a `/seed` result are remembered for each server, and used again the next time you join it. A seed you entered takes the place of the `/seed` result.
- It also tells the world type of the world you are in (Default, Large Biomes, Amplified): in singleplayer from the world itself, on a server from the terrain around you once the seed is known (servers do not send the world type to clients). It also notices when the seed and the world type do not fit the terrain you were sent (a seed entered wrong, a server that changes generation), so that the mods using it can tell you.

## For mod developers

Ask `seedengine.api.SeedEngineApi`. Its Javadoc tells which thread each question may be asked from and which ones are heavy. `currentSeed()` gives the seed of the world the client is in (if known), and `addSeedListener` tells you when it changes. The world type works the same way with `currentWorldType()` and `addWorldTypeListener`, and `currentWorldCheck()` tells whether the seed and the world type fit the terrain the client was sent.
Build against the jar attached to each release and list `seedengine` under `depends` in your `fabric.mod.json`. You may also nest the jar in your own with Loom's `include`, so that players install only your mod. When several mods nest it, Fabric Loader loads only the newest one; a method, once released, keeps its arguments and meaning, so a mod built against an older version still works with a newer one.

## Requirements

- [Fabric Loader](https://fabricmc.net/use/)
- [Fabric API](https://modrinth.com/mod/fabric-api)

The supported Minecraft versions are listed on each release.

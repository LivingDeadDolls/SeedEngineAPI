package seedengine.api;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;

/**
 * A spawner world generation places: a mob spawner (dungeons, mineshafts, strongholds, fortresses) or a trial spawner
 * (trial chambers).
 *
 * @param mob   for a spawner, the mob it spawns (e.g. {@code minecraft:cave_spider}); for a trial spawner, the
 *              configuration it uses (e.g. {@code minecraft:trial_chamber/melee/zombie}); empty if unknown
 * @param trial whether it is a trial spawner
 */
public record Spawner(BlockPos pos, Optional<Identifier> mob, boolean trial) {
    /**
     * The spawner a block entity stands for, read from what was written into it; empty for other blocks. Works for
     * the block entities of a real world too (a loaded chunk's). Any thread that may read the block entity.
     */
    public static Optional<Spawner> of(BlockEntity blockEntity, HolderLookup.Provider registries) {
        BlockPos pos = blockEntity.getBlockPos().immutable();
        if (blockEntity instanceof SpawnerBlockEntity) {
            CompoundTag tag = blockEntity.saveWithoutMetadata(registries);
            Optional<Identifier> mob = tag.getCompound("SpawnData").flatMap(data -> data.getCompound("entity"))
                    .flatMap(entity -> entity.getString("id")).map(Identifier::tryParse);
            return Optional.of(new Spawner(pos, mob, false));
        }
        if (blockEntity instanceof TrialSpawnerBlockEntity) {
            CompoundTag tag = blockEntity.saveWithoutMetadata(registries);
            Optional<Identifier> config = tag.getString("normal_config").map(Identifier::tryParse);
            return Optional.of(new Spawner(pos, config, true));
        }
        return Optional.empty();
    }
}

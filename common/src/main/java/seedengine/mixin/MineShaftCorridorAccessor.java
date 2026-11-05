package seedengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether a mineshaft corridor is one with a cave spider spawner, and whether it placed it: it places it in the first
 * chunk (in the order the world decorates them) whose random puts it inside, so where depends on that order.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor")
public interface MineShaftCorridorAccessor {
    @Accessor("spiderCorridor")
    boolean seedengine$spiderCorridor();

    @Accessor("hasPlacedSpider")
    boolean seedengine$hasPlacedSpider();

    @Accessor("hasPlacedSpider")
    void seedengine$setHasPlacedSpider(boolean placed);
}

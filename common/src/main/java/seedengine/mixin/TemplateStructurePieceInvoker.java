package seedengine.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The template a piece places, as the piece names it (an end city's adds its folder: {@code minecraft:end_city/ship}). */
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceInvoker {
    @Invoker("makeTemplateLocation")
    Identifier seedengine$templateLocation();
}

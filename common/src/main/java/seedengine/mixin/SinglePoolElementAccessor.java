package seedengine.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A jigsaw piece's template: its id, or the template itself where a data pack wrote it inline (no id). */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
    @Accessor("template")
    Either<Identifier, StructureTemplate> seedengine$template();
}

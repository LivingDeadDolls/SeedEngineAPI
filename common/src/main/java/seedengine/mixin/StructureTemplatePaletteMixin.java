package seedengine.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureTemplate.Palette.class)
abstract class StructureTemplatePaletteMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    /**
     * A palette keeps the blocks of each kind it was asked for in a plain HashMap, filled on first use. Structure
     * Scout works out structures on several threads that share templates, and threads filling the map at once fail
     * (a jigsaw structure would then be missed). A concurrent map lets them.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void seedengine$concurrentCache(CallbackInfo info) {
        this.cache = new ConcurrentHashMap<>();
    }
}

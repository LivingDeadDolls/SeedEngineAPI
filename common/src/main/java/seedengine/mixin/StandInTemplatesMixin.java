package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.shape.GhostCapture;

@Mixin({FossilFeature.class, TemplateFeature.class})
abstract class StandInTemplatesMixin {
    /**
     * Fossils and template features (a sulfur spring) take their templates from the server. This mod's stand-in levels
     * have no server behind them: there they get the templates the stand-in's generation has, so that they are placed
     * (before the dungeons of the same chunk, as in the world). With a server nothing changes.
     */
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getServer()Lnet/minecraft/server/MinecraftServer;"))
    private MinecraftServer seedengine$noServer(ServerLevel level, Operation<MinecraftServer> original, @Local(argsOnly = true) WorldGenLevel placing) {
        if (level == null && GhostCapture.isGhost(placing)) return null;
        return original.call(level);
    }

    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getStructureTemplateManager()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;"))
    private StructureTemplateManager seedengine$standInTemplates(MinecraftServer server, Operation<StructureTemplateManager> original,
                                                                 @Local(argsOnly = true) WorldGenLevel placing) {
        if (server == null && GhostCapture.isGhost(placing)) return GhostCapture.contextOf(placing).templates();
        return original.call(server);
    }
}

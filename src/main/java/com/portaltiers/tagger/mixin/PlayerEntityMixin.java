package com.portaltiers.tagger.mixin;

import com.portaltiers.tagger.PortalTierManager;
import com.portaltiers.tagger.config.PortalConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Portal tier badge to the floating nametag rendered above a
 * player's head (driven by {@link PlayerEntity#getDisplayName()}).
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void portaltiertagger$appendTier(CallbackInfoReturnable<Text> cir) {
        PortalConfig cfg = PortalConfig.get();
        if (!cfg.enabled || !cfg.showInNametag) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        Text original = cir.getReturnValue();
        if (original == null) return;

        Text modified = PortalTierManager.appendTier(self.getGameProfile().getName(), original);
        if (modified != original) {
            cir.setReturnValue(modified);
        }
    }
}

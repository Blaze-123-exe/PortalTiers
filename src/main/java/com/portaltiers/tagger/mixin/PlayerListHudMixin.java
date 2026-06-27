package com.portaltiers.tagger.mixin;

import com.portaltiers.tagger.PortalTierManager;
import com.portaltiers.tagger.config.PortalConfig;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Portal tier badge to a player's name in the tab list.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void portaltiertagger$appendTier(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        PortalConfig cfg = PortalConfig.get();
        if (!cfg.enabled || !cfg.showInTab) return;
        if (entry == null || entry.getProfile() == null) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        Text modified = PortalTierManager.appendTier(entry.getProfile().getName(), original);
        if (modified != original) {
            cir.setReturnValue(modified);
        }
    }
}

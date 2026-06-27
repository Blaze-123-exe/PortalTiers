package com.portaltiers.tagger.mixin;

import com.portaltiers.tagger.PortalTierManager;
import com.portaltiers.tagger.config.PortalConfig;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Injects tier badges before player names that appear in chat messages.
 * Targets the common funnel overload so both system and player messages pass
 * through.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private Text portaltiertagger$decorate(Text message) {
        PortalConfig cfg = PortalConfig.get();
        if (!cfg.enabled || !cfg.showInChat) return message;
        Text replaced = PortalTierManager.deepReplace(message);
        return replaced != null ? replaced : message;
    }
}

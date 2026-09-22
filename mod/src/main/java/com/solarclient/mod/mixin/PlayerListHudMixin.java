package com.solarclient.mod.mixin;

import com.solarclient.mod.client.social.SolarBadges;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the SolarClient rank badge in the tab list, by prepending the
 * badge glyph to each entry's rendered name. Because the badge is a font
 * glyph, vanilla's text renderer handles all the positioning — nothing here
 * touches coordinates.
 *
 * Hooked at RETURN of getPlayerName so we wrap whatever the game (and any
 * other mod) already decided the name should be, rather than replacing it.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void solarclient$addBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            Text badged = SolarBadges.withBadge(entry.getProfile().id(), cir.getReturnValue());
            cir.setReturnValue(badged);
        } catch (Exception ignored) {
            // A cosmetic badge must never take the tab list down.
        }
    }
}

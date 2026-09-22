package com.solarclient.mod.mixin;

import com.solarclient.mod.client.social.SolarBadges;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the rank badge on the floating name above a player's head.
 *
 * Hooked at the END of updateRenderState, which is where the game has just
 * finished deciding what a player's name label should say (including any
 * team colour or prefix a server applied). We wrap that final text, so:
 *   - the badge shows in the world, not just the tab list, and
 *   - whatever the server did to the name is preserved — we only prepend the
 *     glyph, we don't replace anything.
 *
 * Filtered to players and to labels that are actually present, so it never
 * touches mob nametags or costs anything when there's no label to draw.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void solarclient$badgeNameLabel(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        try {
            if (entity instanceof PlayerEntity player && state.displayName != null) {
                state.displayName = SolarBadges.withBadge(player.getUuid(), state.displayName);
            }
        } catch (Exception ignored) {
            // A cosmetic badge must never break entity rendering.
        }
    }
}

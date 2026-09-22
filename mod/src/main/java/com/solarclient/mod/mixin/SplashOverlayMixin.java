package com.solarclient.mod.mixin;

import com.solarclient.mod.client.gui.IntroPlayback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Mojang's splash branding with the SolarClient intro video.
 * Resource reload still runs underneath; we only take over what is drawn
 * and dismiss the overlay once both reload and the video have finished.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;

    @Unique private boolean solar$started;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void solar$introInsteadOfMojang(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.solar$started) {
            IntroPlayback.begin(this.client);
            this.solar$started = true;
        }

        int w = this.client.getWindow().getScaledWidth();
        int h = this.client.getWindow().getScaledHeight();

        IntroPlayback.renderVideoFrame(context, w, h);

        float progress = this.reload.getProgress();
        if (progress < 0.999f) {
            int barW = Math.min(220, Math.max(80, w / 3));
            int bx = (w - barW) / 2;
            int by = h - 28;
            context.fill(bx, by, bx + barW, by + 3, 0x66000000);
            context.fill(bx, by, bx + Math.max(2, (int) (barW * progress)), by + 3, 0xFFC9B6FF);
        }

        boolean reloadDone = this.reload.isComplete() || progress >= 0.999f;
        if (reloadDone && IntroPlayback.isVideoFinished()) {
            this.client.setOverlay(null);
        }

        ci.cancel();
    }
}

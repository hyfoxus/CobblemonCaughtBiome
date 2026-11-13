package org.hyfoxus.cobblemoncaughtbiome.mixin.client;

import com.cobblemon.mod.common.client.gui.summary.widgets.screens.info.InfoWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.hyfoxus.cobblemoncaughtbiome.CaughtBiome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InfoWidget.class)
public abstract class InfoWidgetMixin {
    private static final float SCALE = 0.5F;
    private static final int X_OFFSET = 8;
    private static final int Y_OFFSET = 131;

    @Shadow
    @Final
    private Pokemon pokemon;

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void cobblemoncaughtbiome$drawCaughtIn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractWidget self = (AbstractWidget) (Object) this;
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1F);
        graphics.drawString(
                Minecraft.getInstance().font,
                CaughtBiome.display(this.pokemon.getPersistentData()),
                (int) ((self.getX() + X_OFFSET) / SCALE),
                (int) ((self.getY() + Y_OFFSET) / SCALE),
                0xFFFFFF,
                true);
        graphics.pose().popPose();
    }
}

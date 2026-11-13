package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

final class JournalButton extends Button {
    private final boolean selected;

    JournalButton(int x, int y, int width, Component label, boolean selected, OnPress action) {
        super(x, y, width, 20, label, action, DEFAULT_NARRATION);
        this.selected = selected;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height,
                selected ? 0xFF233C3A : isHoveredOrFocused() ? 0xFF293944 : 0xFF1A2732);
        graphics.renderOutline(getX(), getY(), width, height,
                selected || isHoveredOrFocused() ? 0xFF000000 | SoulLinkColors.TEAL : 0xFF34434F);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 8);
        graphics.drawCenteredString(font, label, getX() + width / 2, getY() + 6,
                selected ? SoulLinkColors.TEAL : SoulLinkColors.TEXT);
    }
}

package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;
import org.lwjgl.glfw.GLFW;

/** Small, non-pausing popup anchored above the hotbar for an incoming Soul Link invite. */
public final class InvitePopupScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 50;
    private static final int OPTION_WIDTH = 90;
    private static final int OPTION_HEIGHT = 20;

    private final String inviter;
    private final String token;
    private int ticksRemaining;
    private int selected;
    private int panelLeft, panelTop;
    private int acceptX, acceptY, declineX, declineY;

    public InvitePopupScreen(String inviter, String token, int ticksRemaining) {
        super(Component.literal("Soul Link Invite"));
        this.inviter = inviter;
        this.token = token;
        this.ticksRemaining = ticksRemaining;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = height - 95;
        acceptY = panelTop + 28;
        declineY = acceptY;
        acceptX = panelLeft + 15;
        declineX = panelLeft + PANEL_WIDTH - 15 - OPTION_WIDTH;
    }

    private void sendCommand(String command) {
        if (minecraft != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command);
    }

    private void activate(int which) {
        sendCommand(which == 0 ? "soullink accept " + token : "soullink decline " + token);
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        ticksRemaining--;
        if (ticksRemaining <= 0 && minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF0121C27);
        graphics.fill(acceptX, acceptY, acceptX + OPTION_WIDTH, acceptY + OPTION_HEIGHT, selected == 0 ? 0xFF233C3A : 0xFF1A2732);
        graphics.fill(declineX, declineY, declineX + OPTION_WIDTH, declineY + OPTION_HEIGHT, selected == 1 ? 0xFF233C3A : 0xFF1A2732);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF34434F);
        graphics.drawCenteredString(font, fit(inviter + " invited you to a run", PANEL_WIDTH - 16),
                panelLeft + PANEL_WIDTH / 2, panelTop + 6, SoulLinkColors.TEXT);
        drawOption(graphics, "Accept", acceptX, acceptY, selected == 0);
        drawOption(graphics, "Decline", declineX, declineY, selected == 1);
        graphics.drawCenteredString(font, "expires in " + Math.max(0, ticksRemaining / 20) + "s",
                panelLeft + PANEL_WIDTH / 2, panelTop + PANEL_HEIGHT - 12, SoulLinkColors.TEXT);
    }

    private void drawOption(GuiGraphics graphics, String label, int x, int y, boolean highlighted) {
        graphics.renderOutline(x, y, OPTION_WIDTH, OPTION_HEIGHT, highlighted ? 0xFF000000 | SoulLinkColors.TEAL : 0xFF34434F);
        graphics.drawCenteredString(font, label, x + OPTION_WIDTH / 2, y + 6, highlighted ? SoulLinkColors.TEAL : SoulLinkColors.TEXT);
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inRect(mouseX, mouseY, acceptX, acceptY)) { activate(0); return true; }
            if (inRect(mouseX, mouseY, declineX, declineY)) { activate(1); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inRect(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + OPTION_WIDTH && mouseY >= y && mouseY <= y + OPTION_HEIGHT;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) { selected = 1 - selected; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { activate(selected); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

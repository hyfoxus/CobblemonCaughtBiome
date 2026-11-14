package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

public final class BiomeJournalScreen extends Screen {
    private static final int CARD_HEIGHT = 150;
    private static final int CARD_HEIGHT_COMPACT = 100;
    private static final int GAP = 8;
    private final BiomeTab biomeTab;
    private CompoundTag header;
    private EditBox search;
    private int left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight;
    private boolean compact;
    private int initialPage;
    private int refreshTicks;

    public BiomeJournalScreen(CompoundTag header, List<CompoundTag> rows) {
        super(Component.literal("Soul Link · Biome Journal"));
        this.biomeTab = new BiomeTab(this::addRenderableWidget, this::removeWidget, this::clearPortraitFocus);
        this.header = header.copy();
        biomeTab.setHeader(this.header);
        biomeTab.setRows(rows);
        biomeTab.setFilter(BiomeTab.Filter.parse(header.getString("filter")));
        this.initialPage = Math.max(1, header.getInt("page"));
    }

    void update(CompoundTag header, List<CompoundTag> rows) {
        this.header = header.copy();
        biomeTab.setHeader(this.header);
        biomeTab.setRows(rows);
        refreshTicks = 0;
        applyFilters();
    }

    private void clearPortraitFocus() {
        if (getFocused() instanceof PokemonPortrait) setFocused(search);
    }

    @Override
    protected void init() {
        left = Math.max(4, (width - Math.min(700, width - 16)) / 2);
        panelWidth = width - left * 2;
        compact = height < 225;
        contentTop = compact ? 88 : 108;
        contentBottom = Math.max(contentTop + 32, height - (compact ? 23 : 34));
        cardHeight = compact ? CARD_HEIGHT_COMPACT : CARD_HEIGHT;
        columns = panelWidth >= 490 ? 2 : 1;
        cardWidth = (panelWidth - 28 - GAP * (columns - 1)) / columns;
        biomeTab.setFont(font);
        biomeTab.setLayout(left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight, compact);
        search = new EditBox(font, left + 18, compact ? 36 : 55,
                Math.max(30, panelWidth - 156), 18, Component.literal("Search biomes"));
        search.setBordered(false);
        search.setTextColor(SoulLinkColors.TEXT);
        search.setHint(Component.literal("Search biomes…"));
        search.setMaxLength(100);
        search.setValue(biomeTab.query());
        search.setResponder(value -> { biomeTab.setQuery(value); applyFilters(); });
        addRenderableWidget(search);
        addRenderableWidget(new JournalButton(left + panelWidth - 128, compact ? 31 : 50, 66, Component.literal("Refresh"), false,
                button -> refresh()));
        addRenderableWidget(new JournalButton(left + panelWidth - 58, compact ? 31 : 50, 44, Component.literal("Close"), false,
                button -> onClose()));
        int tabWidth = (panelWidth - 28 - GAP * 3) / 4;
        for (BiomeTab.Filter value : BiomeTab.Filter.values()) {
            addRenderableWidget(new JournalButton(left + 14 + value.ordinal() * (tabWidth + GAP), compact ? 59 : 79, tabWidth,
                    Component.literal(value.label), biomeTab.filter() == value, button -> {
                        biomeTab.selectFilter(value);
                        rebuildWidgets();
                    }));
        }
        applyFilters();
        if (initialPage > 1) {
            biomeTab.scrollToPage(initialPage);
            initialPage = 1;
        }
    }

    private void applyFilters() {
        if (search == null) return;
        biomeTab.applyFilters();
    }

    private void refresh() {
        if (minecraft != null && minecraft.getConnection() != null && refreshTicks == 0) {
            refreshTicks = 100;
            minecraft.getConnection().sendCommand("soullink biomes " + biomeTab.filter().name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void tick() {
        if (refreshTicks > 0) refreshTicks--;
        biomeTab.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD9080D14);
        graphics.fill(left, 8, left + panelWidth, height - 8, 0xF0121C27);
        graphics.fill(left + 14, compact ? 31 : 50, left + panelWidth - 134,
                compact ? 51 : 70, 0xFF0D1721);
        biomeTab.renderCardBackgrounds(graphics);
        // Everything from here on is drawn after super.render() below: anything drawn before the widget
        // pipeline runs renders blurry on this screen (confirmed empirically), after it renders sharp.
        // Only backgrounds that sit behind a widget (panel/search-box fill, card fill/backdrop) stay above.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.renderOutline(left, 8, panelWidth, height - 16, 0xFF34434F);
        graphics.fill(left, 8, left + panelWidth, 10, 0xFF000000 | SoulLinkColors.TEAL);
        biomeTab.renderScrollbar(graphics);
        graphics.drawString(font, fit("SOUL LINK  /  BIOME JOURNAL", panelWidth - 28), left + 14,
                compact ? 12 : 18, SoulLinkColors.TEAL, false);
        String names = header.getString("firstName") + "  ↔  " + header.getString("secondName")
                + (header.getBoolean("paused") ? "  ·  Paused" : "  ·  Active");
        graphics.drawString(font, fit(names, panelWidth - 28), left + 14, compact ? 24 : 33, SoulLinkColors.TEXT, false);
        biomeTab.renderCardLabels(graphics);
        if (biomeTab.isEmpty()) {
            graphics.drawCenteredString(font, fit("No biomes match your search or filter.", panelWidth - 28), width / 2,
                    contentTop + 14, SoulLinkColors.TEXT);
        }
        String counts = "Available " + biomeTab.count(BiomeTab.Filter.AVAILABLE) + "   ·   Waiting " + biomeTab.count(BiomeTab.Filter.WAITING)
                + "   ·   Linked " + biomeTab.count(BiomeTab.Filter.LINKED);
        graphics.drawString(font, fit(counts, panelWidth - 28), left + 14, height - 25, SoulLinkColors.TEXT, false);
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (biomeTab.mouseScrolled(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (biomeTab.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (biomeTab.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        biomeTab.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!search.isFocused() && biomeTab.keyPressed(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

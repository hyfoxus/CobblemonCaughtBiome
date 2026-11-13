package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.hyfoxus.cobblemoncaughtbiome.BiomeNames;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;
import org.lwjgl.glfw.GLFW;

public final class BiomeJournalScreen extends Screen {
    private static final int CARD_HEIGHT = 108;
    private static final int GAP = 8;
    private List<Row> rows;
    private final List<Row> filtered = new ArrayList<>();
    private final List<PortraitPair> portraits = new ArrayList<>();
    private CompoundTag header;
    private Filter filter;
    private EditBox search;
    private String query = "";
    private int left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight;
    private boolean compact;
    private double scroll;
    private boolean draggingScrollbar;
    private int initialPage;
    private int refreshTicks;

    public BiomeJournalScreen(CompoundTag header, List<CompoundTag> rows) {
        super(Component.literal("Soul Link · Biome Journal"));
        this.header = header.copy();
        this.rows = parseRows(rows);
        this.filter = Filter.parse(header.getString("filter"));
        this.initialPage = Math.max(1, header.getInt("page"));
    }

    void update(CompoundTag header, List<CompoundTag> rows) {
        this.header = header.copy();
        this.rows = parseRows(rows);
        refreshTicks = 0;
        applyFilters();
    }

    private static List<Row> parseRows(List<CompoundTag> rows) {
        return rows.stream().map(tag -> new Row(tag.getString("biome"), tag.getString("theme"),
                tag.contains("first", Tag.TAG_COMPOUND) ? tag.getCompound("first") : null,
                tag.contains("second", Tag.TAG_COMPOUND) ? tag.getCompound("second") : null)).toList();
    }

    @Override
    protected void init() {
        left = Math.max(4, (width - Math.min(700, width - 16)) / 2);
        panelWidth = width - left * 2;
        compact = height < 225;
        contentTop = compact ? 88 : 108;
        contentBottom = Math.max(contentTop + 32, height - (compact ? 23 : 34));
        cardHeight = compact ? 72 : CARD_HEIGHT;
        columns = panelWidth >= 490 ? 2 : 1;
        cardWidth = (panelWidth - 28 - GAP * (columns - 1)) / columns;
        search = new EditBox(font, left + 18, compact ? 36 : 55,
                Math.max(30, panelWidth - 156), 18, Component.literal("Search biomes"));
        search.setBordered(false);
        search.setTextColor(SoulLinkColors.TEXT);
        search.setHint(Component.literal("Search biomes…"));
        search.setMaxLength(100);
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll = 0; applyFilters(); });
        addRenderableWidget(search);
        addRenderableWidget(new JournalButton(left + panelWidth - 128, compact ? 31 : 50, 66, Component.literal("Refresh"), false,
                button -> refresh()));
        addRenderableWidget(new JournalButton(left + panelWidth - 58, compact ? 31 : 50, 44, Component.literal("Close"), false,
                button -> onClose()));
        int tabWidth = (panelWidth - 28 - GAP * 3) / 4;
        for (Filter value : Filter.values()) {
            addRenderableWidget(new JournalButton(left + 14 + value.ordinal() * (tabWidth + GAP), compact ? 59 : 79, tabWidth,
                    Component.literal(value.label), filter == value, button -> {
                        filter = value;
                        scroll = 0;
                        rebuildWidgets();
                    }));
        }
        applyFilters();
        if (initialPage > 1) {
            scroll = Math.min(maxScroll(), ((long) initialPage - 1) * 10 / columns * (cardHeight + GAP));
            initialPage = 1;
            positionPortraits();
        }
    }

    private void applyFilters() {
        if (search == null) return;
        if (getFocused() instanceof PokemonPortrait) setFocused(search);
        portraits.forEach(pair -> { removeWidget(pair.first()); removeWidget(pair.second()); });
        portraits.clear();
        filtered.clear();
        String needle = query.toLowerCase(Locale.ROOT).strip();
        rows.stream().filter(row -> filter == Filter.ALL || row.status() == filter)
                .filter(row -> row.name().getString().toLowerCase(Locale.ROOT).contains(needle)
                        || row.id().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(row -> row.name().getString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(filtered::add);
        for (Row row : filtered) {
            PokemonPortrait first = new PokemonPortrait(row.first(), header.getString("firstName"));
            PokemonPortrait second = new PokemonPortrait(row.second(), header.getString("secondName"));
            addRenderableWidget(first);
            addRenderableWidget(second);
            portraits.add(new PortraitPair(first, second));
        }
        scroll = Math.min(scroll, maxScroll());
        positionPortraits();
    }

    private void positionPortraits() {
        for (int i = 0; i < portraits.size(); i++) {
            int x = left + 14 + i % columns * (cardWidth + GAP);
            int y = contentTop + i / columns * (cardHeight + GAP) - (int) scroll;
            PortraitPair pair = portraits.get(i);
            int size = compact ? 30 : 42;
            int portraitY = compact ? 32 : 37;
            pair.first().setWidth(size);
            pair.second().setWidth(size);
            pair.first().setHeight(size);
            pair.second().setHeight(size);
            pair.first().setX(x + cardWidth / 4 - size / 2);
            pair.second().setX(x + cardWidth * 3 / 4 - size / 2);
            pair.first().setY(y + portraitY);
            pair.second().setY(y + portraitY);
            boolean visible = y + portraitY >= contentTop && y + portraitY + size <= contentBottom;
            pair.first().visible = visible;
            pair.second().visible = visible;
        }
    }

    private int maxScroll() {
        return Math.max(0, ((filtered.size() + columns - 1) / columns) * (cardHeight + GAP)
                - GAP - (contentBottom - contentTop));
    }

    private void refresh() {
        if (minecraft != null && minecraft.getConnection() != null && refreshTicks == 0) {
            refreshTicks = 100;
            minecraft.getConnection().sendCommand("soullink biomes " + filter.name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void tick() {
        if (refreshTicks > 0) refreshTicks--;
        portraits.forEach(pair -> { pair.first().tick(); pair.second().tick(); });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD9080D14);
        graphics.fill(left, 8, left + panelWidth, height - 8, 0xF0121C27);
        graphics.renderOutline(left, 8, panelWidth, height - 16, 0xFF34434F);
        graphics.fill(left, 8, left + panelWidth, 10, 0xFF000000 | SoulLinkColors.TEAL);
        graphics.drawString(font, fit("SOUL LINK  /  BIOME JOURNAL", panelWidth - 28), left + 14,
                compact ? 12 : 18, SoulLinkColors.TEAL, false);
        String names = header.getString("firstName") + "  ↔  " + header.getString("secondName")
                + (header.getBoolean("paused") ? "  ·  Paused" : "  ·  Active");
        graphics.drawString(font, fit(names, panelWidth - 28), left + 14, compact ? 24 : 33, SoulLinkColors.TEXT, false);
        graphics.fill(left + 14, compact ? 31 : 50, left + panelWidth - 134,
                compact ? 51 : 70, 0xFF0D1721);
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < filtered.size(); i++) {
            int y = contentTop + i / columns * (cardHeight + GAP) - (int) scroll;
            if (y + cardHeight < contentTop || y > contentBottom) continue;
            renderCard(graphics, filtered.get(i), left + 14 + i % columns * (cardWidth + GAP), y);
        }
        graphics.disableScissor();
        drawScrollbar(graphics);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, fit("No biomes match your search or filter.", panelWidth - 28), width / 2,
                    contentTop + 14, SoulLinkColors.TEXT);
        }
        String counts = "Available " + count(Filter.AVAILABLE) + "   ·   Waiting " + count(Filter.WAITING)
                + "   ·   Linked " + count(Filter.LINKED);
        graphics.drawString(font, fit(counts, panelWidth - 28), left + 14, height - 25, SoulLinkColors.TEXT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, Row row, int x, int y) {
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xFF34515A);
        BiomeBackdrop.draw(graphics, row.theme(), x + 1, y + 1, cardWidth - 2, 29);
        graphics.fill(x + 1, y + 1, x + cardWidth - 1, y + 30, 0x9914212A);
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF82A3AA);
        graphics.drawString(font, fit(row.name().getString(), cardWidth - 16), x + 8, y + 7, 0xFFF7FBF8, true);
        graphics.drawString(font, row.status().label, x + 8, y + 19, row.status().color, true);
        graphics.drawCenteredString(font, row.status() == Filter.LINKED ? "↔" : "·", x + cardWidth / 2,
                y + (compact ? 45 : 54),
                row.status().color);
        if (compact) return;
        graphics.drawCenteredString(font, fit(header.getString("firstName"), cardWidth / 2 - 12),
                x + cardWidth / 4, y + 85, SoulLinkColors.TEXT);
        graphics.drawCenteredString(font, fit(header.getString("secondName"), cardWidth / 2 - 12),
                x + cardWidth * 3 / 4, y + 85, SoulLinkColors.TEXT);
    }

    private long count(Filter state) { return rows.stream().filter(row -> row.status() == state).count(); }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) return;
        int x = left + panelWidth - 7;
        int track = contentBottom - contentTop;
        int thumb = Math.max(16, track * track / (track + maxScroll()));
        int y = contentTop + (int) ((track - thumb) * scroll / maxScroll());
        graphics.fill(x, contentTop, x + 2, contentBottom, 0xFF293A46);
        graphics.fill(x, y, x + 2, y + thumb, 0xFF000000 | SoulLinkColors.TEAL);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= contentTop && mouseY <= contentBottom && mouseX >= left && mouseX <= left + panelWidth) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * 28));
            positionPortraits();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= left + panelWidth - 12
                && mouseX <= left + panelWidth && mouseY >= contentTop && mouseY <= contentBottom) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragScrollbar(double mouseY) {
        scroll = Math.max(0, Math.min(maxScroll(), (mouseY - contentTop) / (contentBottom - contentTop) * maxScroll()));
        positionPortraits();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) { dragScrollbar(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!search.isFocused()) {
            int direction = key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN ? 1
                    : key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP ? -1 : 0;
            if (direction != 0) {
                scroll = Math.max(0, Math.min(maxScroll(), scroll + direction * (cardHeight + GAP)));
                positionPortraits();
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record PortraitPair(PokemonPortrait first, PokemonPortrait second) { }

    private record Row(String id, String theme, CompoundTag first, CompoundTag second) {
        Component name() { return BiomeNames.display(id); }
        Filter status() { return first != null && second != null ? Filter.LINKED
                : first != null || second != null ? Filter.WAITING : Filter.AVAILABLE; }
    }

    private enum Filter {
        ALL("All", SoulLinkColors.TEXT), AVAILABLE("Available", SoulLinkColors.TEXT),
        WAITING("Waiting", SoulLinkColors.GOLD), LINKED("Linked", SoulLinkColors.TEAL);
        final String label;
        final int color;
        Filter(String label, int color) { this.label = label; this.color = color; }
        static Filter parse(String name) {
            try { return valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { return ALL; }
        }
    }
}

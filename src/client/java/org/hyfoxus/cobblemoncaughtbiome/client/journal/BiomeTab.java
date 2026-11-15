package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.hyfoxus.cobblemoncaughtbiome.BiomeNames;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;
import org.lwjgl.glfw.GLFW;

final class BiomeTab {
    private static final int GAP = 8;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_LABEL_OFFSET = 18;
    private static final int PORTRAIT_PAD = 3;

    private final Consumer<AbstractWidget> addWidget;
    private final Consumer<AbstractWidget> removeWidget;
    private final Runnable clearPortraitFocus;
    private Font font;
    private List<Row> rows = List.of();
    private final List<Row> filtered = new ArrayList<>();
    private final List<PortraitPair> portraits = new ArrayList<>();
    private CompoundTag header = new CompoundTag();
    private Filter filter = Filter.ALL;
    private String query = "";
    private int left, panelWidth, contentTop, contentBottom, columns, cardWidth, cardHeight;
    private boolean compact;
    private double scroll;
    private boolean draggingScrollbar;

    BiomeTab(Consumer<AbstractWidget> addWidget, Consumer<AbstractWidget> removeWidget, Runnable clearPortraitFocus) {
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.clearPortraitFocus = clearPortraitFocus;
    }

    void setFont(Font font) { this.font = font; }

    void setLayout(int left, int panelWidth, int contentTop, int contentBottom, int columns, int cardWidth,
            int cardHeight, boolean compact) {
        this.left = left;
        this.panelWidth = panelWidth;
        this.contentTop = contentTop;
        this.contentBottom = contentBottom;
        this.columns = columns;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.compact = compact;
    }

    void setHeader(CompoundTag header) {
        this.header = header;
        this.filter = normalize(filter);
    }

    void setRows(List<CompoundTag> rows) { this.rows = parseRows(rows, solo()); }

    boolean solo() { return header.getBoolean("solo"); }

    /** The filter buttons for this run: All / Available / Caught for solo runs, today's four otherwise. */
    List<Filter> filters() {
        return solo() ? List.of(Filter.ALL, Filter.AVAILABLE, Filter.CAUGHT)
                : List.of(Filter.ALL, Filter.AVAILABLE, Filter.WAITING, Filter.LINKED);
    }

    private Filter normalize(Filter value) { return filters().contains(value) ? value : Filter.ALL; }

    Filter filter() { return filter; }

    void setFilter(Filter filter) { this.filter = normalize(filter); }

    void selectFilter(Filter filter) {
        this.filter = filter;
        scroll = 0;
    }

    String query() { return query; }

    void setQuery(String query) {
        this.query = query;
        scroll = 0;
    }

    private static List<Row> parseRows(List<CompoundTag> rows, boolean solo) {
        return rows.stream().map(tag -> new Row(tag.getString("biome"), tag.getString("theme"),
                tag.contains("first", Tag.TAG_COMPOUND) ? tag.getCompound("first") : null,
                !solo && tag.contains("second", Tag.TAG_COMPOUND) ? tag.getCompound("second") : null, solo)).toList();
    }

    void applyFilters() {
        clearPortraitFocus.run();
        portraits.forEach(pair -> {
            removeWidget.accept(pair.first());
            if (pair.second() != null) removeWidget.accept(pair.second());
        });
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
            addWidget.accept(first);
            PokemonPortrait second = null;
            if (!row.solo()) {
                second = new PokemonPortrait(row.second(), header.getString("secondName"));
                addWidget.accept(second);
            }
            portraits.add(new PortraitPair(first, second));
        }
        scroll = Math.min(scroll, maxScroll());
        positionPortraits();
    }

    void scrollToPage(int page) {
        scroll = Math.min(maxScroll(), ((long) page - 1) * 10 / columns * (cardHeight + GAP));
        positionPortraits();
    }

    private int portraitBandTop() {
        return HEADER_HEIGHT + PORTRAIT_PAD;
    }

    private int portraitBandHeight() {
        int bottom = cardHeight - (compact ? PORTRAIT_PAD : FOOTER_LABEL_OFFSET + PORTRAIT_PAD);
        return bottom - portraitBandTop();
    }

    private int portraitSize() {
        return Math.max(24, Math.min(portraitBandHeight(), cardWidth / 2 - 8));
    }

    private void positionPortraits() {
        int size = portraitSize();
        int bandTop = portraitBandTop();
        int portraitY = bandTop + (portraitBandHeight() - size) / 2;
        for (int i = 0; i < portraits.size(); i++) {
            int x = left + 14 + i % columns * (cardWidth + GAP);
            int y = contentTop + i / columns * (cardHeight + GAP) - (int) scroll;
            PortraitPair pair = portraits.get(i);
            boolean visible = y + portraitY >= contentTop && y + portraitY + size <= contentBottom;
            pair.first().setWidth(size);
            pair.first().setHeight(size);
            pair.first().setY(y + portraitY);
            pair.first().visible = visible;
            if (pair.second() == null) {
                pair.first().setX(x + cardWidth / 2 - size / 2);
                continue;
            }
            pair.first().setX(x + cardWidth / 4 - size / 2);
            pair.second().setWidth(size);
            pair.second().setHeight(size);
            pair.second().setX(x + cardWidth * 3 / 4 - size / 2);
            pair.second().setY(y + portraitY);
            pair.second().visible = visible;
        }
    }

    private int maxScroll() {
        return Math.max(0, ((filtered.size() + columns - 1) / columns) * (cardHeight + GAP)
                - GAP - (contentBottom - contentTop));
    }

    void tick() {
        portraits.forEach(pair -> {
            pair.first().tick();
            if (pair.second() != null) pair.second().tick();
        });
    }

    boolean isEmpty() { return filtered.isEmpty(); }

    long count(Filter state) { return rows.stream().filter(row -> row.status() == state).count(); }

    void renderCardBackgrounds(GuiGraphics graphics) {
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < filtered.size(); i++) {
            int y = contentTop + i / columns * (cardHeight + GAP) - (int) scroll;
            if (y + cardHeight < contentTop || y > contentBottom) continue;
            renderCardBackground(graphics, filtered.get(i), left + 14 + i % columns * (cardWidth + GAP), y);
        }
        graphics.disableScissor();
    }

    void renderCardLabels(GuiGraphics graphics) {
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < filtered.size(); i++) {
            int y = contentTop + i / columns * (cardHeight + GAP) - (int) scroll;
            if (y + cardHeight < contentTop || y > contentBottom) continue;
            renderCardText(graphics, filtered.get(i), left + 14 + i % columns * (cardWidth + GAP), y);
        }
        graphics.disableScissor();
    }

    private void renderCardBackground(GuiGraphics graphics, Row row, int x, int y) {
        graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xFF34515A);
        BiomeBackdrop.draw(graphics, row.theme(), x + 1, y + 1, cardWidth - 2, 29);
        graphics.fill(x + 1, y + 1, x + cardWidth - 1, y + 13, 0x9914212A);
        graphics.fill(x + 1, y + 13, x + cardWidth - 1, y + 22, 0x4014212A);
    }

    private void renderCardText(GuiGraphics graphics, Row row, int x, int y) {
        graphics.renderOutline(x, y, cardWidth, cardHeight, 0xFF82A3AA);
        graphics.drawString(font, fit(row.name().getString(), cardWidth - 16), x + 8, y + 7, 0xFFF7FBF8, true);
        graphics.drawString(font, row.status().label, x + 8, y + 19, row.status().color, true);
        if (!row.solo()) {
            graphics.drawCenteredString(font, row.status() == Filter.LINKED ? "↔" : "·", x + cardWidth / 2,
                    y + portraitBandTop() + portraitBandHeight() / 2 - 4,
                    row.status().color);
        }
        if (compact) return;
        int labelY = y + cardHeight - FOOTER_LABEL_OFFSET;
        if (row.solo()) {
            graphics.drawCenteredString(font, fit(header.getString("firstName"), cardWidth - 16),
                    x + cardWidth / 2, labelY, SoulLinkColors.TEXT);
            return;
        }
        graphics.drawCenteredString(font, fit(header.getString("firstName"), cardWidth / 2 - 12),
                x + cardWidth / 4, labelY, SoulLinkColors.TEXT);
        graphics.drawCenteredString(font, fit(header.getString("secondName"), cardWidth / 2 - 12),
                x + cardWidth * 3 / 4, labelY, SoulLinkColors.TEXT);
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) return;
        int x = left + panelWidth - 7;
        int track = contentBottom - contentTop;
        int thumb = Math.max(16, track * track / (track + maxScroll()));
        int y = contentTop + (int) ((track - thumb) * scroll / maxScroll());
        graphics.fill(x, contentTop, x + 2, contentBottom, 0xFF293A46);
        graphics.fill(x, y, x + 2, y + thumb, 0xFF000000 | SoulLinkColors.TEAL);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseY >= contentTop && mouseY <= contentBottom && mouseX >= left && mouseX <= left + panelWidth) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * 28));
            positionPortraits();
            return true;
        }
        return false;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= left + panelWidth - 12
                && mouseX <= left + panelWidth && mouseY >= contentTop && mouseY <= contentBottom) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }
        return false;
    }

    private void dragScrollbar(double mouseY) {
        scroll = Math.max(0, Math.min(maxScroll(), (mouseY - contentTop) / (contentBottom - contentTop) * maxScroll()));
        positionPortraits();
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingScrollbar) { dragScrollbar(mouseY); return true; }
        return false;
    }

    void mouseReleased() { draggingScrollbar = false; }

    boolean keyPressed(int key) {
        int direction = key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN ? 1
                : key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP ? -1 : 0;
        if (direction != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + direction * (cardHeight + GAP)));
            positionPortraits();
            return true;
        }
        return false;
    }

    /** {@code second} is null for solo-run cards. */
    private record PortraitPair(PokemonPortrait first, PokemonPortrait second) { }

    private record Row(String id, String theme, CompoundTag first, CompoundTag second, boolean solo) {
        Component name() { return BiomeNames.display(id); }
        Filter status() {
            if (solo) return first != null ? Filter.CAUGHT : Filter.AVAILABLE;
            return first != null && second != null ? Filter.LINKED
                    : first != null || second != null ? Filter.WAITING : Filter.AVAILABLE;
        }
    }

    enum Filter {
        ALL("All", SoulLinkColors.TEXT), AVAILABLE("Available", SoulLinkColors.TEXT),
        WAITING("Waiting", SoulLinkColors.GOLD), LINKED("Linked", SoulLinkColors.TEAL),
        CAUGHT("Caught", SoulLinkColors.TEAL);
        final String label;
        final int color;
        Filter(String label, int color) { this.label = label; this.color = color; }
        static Filter parse(String name) {
            try { return valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { return ALL; }
        }
    }
}

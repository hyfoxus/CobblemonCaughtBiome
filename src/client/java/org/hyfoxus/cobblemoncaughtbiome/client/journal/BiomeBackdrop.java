package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import net.minecraft.client.gui.GuiGraphics;

final class BiomeBackdrop {
    private BiomeBackdrop() { }

    static void draw(GuiGraphics graphics, String theme, int x, int y, int width, int height) {
        int sky = switch (theme) {
            case "ocean" -> 0xFF5B95A9;
            case "forest" -> 0xFF5F947A;
            case "desert" -> 0xFFC3A773;
            case "snow" -> 0xFF9BB5C5;
            case "nether" -> 0xFF986B72;
            case "end" -> 0xFF8F82AB;
            default -> 0xFF79A38A;
        };
        int bands = 3;
        for (int band = 0; band < bands; band++) {
            graphics.fill(x, y + height * band / bands, x + width, y + height * (band + 1) / bands,
                    lerpColor(sky, 0xFF35545C, band / (float) (bands - 1)));
        }
        for (int i = 0; i < width; i += 4) {
            double wave = Math.sin(i * 0.045) * 3 + Math.cos(i * 0.08) * 2;
            int horizon = y + height - 9 + (int) wave;
            int color = switch (theme) {
                case "ocean" -> 0xFF437E92;
                case "desert" -> 0xFFA68C5E;
                case "snow" -> 0xFF7E9AAA;
                case "nether" -> 0xFF7D5059;
                case "end" -> 0xFF776A91;
                default -> 0xFF527965;
            };
            graphics.fill(x + i, horizon, x + Math.min(width, i + 4), y + height, color);
        }
        if (theme.equals("forest")) {
            for (int i = 10; i < width; i += 22) {
                int treeHeight = 12 + i % 9;
                for (int row = 0; row < treeHeight; row += 2) {
                    int half = 1 + row / 3;
                    graphics.fill(x + i - half, y + height - treeHeight + row,
                            x + i + half, y + height - treeHeight + row + 2, 0xFF416452);
                }
            }
        } else if (theme.equals("ocean")) {
            for (int i = 5; i < width - 10; i += 32)
                graphics.fill(x + i, y + height - 5, x + i + 14, y + height - 4, 0xFF507B85);
        } else if (theme.equals("snow")) {
            for (int i = 15; i < width - 12; i += 50) {
                for (int row = 0; row < 14; row++) {
                    graphics.fill(x + i - row, y + 8 + row, x + i + row + 1, y + 9 + row,
                            row < 5 ? 0xFF9AABB4 : 0xFF4E6573);
                }
            }
        } else if (theme.equals("end")) {
            for (int i = 9; i < width - 3; i += 27)
                graphics.fill(x + i, y + 5 + i % 10, x + i + 2, y + 7 + i % 10, 0xFF9685AE);
        }
        graphics.fill(x, y, x + width, y + height, 0x110D141C);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

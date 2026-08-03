package com.cobblemonpokerogue.bridge.presentation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rasterizes one leaderboard row onto a vanilla map canvas (128×128 map-palette bytes): the
 * run's team as PokéRogue's own party-icon PNGs, laid out 3×2 inside a bubble whose border
 * color encodes the game mode. Icons are read from the self-hosted frontend's
 * {@code images/pokemon/icons/<gen>/<speciesId>.png} tree (config {@code spriteDir}); a
 * regional-form id with no icon of its own falls back to the base species ({@code id % 2000}).
 *
 * <p>Transparent pixels stay map-transparent, so the wall behind the (invisible) item frame
 * shows through and only the bubble reads as a floating card.
 */
final class MapPainter {

    private static final Logger LOG = LoggerFactory.getLogger("pokerogue-bridge");
    static final int SIZE = 128;
    /** Maps tiled side by side per row — the painting is TILES×1 blocks on the wall. */
    static final int TILES = 3;
    private static final int W = TILES * SIZE;

    // Bubble geometry: one wide card with all 6 icons on a single line.
    private static final int BUBBLE_X0 = 4, BUBBLE_X1 = W - 4, BUBBLE_Y0 = 10, BUBBLE_Y1 = 118;
    private static final int BORDER = 5;
    private static final int BUBBLE_FILL = 0x2B2B33;

    private final Path spriteDir;
    private final Map<Integer, BufferedImage> iconCache = new HashMap<>();
    /** Species ids that already warned about a missing icon, so the log stays quiet. */
    private final java.util.Set<Integer> warnedMissing = new java.util.HashSet<>();

    // Map palette lookup, built once: packed byte + RGB per (base color × brightness).
    private static byte[] paletteBytes;
    private static int[] paletteRgb;

    MapPainter(Path spriteDir) {
        this.spriteDir = spriteDir;
    }

    /** Mode → bubble/border color, shared by the leaderboard rows and the dream cards. */
    public static int borderRgb(String mode) {
        return switch (mode) {
            case "classic" -> 0xFFC94A;
            case "challenge" -> 0xE04040;
            case "endless" -> 0x8A4FD0;
            case "spliced_endless" -> 0xB05FD0;
            case "daily" -> 0x4FBF6A;
            default -> 0x9AA0A6;
        };
    }

    /**
     * Renders a row — up to 6 species icons on one line in the mode-colored bubble — and
     * returns it as {@link #TILES} map-sized byte tiles, viewer-left to viewer-right.
     */
    byte[][] paintRowTiles(List<Integer> speciesIds, int borderRgb) {
        BufferedImage canvas = new BufferedImage(W, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(new java.awt.Color(borderRgb));
            g.fillRoundRect(BUBBLE_X0, BUBBLE_Y0, BUBBLE_X1 - BUBBLE_X0, BUBBLE_Y1 - BUBBLE_Y0, 28, 28);
            g.setColor(new java.awt.Color(BUBBLE_FILL));
            g.fillRoundRect(BUBBLE_X0 + BORDER, BUBBLE_Y0 + BORDER,
                    BUBBLE_X1 - BUBBLE_X0 - 2 * BORDER, BUBBLE_Y1 - BUBBLE_Y0 - 2 * BORDER, 20, 20);

            int cellW = (BUBBLE_X1 - BUBBLE_X0 - 2 * BORDER) / 6;
            int cellH = BUBBLE_Y1 - BUBBLE_Y0 - 2 * BORDER;
            for (int i = 0; i < Math.min(6, speciesIds.size()); i++) {
                BufferedImage icon = icon(speciesIds.get(i));
                if (icon == null) {
                    continue;
                }
                int cx = BUBBLE_X0 + BORDER + i * cellW;
                int cy = BUBBLE_Y0 + BORDER;
                // Fit preserving aspect; cap the upscale so the pixel art stays chunky-clean.
                double fit = Math.min((double) (cellW - 4) / icon.getWidth(),
                        (double) (cellH - 4) / icon.getHeight());
                fit = Math.min(fit, 3.0);
                int w = Math.max(1, (int) (icon.getWidth() * fit));
                int h = Math.max(1, (int) (icon.getHeight() * fit));
                g.drawImage(icon, cx + (cellW - w) / 2, cy + (cellH - h) / 2, w, h, null);
            }
        } finally {
            g.dispose();
        }
        byte[][] tiles = new byte[TILES][];
        for (int t = 0; t < TILES; t++) {
            tiles[t] = quantize(canvas, t * SIZE);
        }
        return tiles;
    }

    @Nullable
    private BufferedImage icon(int speciesId) {
        BufferedImage cached = iconCache.get(speciesId);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = loadIcon(speciesId);
        if (img == null && speciesId > 2000) {
            img = loadIcon(speciesId % 2000); // regional form → base species
        }
        if (img == null) {
            if (warnedMissing.add(speciesId)) {
                LOG.warn("dream board: no party icon for species {} under {}", speciesId, spriteDir);
            }
            return null;
        }
        iconCache.put(speciesId, img);
        return img;
    }

    @Nullable
    private BufferedImage loadIcon(int speciesId) {
        for (int gen = 1; gen <= 9; gen++) {
            Path p = spriteDir.resolve(gen + "/" + speciesId + ".png");
            if (Files.isRegularFile(p)) {
                try {
                    return ImageIO.read(p.toFile());
                } catch (IOException e) {
                    LOG.warn("dream board: unreadable icon {}", p, e);
                    return null;
                }
            }
        }
        return null;
    }

    // ---- map-palette quantization --------------------------------------------------------

    private static byte[] quantize(BufferedImage img, int xOffset) {
        buildPalette();
        byte[] out = new byte[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = img.getRGB(xOffset + x, y);
                if ((argb >>> 24) < 128) {
                    continue; // transparent stays 0 (map NONE)
                }
                out[x + y * SIZE] = nearest(argb & 0xFFFFFF);
            }
        }
        return out;
    }

    private static byte nearest(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int bestI = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < paletteRgb.length; i++) {
            int pr = paletteRgb[i] >> 16 & 255, pg = paletteRgb[i] >> 8 & 255, pb = paletteRgb[i] & 255;
            long d = (long) (pr - r) * (pr - r) + (long) (pg - g) * (pg - g) + (long) (pb - b) * (pb - b);
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        return paletteBytes[bestI];
    }

    private static synchronized void buildPalette() {
        if (paletteBytes != null) {
            return;
        }
        int[] mult = {180, 220, 255, 135};
        List<Byte> bytes = new ArrayList<>();
        List<Integer> rgbs = new ArrayList<>();
        for (int id = 1; id < 64; id++) {
            MapColor color;
            try {
                color = MapColor.byId(id);
            } catch (RuntimeException undefined) {
                continue;
            }
            if (color == null || color.col == 0) {
                continue;
            }
            for (int b = 0; b < 4; b++) {
                int r = (color.col >> 16 & 255) * mult[b] / 255;
                int g = (color.col >> 8 & 255) * mult[b] / 255;
                int bl = (color.col & 255) * mult[b] / 255;
                bytes.add((byte) (id * 4 + b));
                rgbs.add(r << 16 | g << 8 | bl);
            }
        }
        byte[] pb = new byte[bytes.size()];
        int[] pr = new int[rgbs.size()];
        for (int i = 0; i < pb.length; i++) {
            pb[i] = bytes.get(i);
            pr[i] = rgbs.get(i);
        }
        paletteBytes = pb;
        paletteRgb = pr;
    }
}

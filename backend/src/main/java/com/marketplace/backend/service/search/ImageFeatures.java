package com.marketplace.backend.service.search;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * What a picture looks like, without any AI: a colour histogram (4 x 4 x 4 RGB bins, near-white background pixels left
 * out when the product doesn't fill the frame) and a 64-bit difference hash of its shape (dHash on a 9 x 8 greyscale
 * thumbnail). Two pictures of the same kind of product share colours and outline, so the score finds look-alikes.
 */
public record ImageFeatures(float[] histogram, long dhash) {

    public static final int BINS = 64;

    public static ImageFeatures of(BufferedImage img) {
        BufferedImage small = resize(img, 64, 64);
        float[] all = new float[BINS], fg = new float[BINS];
        int fgCount = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int rgb = small.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                boolean background = r > 235 && g > 235 && b > 235;   // product photos sit on white
                vote(all, r, g, b);
                if (!background) {
                    vote(fg, r, g, b);
                    fgCount++;
                }
            }
        }
        float[] h = fgCount > 64 * 64 / 10 ? fg : all;
        normalize(h);
        return new ImageFeatures(h, dhash(img));
    }

    // soft binning: a pixel shares its vote between the (up to 8) nearest bins, so two close shades of a colour
    // never fall into unrelated bins just because they sit on either side of a bin edge
    private static void vote(float[] h, int r, int g, int b) {
        float pr = r / 64f - 0.5f, pg = g / 64f - 0.5f, pb = b / 64f - 0.5f;
        int r0 = (int) Math.floor(pr), g0 = (int) Math.floor(pg), b0 = (int) Math.floor(pb);
        float fr = pr - r0, fg = pg - g0, fb = pb - b0;
        for (int dr = 0; dr <= 1; dr++) {
            for (int dg = 0; dg <= 1; dg++) {
                for (int db = 0; db <= 1; db++) {
                    int ir = clamp(r0 + dr), ig = clamp(g0 + dg), ib = clamp(b0 + db);
                    float w = (dr == 1 ? fr : 1 - fr) * (dg == 1 ? fg : 1 - fg) * (db == 1 ? fb : 1 - fb);
                    h[ir * 16 + ig * 4 + ib] += w;
                }
            }
        }
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(3, i));
    }

    // 9x8 greyscale: each bit = is this pixel brighter than its right neighbour
    static long dhash(BufferedImage img) {
        BufferedImage g = resize(img, 9, 8);
        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash = (hash << 1) | (luma(g.getRGB(x, y)) > luma(g.getRGB(x + 1, y)) ? 1 : 0);
            }
        }
        return hash;
    }

    /** 0..1: histogram intersection (colours) weighted 60%, hash closeness (shape) 40%. */
    public double similarity(ImageFeatures o) {
        double inter = 0;
        for (int i = 0; i < BINS; i++) inter += Math.min(histogram[i], o.histogram[i]);
        double shape = 1 - Long.bitCount(dhash ^ o.dhash) / 64.0;
        return 0.6 * inter + 0.4 * shape;
    }

    public String histogramText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BINS; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.4f", histogram[i]));
        }
        return sb.toString();
    }

    public static ImageFeatures parse(String histogram, long dhash) {
        String[] parts = histogram.split(",");
        float[] h = new float[BINS];
        for (int i = 0; i < BINS && i < parts.length; i++) h[i] = Float.parseFloat(parts[i]);
        return new ImageFeatures(h, dhash);
    }

    private static void normalize(float[] h) {
        float sum = 0;
        for (float v : h) sum += v;
        if (sum > 0) for (int i = 0; i < h.length; i++) h[i] /= sum;
    }

    private static int luma(int rgb) {
        return (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);   // transparent PNGs count as white background
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}

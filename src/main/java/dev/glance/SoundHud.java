package dev.glance;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws the sound radar, the crosshair ring and the sound list. Also reused by the menu preview. */
public final class SoundHud {
	/** One sound, already converted to screen terms. rel: 0 = ahead, +90 = right, -90 = left. */
	public record Marker(float rel, float dist, float dy, String label, SoundType type,
						 float life, long ageMs, int count, double x, double y, double z) {
	}

	public static final int[] ACCENTS = {0xFF2DD4F0, 0xFFA78BFA, 0xFF84E65A, 0xFFFF6B9A, 0xFFFFB02E};
	public static final String[] ACCENT_NAMES = {"Cyan", "Violet", "Lime", "Rose", "Amber"};
	public static final String[] CORNER_NAMES = {"Top left", "Top right", "Bottom left", "Bottom right"};

	private SoundHud() {
	}

	public static int accent(GlanceConfig cfg) {
		return ACCENTS[Math.floorMod(cfg.accent, ACCENTS.length)];
	}

	/** Scales the alpha channel of an ARGB colour by a factor from 0 to 1. */
	public static int alpha(int argb, float factor) {
		int base = argb >>> 24;
		int a = Math.round(base * Mth.clamp(factor, 0.0f, 1.0f));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpColor(int from, int to, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Rectangle with slightly rounded corners. */
	public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + h, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
	}

	public static void ring(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
		if (radius < 1) {
			return;
		}
		int points = Math.max(16, radius * 5);
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0 * i / points;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			g.fill(px, py, px + 1, py + 1, color);
		}
	}

	public static int[] corner(int corner, int screenW, int screenH, int w, int h) {
		boolean right = corner == 1 || corner == 3;
		boolean bottom = corner == 2 || corner == 3;
		int x = right ? screenW - w - 4 : 4;
		int y = bottom ? screenH - h - 4 - 24 : 4;
		return new int[]{x, y};
	}

	// ------------------------------------------------------------------ in-game HUD element

	public static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		GlanceConfig cfg = GlanceConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.soundEnabled || mc.player == null || mc.options.hideGui) {
			return;
		}
		if (!cfg.showRadar && !cfg.showRing && !cfg.showList && !cfg.showBoxes) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		List<Marker> markers = SoundTracker.markers(partialTick);
		int screenW = g.guiWidth();
		int screenH = g.guiHeight();
		long now = Util.getMillis();

		if (cfg.showBoxes) {
			SoundBoxes.draw(g, mc.font, markers, cfg, partialTick);
		}
		if (cfg.showRadar) {
			int size = cfg.radarSize;
			int[] pos = corner(cfg.radarCorner, screenW, screenH, size, size);
			drawRadar(g, mc.font, pos[0], pos[1], size, markers, cfg, now, cfg.showLabels);
		}
		if (cfg.showRing) {
			drawRing(g, screenW / 2, screenH / 2, cfg.ringRadius, markers, cfg);
		}
		if (cfg.showList) {
			drawList(g, mc.font, screenW, screenH, markers, cfg);
		}
	}

	// ------------------------------------------------------------------ radar

	public static void drawRadar(GuiGraphicsExtractor g, Font font, int x, int y, int size,
								 List<Marker> markers, GlanceConfig cfg, long now, boolean labels) {
		int accent = accent(cfg);
		int half = size / 2;
		int cx = x + half;
		int cy = y + half;
		int rMax = half - 5;

		roundRect(g, x, y, size, size, (cfg.backgroundAlpha << 24) | 0x0A0C10);
		g.outline(x, y, size, size, alpha(accent, 0.55f));
		ring(g, cx, cy, rMax, alpha(accent, 0.45f));
		ring(g, cx, cy, rMax / 2, alpha(accent, 0.2f));
		g.fill(x + 2, cy, x + size - 2, cy + 1, alpha(accent, 0.15f));
		g.fill(cx, y + 2, cx + 1, y + size - 2, alpha(accent, 0.15f));

		if (cfg.radarSweep) {
			double sweep = (now % 2400L) / 2400.0 * Math.PI * 2.0;
			for (int k = 0; k < 8; k++) {
				double angle = sweep - k * 0.06;
				float strength = 0.35f * (1.0f - k / 8.0f);
				int step = k == 0 ? 1 : 2;
				for (int i = 3; i <= rMax; i += step) {
					int px = cx + (int) Math.round(Math.sin(angle) * i);
					int py = cy - (int) Math.round(Math.cos(angle) * i);
					g.fill(px, py, px + 1, py + 1, alpha(accent, strength));
				}
			}
		}

		// You, facing up.
		g.fill(cx, cy - 4, cx + 1, cy - 3, 0xFFFFFFFF);
		g.fill(cx - 1, cy - 3, cx + 2, cy - 2, 0xFFFFFFFF);
		g.fill(cx - 2, cy - 2, cx + 3, cy - 1, 0xFFFFFFFF);
		g.fill(cx - 1, cy, cx + 2, cy + 1, 0xFFFFFFFF);

		float range = Math.max(1, cfg.detectRange);
		int labelsDrawn = 0;
		for (int i = 0; i < markers.size(); i++) {
			Marker m = markers.get(i);
			float fade = Mth.clamp(m.life() * 2.0f, 0.0f, 1.0f);
			double rad = Math.toRadians(m.rel());
			float d = Math.min(m.dist() / range, 1.0f) * rMax;
			int px = cx + (int) Math.round(Math.sin(rad) * d);
			int py = cy - (int) Math.round(Math.cos(rad) * d);

			int markerSize = 5 + (m.ageMs() < 300 ? 2 : 0) + (m.count() > 3 ? 2 : 0);
			int h = markerSize / 2;
			g.fill(px - h - 1, py - h - 1, px + h + 2, py + h + 2, alpha(0xFF000000, fade * 0.8f));
			g.fill(px - h, py - h, px + h + 1, py + h + 1, alpha(m.type().color, fade));

			if (m.ageMs() < 600) {
				float t = m.ageMs() / 600.0f;
				ring(g, px, py, 3 + (int) (t * 9), alpha(m.type().color, (1.0f - t) * 0.8f));
			}
		}

		if (labels) {
			// Label only the freshest few sounds, newest first.
			for (int i = markers.size() - 1; i >= 0 && labelsDrawn < 3; i--) {
				Marker m = markers.get(i);
				float fade = Mth.clamp(m.life() * 2.0f, 0.0f, 1.0f);
				if (fade < 0.15f) {
					continue;
				}
				double rad = Math.toRadians(m.rel());
				float d = Math.min(m.dist() / range, 1.0f) * rMax;
				int px = cx + (int) Math.round(Math.sin(rad) * d);
				int py = cy - (int) Math.round(Math.cos(rad) * d);
				String text = font.plainSubstrByWidth(m.label(), 72);
				int width = font.width(text);
				int tx = px + 6;
				if (tx + width > x + size - 2) {
					tx = px - 6 - width;
				}
				tx = Math.max(x + 2, tx);
				int ty = Mth.clamp(py - 4, y + 2, y + size - 11);
				g.text(font, text, tx, ty, alpha(0xFFFFFFFF, fade), true);
				labelsDrawn++;
			}
		}
	}

	// ------------------------------------------------------------------ crosshair ring

	public static void drawRing(GuiGraphicsExtractor g, int cx, int cy, int radius,
								List<Marker> markers, GlanceConfig cfg) {
		float range = Math.max(1, cfg.detectRange);
		for (Marker m : markers) {
			float fade = Mth.clamp(m.life() * 2.0f, 0.0f, 1.0f);
			double rad = Math.toRadians(m.rel());
			double dirX = Math.sin(rad);
			double dirY = -Math.cos(rad);
			// Closer sounds draw a longer bar.
			float closeness = 1.0f - Math.min(m.dist() / range, 1.0f);
			int segments = 2 + Math.round(closeness * 4.0f);
			int block = m.type() == SoundType.DANGER ? 4 : 3;
			for (int i = 0; i < segments; i++) {
				int px = cx + (int) Math.round(dirX * (radius + i * 3));
				int py = cy + (int) Math.round(dirY * (radius + i * 3));
				float segFade = fade * (1.0f - i * 0.1f);
				int o = block / 2;
				g.fill(px - o - 1, py - o - 1, px - o + block + 1, py - o + block + 1,
					alpha(0xFF000000, segFade * 0.6f));
				g.fill(px - o, py - o, px - o + block, py - o + block, alpha(m.type().color, segFade));
			}
		}
	}

	// ------------------------------------------------------------------ list

	private static String arrow(float rel) {
		String[] arrows = {"^", "^>", ">", "v>", "v", "<v", "<", "<^"};
		int sector = Math.floorMod(Math.round(rel / 45.0f), 8);
		return arrows[sector];
	}

	public static void drawList(GuiGraphicsExtractor g, Font font, int screenW, int screenH,
								List<Marker> markers, GlanceConfig cfg) {
		if (markers.isEmpty()) {
			return;
		}
		List<Marker> sorted = new ArrayList<>(markers);
		sorted.sort(Comparator.comparingLong(Marker::ageMs));
		int count = Math.min(sorted.size(), cfg.listMax);

		List<String> lines = new ArrayList<>();
		int width = 0;
		for (int i = 0; i < count; i++) {
			Marker m = sorted.get(i);
			String height = m.dy() > 3 ? " +" : (m.dy() < -3 ? " -" : "");
			String repeat = m.count() > 1 ? " x" + m.count() : "";
			String line = arrow(m.rel()) + " " + m.label() + repeat + "  " + Math.round(m.dist()) + "m" + height;
			lines.add(line);
			width = Math.max(width, font.width(line));
		}

		int boxW = width + 16;
		int boxH = count * 11 + 6;
		int[] pos = corner(cfg.listCorner, screenW, screenH, boxW, boxH);
		int x = pos[0];
		int y = pos[1];
		if (cfg.showRadar && cfg.radarCorner == cfg.listCorner) {
			// Stack the list next to the radar instead of on top of it.
			int radarH = cfg.radarSize + 4;
			boolean bottom = cfg.listCorner == 2 || cfg.listCorner == 3;
			y = bottom ? y - radarH : y + radarH;
		}

		roundRect(g, x, y, boxW, boxH, (cfg.backgroundAlpha << 24) | 0x0A0C10);
		for (int i = 0; i < count; i++) {
			Marker m = sorted.get(i);
			float fade = Mth.clamp(m.life() * 2.0f, 0.0f, 1.0f);
			int ly = y + 4 + i * 11;
			g.fill(x + 4, ly + 2, x + 7, ly + 5, alpha(m.type().color, fade));
			g.text(font, lines.get(i), x + 11, ly, alpha(0xFFFFFFFF, Math.max(fade, 0.2f)), false);
		}
	}
}

package dev.glance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a coloured square on screen around every detected sound so you can find where it came from.
 *
 * The sound's world position is projected onto the screen with plain camera maths, so squares show
 * through walls and need no custom render pipeline. Red is close (and always used for danger sounds),
 * orange is mid range, yellow is far. Sounds that are off-screen or behind you get a small marker on
 * the screen edge pointing the way. Accurate in first person only; the base FOV setting is used, so
 * squares can drift a little while sprinting or under FOV-changing effects.
 */
public final class SoundBoxes {
	public static final int YELLOW = 0xFFFFD60A;
	public static final int ORANGE = 0xFFFF8F1F;
	public static final int RED = 0xFFFF3B30;
	private static final int BLACK = 0xFF000000;

	private SoundBoxes() {
	}

	public static int color(SoundType type, double distance, GlanceConfig cfg) {
		if (type == SoundType.DANGER || distance <= cfg.boxRedDist) {
			return RED;
		}
		if (distance <= cfg.boxOrangeDist) {
			return ORANGE;
		}
		return YELLOW;
	}

	/** Vertical offset so the square sits on the middle of whatever made the sound. */
	private static double centerOffset(SoundType type) {
		return switch (type) {
			case PLAYER, HOSTILE, PASSIVE, FOOTSTEPS -> 0.9;
			case DANGER -> 0.5;
			default -> 0.0;
		};
	}

	public static void draw(GuiGraphicsExtractor g, Font font, List<SoundHud.Marker> markers,
							GlanceConfig cfg, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || markers.isEmpty() || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		if (w <= 0 || h <= 0) {
			return;
		}

		Vec3 eye = player.getEyePosition(partialTick);
		double yaw = Math.toRadians(player.getViewYRot(partialTick));
		double pitch = Math.toRadians(player.getViewXRot(partialTick));
		double sinY = Math.sin(yaw);
		double cosY = Math.cos(yaw);
		double sinP = Math.sin(pitch);
		double cosP = Math.cos(pitch);

		// Camera basis. Minecraft yaw 0 faces +Z (south) and positive pitch looks down.
		double fx = -sinY * cosP;
		double fy = -sinP;
		double fz = cosY * cosP;
		double rx = -cosY;
		double rz = -sinY;
		double ux = -sinY * sinP;
		double uy = cosP;
		double uz = cosY * sinP;

		// The FOV option is the vertical field of view.
		double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = (double) w / (double) h;

		// Far squares first so near ones draw on top.
		List<SoundHud.Marker> sorted = new ArrayList<>(markers);
		sorted.sort(Comparator.comparingDouble(SoundHud.Marker::dist).reversed());

		for (SoundHud.Marker m : sorted) {
			float fade = Mth.clamp(m.life() * 4.0f, 0.0f, 1.0f);
			if (fade <= 0.02f) {
				continue;
			}

			double dx = m.x() - eye.x();
			double dy = m.y() + centerOffset(m.type()) - eye.y();
			double dz = m.z() - eye.z();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

			double xr = dx * rx + dz * rz;
			double yu = dx * ux + dy * uy + dz * uz;
			double zf = dx * fx + dy * fy + dz * fz;
			int color = color(m.type(), dist, cfg);

			boolean onScreen = false;
			int sx = 0;
			int sy = 0;
			int half = 0;
			if (zf > 0.1) {
				double px = w / 2.0 + (xr / zf) / (tanHalf * aspect) * (w / 2.0);
				double py = h / 2.0 - (yu / zf) / tanHalf * (h / 2.0);
				double blockHalf = (cfg.boxSize * 0.5 / zf) / tanHalf * (h / 2.0);
				double halfSize = Math.min(Math.max(cfg.boxMinPx / 2.0, blockHalf), h * 0.45);
				onScreen = px > -halfSize && px < w + halfSize && py > -halfSize && py < h + halfSize;
				sx = (int) Math.round(px);
				sy = (int) Math.round(py);
				half = (int) Math.round(halfSize);
			}

			if (onScreen) {
				drawBox(g, font, m, sx, sy, half, color, fade, dist, w, h, cfg);
			} else if (cfg.boxEdgeMarkers) {
				drawEdge(g, w, h, xr, yu, color, fade);
			}
		}
	}

	private static void drawBox(GuiGraphicsExtractor g, Font font, SoundHud.Marker m, int cx, int cy, int half,
								int color, float fade, double dist, int screenW, int screenH, GlanceConfig cfg) {
		int x = cx - half;
		int y = cy - half;
		int size = half * 2;
		int thickness = cfg.boxThickness;

		g.fill(x, y, x + size, y + size, SoundHud.alpha(color, fade * 0.10f));
		// Dark outline around the coloured one so it stays visible on any background.
		g.outline(x - 1, y - 1, size + 2, size + 2, SoundHud.alpha(BLACK, fade * 0.85f));
		for (int t = 0; t < thickness; t++) {
			g.outline(x + t, y + t, size - t * 2, size - t * 2, SoundHud.alpha(color, fade));
		}

		// Expanding pulse on brand new sounds.
		if (m.ageMs() < 500) {
			float t = m.ageMs() / 500.0f;
			int grow = Math.round(t * 10.0f);
			g.outline(x - grow, y - grow, size + grow * 2, size + grow * 2,
				SoundHud.alpha(color, (1.0f - t) * fade));
		}

		if (cfg.boxLabels) {
			String text = font.plainSubstrByWidth(m.label(), 110) + "  " + Math.round(dist) + "m";
			int tw = font.width(text);
			int tx = Mth.clamp(cx - tw / 2, 3, Math.max(3, screenW - tw - 3));
			int ty = Mth.clamp(y + size + 4, 2, screenH - 12);
			g.fill(tx - 3, ty - 2, tx + tw + 3, ty + 10, SoundHud.alpha(BLACK, fade * 0.6f));
			g.text(font, text, tx, ty, SoundHud.alpha(color, fade), false);
		}
	}

	/** A small square on the screen edge pointing toward a sound that is off-screen or behind you. */
	private static void drawEdge(GuiGraphicsExtractor g, int w, int h, double xr, double yu, int color, float fade) {
		double dx = xr;
		double dy = -yu;
		double len = Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-6) {
			dx = 0.0;
			dy = 1.0;
			len = 1.0;
		}
		dx /= len;
		dy /= len;

		double inset = 16.0;
		double tx = Math.abs(dx) < 1.0e-6 ? Double.MAX_VALUE : (w / 2.0 - inset) / Math.abs(dx);
		double ty = Math.abs(dy) < 1.0e-6 ? Double.MAX_VALUE : (h / 2.0 - inset) / Math.abs(dy);
		double t = Math.min(tx, ty);
		int px = (int) Math.round(w / 2.0 + dx * t);
		int py = (int) Math.round(h / 2.0 + dy * t);

		int s = 5;
		g.fill(px - s - 1, py - s - 1, px + s + 1, py + s + 1, SoundHud.alpha(BLACK, fade * 0.85f));
		g.fill(px - s, py - s, px + s, py + s, SoundHud.alpha(color, fade));
	}
}

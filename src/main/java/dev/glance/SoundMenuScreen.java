package dev.glance;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Glance settings menu: a dark, tabbed panel with animated switches, sliders, per-type filters
 * and a live preview of the radar and ring.
 */
public class SoundMenuScreen extends Screen {
	private static final int PW = 400;
	private static final int PH = 232;
	private static final int HEADER = 26;
	private static final int SIDEBAR = 86;
	private static final int PREVIEW = 122;

	private static final int TEXT = 0xFFE6E9EF;
	private static final int DIM = 0xFF8A91A0;
	private static final int TRACK = 0xFF2A2F3A;

	private static final String[] TAB_NAMES = {"Detector", "Boxes", "Radar", "Ring & List", "Filters", "Info HUD"};

	private final GlanceConfig cfg = GlanceConfig.get();
	private final List<List<Row>> tabs = new ArrayList<>();
	private final long openedAt = Util.getMillis();
	private int tab = 0;
	private Slider dragging;

	public SoundMenuScreen() {
		super(Component.literal("Glance"));
		buildTabs();
	}

	// ------------------------------------------------------------------ layout helpers

	private int left() {
		return (this.width - PW) / 2;
	}

	private int top() {
		float t = Mth.clamp((Util.getMillis() - openedAt) / 160.0f, 0.0f, 1.0f);
		float eased = 1.0f - (1.0f - t) * (1.0f - t);
		return (this.height - PH) / 2 + Math.round((1.0f - eased) * 12.0f);
	}

	private int accent() {
		return SoundHud.accent(cfg);
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ rows

	private abstract class Row {
		final int height;

		Row(int height) {
			this.height = height;
		}

		abstract void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my);

		boolean click(int x, int y, int w, int mx, int my, int button) {
			return false;
		}
	}

	private final class Toggle extends Row {
		private final String label;
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private final int dotColor;
		private float anim = -1.0f;

		Toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
			this(label, 0, getter, setter);
		}

		Toggle(String label, int dotColor, BooleanSupplier getter, Consumer<Boolean> setter) {
			super(22);
			this.label = label;
			this.dotColor = dotColor;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
			boolean on = getter.getAsBoolean();
			if (anim < 0.0f) {
				anim = on ? 1.0f : 0.0f;
			}
			anim += ((on ? 1.0f : 0.0f) - anim) * 0.35f;

			if (inside(mx, my, x, y, w, height)) {
				g.fill(x, y, x + w, y + height, 0x14FFFFFF);
			}
			int textX = x + 6;
			if (dotColor != 0) {
				g.fill(x + 6, y + 8, x + 10, y + 12, dotColor);
				textX += 9;
			}
			g.text(font, label, textX, y + 7, TEXT, false);

			int sx = x + w - 30;
			int sy = y + 6;
			SoundHud.roundRect(g, sx, sy, 24, 10, SoundHud.lerpColor(TRACK, accent(), anim));
			int kx = sx + 1 + Math.round(anim * 14.0f);
			g.fill(kx, sy + 1, kx + 8, sy + 9, 0xFFFFFFFF);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my, int button) {
			if (button == 0 && inside(mx, my, x, y, w, height)) {
				setter.accept(!getter.getAsBoolean());
				cfg.save();
				return true;
			}
			return false;
		}
	}

	private final class Slider extends Row {
		private final String label;
		private final int min;
		private final int max;
		private final int step;
		private final String suffix;
		private final IntSupplier getter;
		private final IntConsumer setter;
		private int trackX;
		private int trackW = 1;

		Slider(String label, int min, int max, int step, String suffix, IntSupplier getter, IntConsumer setter) {
			super(30);
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
			int value = getter.getAsInt();
			trackX = x + 6;
			trackW = w - 12;

			if (inside(mx, my, x, y, w, height) || dragging == this) {
				g.fill(x, y, x + w, y + height, 0x14FFFFFF);
			}
			g.text(font, label, x + 6, y + 4, TEXT, false);
			String shown = value + suffix;
			g.text(font, shown, x + w - 6 - font.width(shown), y + 4, accent(), false);

			float frac = (value - min) / (float) (max - min);
			int ty = y + 20;
			SoundHud.roundRect(g, trackX, ty, trackW, 4, TRACK);
			int filled = Math.round(frac * trackW);
			if (filled > 1) {
				SoundHud.roundRect(g, trackX, ty, filled, 4, accent());
			}
			int kx = trackX + filled - 2;
			g.fill(kx, ty - 3, kx + 5, ty + 7, 0xFFFFFFFF);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my, int button) {
			if (button == 0 && inside(mx, my, x, y + 10, w, height - 10)) {
				dragging = this;
				update(mx);
				return true;
			}
			return false;
		}

		void update(double mouseX) {
			float frac = Mth.clamp((float) ((mouseX - trackX) / trackW), 0.0f, 1.0f);
			int raw = Math.round(frac * (max - min) / (float) step) * step + min;
			setter.accept(Mth.clamp(raw, min, max));
		}
	}

	private final class Choice extends Row {
		private final String label;
		private final String[] options;
		private final IntSupplier getter;
		private final IntConsumer setter;

		Choice(String label, String[] options, IntSupplier getter, IntConsumer setter) {
			super(22);
			this.label = label;
			this.options = options;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
			if (inside(mx, my, x, y, w, height)) {
				g.fill(x, y, x + w, y + height, 0x14FFFFFF);
			}
			g.text(font, label, x + 6, y + 7, TEXT, false);
			int pillW = 74;
			int px = x + w - pillW - 6;
			SoundHud.roundRect(g, px, y + 3, pillW, 16, 0xFF1B1F27);
			String name = options[Math.floorMod(getter.getAsInt(), options.length)];
			g.text(font, name, px + (pillW - font.width(name)) / 2, y + 7, accent(), false);
		}

		@Override
		boolean click(int x, int y, int w, int mx, int my, int button) {
			if ((button == 0 || button == 1) && inside(mx, my, x, y, w, height)) {
				int next = Math.floorMod(getter.getAsInt() + (button == 1 ? -1 : 1), options.length);
				setter.accept(next);
				cfg.save();
				return true;
			}
			return false;
		}
	}

	/** Shows what the three box colours mean with the current distance settings. */
	private final class Legend extends Row {
		Legend() {
			super(20);
		}

		@Override
		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
			int[] colors = {SoundBoxes.RED, SoundBoxes.ORANGE, SoundBoxes.YELLOW};
			String[] labels = {"<" + cfg.boxRedDist + "m", "<" + cfg.boxOrangeDist + "m", "far"};
			int cx = x + 6;
			for (int i = 0; i < 3; i++) {
				g.outline(cx, y + 5, 9, 9, colors[i]);
				g.outline(cx + 1, y + 6, 7, 7, colors[i]);
				g.text(font, labels[i], cx + 13, y + 6, TEXT, false);
				cx += 13 + font.width(labels[i]) + 10;
			}
		}
	}

	// ------------------------------------------------------------------ tabs

	private void buildTabs() {
		// 0: Detector
		List<Row> detector = new ArrayList<>();
		detector.add(new Toggle("Sound detector", () -> cfg.soundEnabled, v -> cfg.soundEnabled = v));
		detector.add(new Toggle("Ignore own sounds", () -> cfg.ignoreOwn, v -> cfg.ignoreOwn = v));
		detector.add(new Toggle("Sounds without subtitles", () -> cfg.detectNoSubtitle, v -> cfg.detectNoSubtitle = v));
		detector.add(new Slider("Detection range", 8, 128, 4, " m", () -> cfg.detectRange, v -> cfg.detectRange = v));
		detector.add(new Slider("Sound lifetime", 1, 10, 1, " s", () -> cfg.maxAgeSeconds, v -> cfg.maxAgeSeconds = v));
		detector.add(new Choice("Accent colour", SoundHud.ACCENT_NAMES, () -> cfg.accent, v -> cfg.accent = v));
		tabs.add(detector);

		// 1: Boxes
		List<Row> boxes = new ArrayList<>();
		boxes.add(new Toggle("Marker boxes", () -> cfg.showBoxes, v -> cfg.showBoxes = v));
		boxes.add(new Toggle("Name and distance", () -> cfg.boxLabels, v -> cfg.boxLabels = v));
		boxes.add(new Toggle("Edge markers", () -> cfg.boxEdgeMarkers, v -> cfg.boxEdgeMarkers = v));
		boxes.add(new Slider("Line thickness", 1, 4, 1, " px", () -> cfg.boxThickness, v -> cfg.boxThickness = v));
		boxes.add(new Slider("Red within", 2, 40, 2, " m", () -> cfg.boxRedDist, v -> {
			cfg.boxRedDist = v;
			cfg.boxOrangeDist = Math.max(cfg.boxOrangeDist, v);
		}));
		boxes.add(new Slider("Orange within", 6, 64, 2, " m", () -> cfg.boxOrangeDist,
			v -> cfg.boxOrangeDist = Math.max(v, cfg.boxRedDist)));
		boxes.add(new Legend());
		tabs.add(boxes);

		// 2: Radar
		List<Row> radar = new ArrayList<>();
		radar.add(new Toggle("Show radar", () -> cfg.showRadar, v -> cfg.showRadar = v));
		radar.add(new Toggle("Sweep animation", () -> cfg.radarSweep, v -> cfg.radarSweep = v));
		radar.add(new Toggle("Sound labels", () -> cfg.showLabels, v -> cfg.showLabels = v));
		radar.add(new Slider("Radar size", 60, 140, 10, " px", () -> cfg.radarSize, v -> cfg.radarSize = v));
		radar.add(new Slider("Panel opacity", 0, 255, 15, "", () -> cfg.backgroundAlpha, v -> cfg.backgroundAlpha = v));
		radar.add(new Choice("Position", SoundHud.CORNER_NAMES, () -> cfg.radarCorner, v -> cfg.radarCorner = v));
		tabs.add(radar);

		// 3: Ring & List
		List<Row> ringList = new ArrayList<>();
		ringList.add(new Toggle("Crosshair ring", () -> cfg.showRing, v -> cfg.showRing = v));
		ringList.add(new Slider("Ring radius", 20, 80, 2, " px", () -> cfg.ringRadius, v -> cfg.ringRadius = v));
		ringList.add(new Toggle("Sound list", () -> cfg.showList, v -> cfg.showList = v));
		ringList.add(new Slider("List length", 3, 10, 1, "", () -> cfg.listMax, v -> cfg.listMax = v));
		ringList.add(new Choice("List position", SoundHud.CORNER_NAMES, () -> cfg.listCorner, v -> cfg.listCorner = v));
		tabs.add(ringList);

		// 4: Filters
		List<Row> filters = new ArrayList<>();
		for (SoundType type : SoundType.values()) {
			String id = type.name();
			filters.add(new Toggle(
				type.label, type.color,
				() -> !cfg.disabledTypes.contains(id),
				v -> {
					if (v) {
						cfg.disabledTypes.remove(id);
					} else {
						cfg.disabledTypes.add(id);
					}
				}
			));
		}
		tabs.add(filters);

		// 5: Info HUD
		List<Row> info = new ArrayList<>();
		info.add(new Toggle("Info HUD", () -> cfg.visible, v -> cfg.visible = v));
		info.add(new Toggle("FPS", () -> cfg.showFps, v -> cfg.showFps = v));
		info.add(new Toggle("Coordinates", () -> cfg.showCoords, v -> cfg.showCoords = v));
		info.add(new Toggle("Facing", () -> cfg.showFacing, v -> cfg.showFacing = v));
		info.add(new Toggle("Speed", () -> cfg.showSpeed, v -> cfg.showSpeed = v));
		info.add(new Toggle("Light level", () -> cfg.showLight, v -> cfg.showLight = v));
		info.add(new Toggle("Held durability", () -> cfg.showHeldDurability, v -> cfg.showHeldDurability = v));
		info.add(new Choice("Position", SoundHud.CORNER_NAMES, () -> cfg.corner, v -> cfg.corner = v));
		tabs.add(info);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		int px = left();
		int py = top();
		int accent = accent();
		long now = Util.getMillis();

		// Panel body, sidebar and header.
		SoundHud.roundRect(g, px, py, PW, PH, 0xF2101218);
		g.fill(px + 1, py + HEADER, px + SIDEBAR, py + PH - 2, 0xFF0B0D11);
		g.fill(px + 2, py, px + PW - 2, py + HEADER - 1, 0xFF0D1015);
		g.fill(px + 2, py + HEADER - 1, px + PW - 2, py + HEADER, SoundHud.alpha(accent, 0.85f));
		g.outline(px, py, PW, PH, SoundHud.alpha(accent, 0.35f));

		Component title = Component.literal("GLANCE").withStyle(ChatFormatting.BOLD);
		g.text(font, title, px + 10, py + 9, accent, false);
		g.text(font, "SOUND DETECTOR", px + 10 + font.width(title) + 6, py + 9, DIM, false);
		String hint = "RSHIFT / ESC to close";
		g.text(font, hint, px + PW - 10 - font.width(hint), py + 9, DIM, false);

		// Sidebar tabs.
		for (int i = 0; i < TAB_NAMES.length; i++) {
			int ty = py + HEADER + 8 + i * 24;
			boolean active = i == tab;
			boolean hover = inside(mouseX, mouseY, px + 4, ty, SIDEBAR - 8, 20);
			if (active) {
				SoundHud.roundRect(g, px + 4, ty, SIDEBAR - 8, 20, SoundHud.alpha(accent, 0.16f));
				g.fill(px + 4, ty + 4, px + 6, ty + 16, accent);
			} else if (hover) {
				SoundHud.roundRect(g, px + 4, ty, SIDEBAR - 8, 20, 0x12FFFFFF);
			}
			g.text(font, TAB_NAMES[i], px + 12, ty + 6, active ? 0xFFFFFFFF : DIM, false);
		}
		g.text(font, "v" + GlanceClient.VERSION, px + 10, py + PH - 14, 0xFF4A505C, false);

		// Rows for the active tab.
		int rowX = px + SIDEBAR + 8;
		int rowW = PW - SIDEBAR - PREVIEW - 20;
		int rowY = py + HEADER + 8;
		for (Row row : tabs.get(tab)) {
			row.draw(g, rowX, rowY, rowW, mouseX, mouseY);
			rowY += row.height;
		}

		drawPreview(g, px, py, now);
	}

	private void drawPreview(GuiGraphicsExtractor g, int px, int py, long now) {
		int cardX = px + PW - PREVIEW - 6;
		int cardY = py + HEADER + 8;
		int cardH = PH - HEADER - 16;
		SoundHud.roundRect(g, cardX, cardY, PREVIEW, cardH, 0xFF0B0D11);
		g.text(font, "PREVIEW", cardX + 8, cardY + 6, DIM, false);

		List<SoundHud.Marker> demo = demoMarkers(now);

		int radarSize = 100;
		int radarX = cardX + (PREVIEW - radarSize) / 2;
		int radarY = cardY + 18;
		SoundHud.drawRadar(g, font, radarX, radarY, radarSize, demo, cfg, now, false);

		int ringCx = cardX + PREVIEW / 2;
		int ringCy = radarY + radarSize + 38;
		g.text(font, "RING", cardX + 8, ringCy - 4, DIM, false);
		g.fill(ringCx - 1, ringCy - 1, ringCx + 2, ringCy + 2, 0xFF3A3F4B);
		SoundHud.drawRing(g, ringCx, ringCy, 14, demo, cfg);
	}

	/** Fake sounds that drift around so the preview reacts to every setting. */
	private List<SoundHud.Marker> demoMarkers(long now) {
		SoundType[] types = SoundType.values();
		List<SoundHud.Marker> list = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			float direction = (i % 2 == 0) ? 1.0f : -1.0f;
			float rel = Mth.wrapDegrees(i * 61.0f + now * 0.02f * direction);
			float wave = 0.5f + 0.5f * (float) Math.sin(now * 0.0007 + i * 1.7);
			float dist = cfg.detectRange * (0.2f + 0.7f * wave);
			long age = (now + i * 500L) % 2400L;
			float life = 1.0f - age / 2400.0f;
			list.add(new SoundHud.Marker(rel, dist, 0, types[i % types.length].label,
				types[i % types.length], life, age, 1, 0, 0, 0));
		}
		return list;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();
		int button = event.button();
		int px = left();
		int py = top();

		if (button == 0) {
			for (int i = 0; i < TAB_NAMES.length; i++) {
				int ty = py + HEADER + 8 + i * 24;
				if (inside(mx, my, px + 4, ty, SIDEBAR - 8, 20)) {
					tab = i;
					return true;
				}
			}
		}

		int rowX = px + SIDEBAR + 8;
		int rowW = PW - SIDEBAR - PREVIEW - 20;
		int rowY = py + HEADER + 8;
		for (Row row : tabs.get(tab)) {
			if (row.click(rowX, rowY, rowW, mx, my, button)) {
				return true;
			}
			rowY += row.height;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			dragging.update(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			cfg.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		cfg.save();
		this.minecraft.gui.setScreen(null);
	}
}

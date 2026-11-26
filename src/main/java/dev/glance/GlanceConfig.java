package dev.glance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny JSON config stored at config/glance.json. Edit the file (or use the keybinds)
 * and the changes are picked up on next launch.
 */
public final class GlanceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("glance.json");

	/** 0 = top left, 1 = top right, 2 = bottom left, 3 = bottom right. */
	public int corner = 0;
	public boolean visible = true;
	public boolean showFps = true;
	public boolean showCoords = true;
	public boolean showFacing = true;
	public boolean showSpeed = true;
	public boolean showLight = true;
	public boolean showHeldDurability = true;
	/** Background opacity, 0-255. */
	public int backgroundAlpha = 112;

	// ---- Sound detector ----
	public boolean soundEnabled = true;
	/** Ignore sounds right on top of you (your own footsteps, hits, etc). */
	public boolean ignoreOwn = true;
	/** Maximum distance in blocks, 8-128. Default is the maximum so nothing is filtered out. */
	public int detectRange = 128;
	/** How long a sound stays on screen, in seconds, 1-10. */
	public int maxAgeSeconds = 6;
	/** Also detect sounds that have no vanilla subtitle (they are named from their sound id). */
	public boolean detectNoSubtitle = true;

	// ---- Marker boxes ----
	public boolean showBoxes = true;
	public boolean boxLabels = true;
	/** Show an indicator on the screen edge pointing at sounds that are off-screen or behind you. */
	public boolean boxEdgeMarkers = true;
	/** Box size in blocks, 1-4. */
	public int boxSize = 1;
	/** Smallest box drawn on screen, in pixels, 8-40. */
	public int boxMinPx = 14;
	public int boxThickness = 2;
	/** Red when closer than this many blocks (danger sounds are always red). */
	public int boxRedDist = 10;
	/** Orange when closer than this many blocks, yellow beyond. */
	public int boxOrangeDist = 24;
	/** Index into SoundHud.ACCENTS. */
	public int accent = 0;

	public boolean showRadar = true;
	public int radarSize = 90;
	public int radarCorner = 1;
	public boolean radarSweep = true;
	public boolean showLabels = true;

	public boolean showRing = true;
	public int ringRadius = 36;

	public boolean showList = false;
	public int listCorner = 3;
	public int listMax = 5;

	/** Names of SoundType values that are switched off. */
	public java.util.Set<String> disabledTypes = new java.util.HashSet<>();

	private static GlanceConfig instance;

	public static GlanceConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static GlanceConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				GlanceConfig loaded = GSON.fromJson(reader, GlanceConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				GlanceClient.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		GlanceConfig fresh = new GlanceConfig();
		fresh.save();
		return fresh;
	}

	/** Clamps every value to a safe range so a hand-edited file cannot break rendering. */
	private void sanitize() {
		corner = Math.floorMod(corner, 4);
		radarCorner = Math.floorMod(radarCorner, 4);
		listCorner = Math.floorMod(listCorner, 4);
		accent = Math.floorMod(accent, SoundHud.ACCENTS.length);
		backgroundAlpha = clamp(backgroundAlpha, 0, 255);
		detectRange = clamp(detectRange, 8, 128);
		maxAgeSeconds = clamp(maxAgeSeconds, 1, 10);
		radarSize = clamp(radarSize, 60, 140);
		ringRadius = clamp(ringRadius, 20, 80);
		listMax = clamp(listMax, 3, 10);
		boxSize = clamp(boxSize, 1, 4);
		boxMinPx = clamp(boxMinPx, 8, 40);
		boxThickness = clamp(boxThickness, 1, 4);
		boxRedDist = clamp(boxRedDist, 2, 40);
		boxOrangeDist = clamp(boxOrangeDist, boxRedDist, 64);
		if (disabledTypes == null) {
			disabledTypes = new java.util.HashSet<>();
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			GlanceClient.LOGGER.warn("Could not write {}", PATH, e);
		}
	}
}

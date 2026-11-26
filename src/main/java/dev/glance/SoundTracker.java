package dev.glance;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Listens to every positional sound the game plays and keeps a short-lived list of where each one
 * came from. Detection happens before the game applies your master volume, so it works the same
 * with the volume turned all the way down.
 *
 * It can only see sounds the server actually sent to your client, which is the game's own sound range.
 */
public final class SoundTracker {
	private static final int MAX_ENTRIES = 96;

	private static final class Entry {
		double x, y, z;
		String label;
		SoundType type;
		long time;
		int count = 1;
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private SoundTracker() {
	}

	public static void register(Minecraft client) {
		client.getSoundManager().addListener(SoundTracker::onSound);
	}

	private static void onSound(SoundInstance sound, WeighedSoundEvents accessor, float range) {
		GlanceConfig cfg = GlanceConfig.get();
		Player player = Minecraft.getInstance().player;
		if (!cfg.soundEnabled || player == null) {
			return;
		}
		// Skip menu / UI sounds and sounds that are not positional.
		if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) {
			return;
		}

		Component subtitle = accessor == null ? null : accessor.getSubtitle();
		if (subtitle == null && !cfg.detectNoSubtitle) {
			return;
		}

		double dx = sound.getX() - player.getX();
		double dy = sound.getY() - player.getY();
		double dz = sound.getZ() - player.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (cfg.ignoreOwn && dist < 1.5) {
			return;
		}
		if (dist > cfg.detectRange) {
			return;
		}

		// Vanilla subtitle keys mirror the sound event id, so the id is enough to classify.
		String path = sound.getIdentifier().getPath();
		SoundType type = SoundType.classify("subtitles." + path);
		if (cfg.disabledTypes.contains(type.name())) {
			return;
		}

		String label = subtitle != null ? subtitle.getString() : prettyName(path);
		long now = Util.getMillis();

		// Merge repeats of the same sound from the same spot so the screen stays readable.
		for (Entry existing : ENTRIES) {
			if (existing.label.equals(label) && now - existing.time < 1500) {
				double ex = existing.x - sound.getX();
				double ez = existing.z - sound.getZ();
				if (ex * ex + ez * ez < 9.0) {
					existing.x = sound.getX();
					existing.y = sound.getY();
					existing.z = sound.getZ();
					existing.time = now;
					existing.count++;
					return;
				}
			}
		}

		Entry entry = new Entry();
		entry.x = sound.getX();
		entry.y = sound.getY();
		entry.z = sound.getZ();
		entry.label = label;
		entry.type = type;
		entry.time = now;
		ENTRIES.add(entry);
		if (ENTRIES.size() > MAX_ENTRIES) {
			ENTRIES.remove(0);
		}
	}

	/** "entity.zombie.ambient" becomes "Zombie ambient". */
	private static String prettyName(String path) {
		String[] parts = path.split("\\.");
		int start = 0;
		if (parts.length > 1) {
			switch (parts[0]) {
				case "entity", "block", "item", "ambient", "weather", "particle" -> start = 1;
				default -> {
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < parts.length; i++) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(parts[i].replace('_', ' '));
		}
		String text = sb.toString().trim();
		if (text.isEmpty()) {
			return path;
		}
		return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}

	/** Drops expired entries. Call once per client tick. */
	public static void tick() {
		if (ENTRIES.isEmpty()) {
			return;
		}
		long maxAge = GlanceConfig.get().maxAgeSeconds * 1000L;
		long now = Util.getMillis();
		ENTRIES.removeIf(entry -> now - entry.time > maxAge);
	}

	/** Builds screen-ready markers relative to where the player is currently looking. */
	public static List<SoundHud.Marker> markers(float partialTick) {
		List<SoundHud.Marker> out = new ArrayList<>();
		Player player = Minecraft.getInstance().player;
		if (player == null || ENTRIES.isEmpty()) {
			return out;
		}

		GlanceConfig cfg = GlanceConfig.get();
		float yaw = player.getViewYRot(partialTick);
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		long now = Util.getMillis();
		long maxAge = Math.max(1000L, cfg.maxAgeSeconds * 1000L);

		for (Entry entry : ENTRIES) {
			long age = now - entry.time;
			if (age > maxAge) {
				continue;
			}
			double dx = entry.x - px;
			double dz = entry.z - pz;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			// Minecraft yaw 0 faces +Z (south) and grows clockwise, so atan2(-dx, dz) gives world yaw.
			float worldYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float rel = Mth.wrapDegrees(worldYaw - yaw);
			float life = 1.0f - (float) age / (float) maxAge;
			out.add(new SoundHud.Marker(
				rel, (float) horizontal, (float) (entry.y - py),
				entry.label, entry.type, life, age, entry.count,
				entry.x, entry.y, entry.z
			));
		}
		return out;
	}
}

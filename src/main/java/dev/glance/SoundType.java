package dev.glance;

import java.util.Locale;

/** Broad groups a detected sound can fall into, each with its own colour in the UI. */
public enum SoundType {
	PLAYER("Players", 0xFF4DA3FF),
	HOSTILE("Hostile mobs", 0xFFFF4D4D),
	PASSIVE("Passive mobs", 0xFF6BE675),
	DANGER("Danger", 0xFFFF9F1C),
	FOOTSTEPS("Footsteps", 0xFFB48CFF),
	BLOCKS("Blocks", 0xFFE6D36B),
	AMBIENT("Ambient", 0xFF7FD6D1),
	OTHER("Other", 0xFFAAB0BC);

	private static final String[] DANGER_WORDS = {
		"primed", "explode", "lightning", "fuse", "warden", "tnt"
	};

	private static final String[] HOSTILE_WORDS = {
		"zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pillager", "vindicator",
		"evoker", "ravager", "vex", "phantom", "ghast", "blaze", "slime", "magma_cube", "drowned",
		"husk", "stray", "guardian", "silverfish", "endermite", "hoglin", "zoglin", "piglin_brute",
		"wither", "shulker", "bogged", "breeze", "illusioner"
	};

	public final String label;
	public final int color;

	SoundType(String label, int color) {
		this.label = label;
		this.color = color;
	}

	/** Classifies a vanilla subtitle translation key such as "subtitles.entity.zombie.ambient". */
	public static SoundType classify(String key) {
		String k = key.toLowerCase(Locale.ROOT);
		if (containsAny(k, DANGER_WORDS)) {
			return DANGER;
		}
		if (k.contains(".player.")) {
			return PLAYER;
		}
		if (k.contains("footstep") || k.contains(".step")) {
			return FOOTSTEPS;
		}
		if (k.startsWith("subtitles.entity.")) {
			return containsAny(k, HOSTILE_WORDS) ? HOSTILE : PASSIVE;
		}
		if (k.startsWith("subtitles.block.")) {
			return BLOCKS;
		}
		if (k.startsWith("subtitles.ambient")) {
			return AMBIENT;
		}
		return OTHER;
	}

	private static boolean containsAny(String text, String[] words) {
		for (String word : words) {
			if (text.contains(word)) {
				return true;
			}
		}
		return false;
	}
}

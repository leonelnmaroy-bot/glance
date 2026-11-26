package dev.glance;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Glance: a small, client-only HUD showing FPS, coordinates, facing, speed,
 * light level and held-item durability.
 */
public class GlanceClient implements ClientModInitializer {
	public static final String MOD_ID = "glance";
	public static final String VERSION = "1.2.1";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int MARGIN = 4;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath(MOD_ID, "main")
	);

	private static KeyMapping toggleKey;
	private static KeyMapping cornerKey;
	private static KeyMapping menuKey;

	// Horizontal speed in blocks per second, updated once per client tick.
	private static double lastX, lastZ;
	private static boolean hasLast;
	private static double speed;

	@Override
	public void onInitializeClient() {
		GlanceConfig config = GlanceConfig.get();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + MOD_ID + ".toggle",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			CATEGORY
		));
		cornerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + MOD_ID + ".corner",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			CATEGORY
		));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + MOD_ID + ".menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			CATEGORY
		));

		// Hook the sound feed once the client (and its sound manager) is fully started.
		ClientLifecycleEvents.CLIENT_STARTED.register(SoundTracker::register);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				client.gui.setScreen(new SoundMenuScreen());
			}
			SoundTracker.tick();
			while (toggleKey.consumeClick()) {
				config.visible = !config.visible;
				config.save();
				notify(client, config.visible ? "message.glance.shown" : "message.glance.hidden");
			}
			while (cornerKey.consumeClick()) {
				config.corner = (config.corner + 1) % 4;
				config.save();
			}
			updateSpeed(client);
		});

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
			GlanceClient::extract
		);
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "sounds"),
			SoundHud::extract
		);

		LOGGER.info("Glance loaded");
	}

	private static void notify(Minecraft client, String key) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.translatable(key));
		}
	}

	private static void updateSpeed(Minecraft client) {
		Player player = client.player;
		if (player == null) {
			hasLast = false;
			speed = 0;
			return;
		}
		double x = player.getX();
		double z = player.getZ();
		if (hasLast) {
			double dx = x - lastX;
			double dz = z - lastZ;
			// 20 ticks per second. Large jumps (teleports) are ignored.
			double perTick = Math.sqrt(dx * dx + dz * dz);
			speed = perTick > 10 ? 0 : perTick * 20.0;
		}
		lastX = x;
		lastZ = z;
		hasLast = true;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		GlanceConfig config = GlanceConfig.get();
		Minecraft client = Minecraft.getInstance();
		if (!config.visible || client.player == null || client.level == null || client.options.hideGui) {
			return;
		}

		List<String> lines = buildLines(client, client.player, config);
		if (lines.isEmpty()) {
			return;
		}

		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, client.font.width(line));
		}
		int boxWidth = textWidth + PADDING * 2;
		int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2 - 1;

		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		boolean right = config.corner == 1 || config.corner == 3;
		boolean bottom = config.corner == 2 || config.corner == 3;
		int x = right ? screenWidth - boxWidth - MARGIN : MARGIN;
		int y = bottom ? screenHeight - boxHeight - MARGIN - 20 : MARGIN;

		int background = (config.backgroundAlpha << 24);
		graphics.fill(x, y, x + boxWidth, y + boxHeight, background);

		int textY = y + PADDING;
		for (String line : lines) {
			graphics.text(client.font, line, x + PADDING, textY, 0xFFFFFFFF, true);
			textY += LINE_HEIGHT;
		}
	}

	private static List<String> buildLines(Minecraft client, Player player, GlanceConfig config) {
		List<String> lines = new ArrayList<>();

		if (config.showFps) {
			lines.add(client.getFps() + " FPS");
		}
		if (config.showCoords) {
			lines.add(String.format(Locale.ROOT, "XYZ %.1f / %.1f / %.1f", player.getX(), player.getY(), player.getZ()));
		}
		if (config.showFacing) {
			lines.add("Facing " + facing(player.getYRot()));
		}
		if (config.showSpeed) {
			lines.add(String.format(Locale.ROOT, "Speed %.1f b/s", speed));
		}
		if (config.showLight && client.level != null) {
			BlockPos pos = player.blockPosition();
			lines.add("Light " + client.level.getMaxLocalRawBrightness(pos));
		}
		if (config.showHeldDurability) {
			ItemStack held = player.getMainHandItem();
			if (!held.isEmpty() && held.isDamageableItem()) {
				int left = held.getMaxDamage() - held.getDamageValue();
				lines.add(left + " / " + held.getMaxDamage() + " durability");
			}
		}
		return lines;
	}

	/** Converts a yaw angle in degrees to a compass label. Minecraft yaw 0 faces south. */
	static String facing(float yaw) {
		float wrapped = Mth.wrapDegrees(yaw);
		if (wrapped < 0) {
			wrapped += 360.0f;
		}
		// 0 = S, 90 = W, 180 = N, 270 = E, split into 8 sectors of 45 degrees.
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		int index = Math.floorMod((int) Math.floor((wrapped + 22.5f) / 45.0f), 8);
		return names[index];
	}
}

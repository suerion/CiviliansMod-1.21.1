package net.asian.civiliansmod.util;

import net.minecraft.world.World;

public class ModCompat {

    // Cached because replay status may be queried frequently; MUST be reset on join/world changes.
    private static Boolean flashbackReplay = null;

    public static boolean isFlashbackReplay() {
        if (flashbackReplay != null) {
            return flashbackReplay;
        }

        try {
            Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
            Object result = flashbackClass.getMethod("isInReplay").invoke(null);
            flashbackReplay = result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            flashbackReplay = false;
        }

        return flashbackReplay;
    }

    public static void resetRuntimeCaches() {
        flashbackReplay = null;
    }

    public static boolean isInReplay() {
        return isFlashbackReplay();
    }

    public static boolean isRealServerWorld(World world) {
        if (world == null) return false;
        if (world.isClient) return false;

        if (isInReplay()) return false;

        if (!(world instanceof net.minecraft.server.world.ServerWorld)) return false;
        if (world.getServer() == null) return false;

        return true;
    }

    private static boolean replayJoinPhase = true;

    public static void onClientJoinComplete() {
        replayJoinPhase = false;
    }

    public static boolean isInReplayJoinPhase() {
        return replayJoinPhase && isInReplay();
    }
}

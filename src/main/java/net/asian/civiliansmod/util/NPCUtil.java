package net.asian.civiliansmod.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import net.asian.civiliansmod.CiviliansMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class NPCUtil {
    public static final Map<Integer, SkinIdentifier> waitingSync = new HashMap<>();

    static final List<SkinIdentifier> skins = new ArrayList<>();

    public static List<SkinIdentifier> getSkins() { return skins; }

    public static Map<SkinIdentifier, byte[]> images = new HashMap<>();

    public static boolean isSlim(int index) {
        if (skins.isEmpty()) return false;
        if (index < 0 || index >= skins.size()) return false;
        return skins.get(index).slim();
    }

    private static boolean loaded = false;

    public static void ensureSkinsLoaded() {
        if (loaded) return;
        loaded = true;

        refreshTextures();
        MinecraftClient.getInstance().execute(NPCUtil::registerSkinsRenderThread);
    }


    private static void registerDefaultSkins() {
        MinecraftClient.getInstance().getResourceManager()
                .findResources("textures/entity/npc/wide", path -> path.toString().endsWith(".png"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> skins.add(new SkinIdentifier(e.getKey(), false, false)));
    }

    private static void registerSlimSkins() {
        MinecraftClient.getInstance().getResourceManager()
                .findResources("textures/entity/npc/slim", path -> path.toString().endsWith(".png"))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> skins.add(new SkinIdentifier(e.getKey(), true, false)));
    }

    private static void registerDefaultCustomSkins() {
        try (var files = Files.list(FolderUtil.WIDE_SKIN_PATH).sorted()) {
            searchAndConvertSkins(files, false);
        } catch (
                IOException e) {
            CiviliansMod.LOGGER.error("error while listing skin files");
            e.printStackTrace();
        }
    }

    private static void registerSlimCustomSkins() {
        try (var files = Files.list(FolderUtil.SLIM_SKIN_PATH).sorted()) {
            searchAndConvertSkins(files, true);
        } catch (
                IOException e) {
            CiviliansMod.LOGGER.error("error while listing skin files");
            e.printStackTrace();
        }
    }

    public static SkinIdentifier getNPCTexture(int texture) {
        if (skins.isEmpty()) {
            CiviliansMod.LOGGER.error("Tried to get NPC skin but no skins are loaded!");
            return new SkinIdentifier(Identifier.of("minecraft", "textures/entity/steve.png"), false, false);
        }

        if (texture < 0 || texture >= skins.size()) {
            CiviliansMod.LOGGER.warn("Invalid skin index {} (skins.size = {}). Using 0 as fallback.", texture, skins.size());
            return null;
        }
        return skins.get(texture);
    }

    /**
     * method to refresh all the npc textures.
     */
    public static void refreshTextures() {
        skins.clear();
        registerDefaultSkins();
        registerSlimSkins();

        registerDefaultCustomSkins();
        registerSlimCustomSkins();
    }

    /**
     * Method to convert an image to an Identifier used to display skins.
     * The method will verify for each file that it is a png file and that has the good dimension({@code 64x64} pixels
     *
     * @param files the {@code Stream<Path>} that represents the files in the directory.
     */
    private static void searchAndConvertSkins(Stream<@NotNull Path> files, boolean slim) {
        files.forEach((file) -> {
            if (file.getFileName().toString().endsWith(".png")) {
                try {
                    InputStream stream = Files.newInputStream(file);
                    try {
                        byte[] skin = stream.readAllBytes();
                        stream.close();
                        NativeImage image = NativeImage.read(skin);
                        if (image.getHeight() != 64 || image.getWidth() != 64) {
                            return;
                        }
                        String safeName = file.getFileName().toString().toLowerCase().replace(".png", "").replaceAll("[^a-z0-9._-]", "_");
                        String textureName = "custom_skin_" + safeName;
                        Identifier textureId = Identifier.of(CiviliansMod.MOD_ID, textureName);
                        skins.add(new SkinIdentifier(textureId, slim, true));
                        images.put(skins.getLast(), skin);

                        image.close();
                    } catch (Exception e) {
                        CiviliansMod.LOGGER.error("Error while converting skin files", e);
                    }

                } catch (IOException e) {
                    CiviliansMod.LOGGER.error("Error while converting skin files", e);
                }
            }
        });
    }

    public static void registerSkinsRenderThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        for (Map.Entry<SkinIdentifier, byte[]> entry : images.entrySet()) {
            try {
                NativeImage image = NativeImage.read(entry.getValue());
                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> entry.getKey().id().getPath(), image);

                client.getTextureManager().registerTexture(entry.getKey().id(), texture);
            } catch (Exception e) {
                CiviliansMod.LOGGER.error("[NPCUtil] Failed to register skin {}", entry.getKey().id(), e);
            }
        }
    }

    public static int getDeterministicSkinIndex(UUID uuid) {
        if (skins.isEmpty()) return -1;
        return Math.floorMod(uuid.hashCode(), skins.size());
    }
}
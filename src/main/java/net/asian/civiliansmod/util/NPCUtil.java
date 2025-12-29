package net.asian.civiliansmod.util;

import net.asian.civiliansmod.CiviliansMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class NPCUtil {
    public static final Map<Integer, SkinIdentifier> waitingSync = new HashMap<>();

    static final List<SkinIdentifier> skins = new ArrayList<>();

    public static List<SkinIdentifier> getSkins() {
        return skins;
    }

    public static Map<SkinIdentifier, byte[]> images = new HashMap<>();

    private static boolean sortedForNewSystem = false;

    public static boolean isSlim(int index) {
        if (skins.isEmpty()) {
            CiviliansMod.LOGGER.debug(
                    "[CiviliansMod] isSlim called but skins not loaded yet, defaulting to wide"
            );
            return false;
        }

        if (index < 0 || index >= skins.size()) {
            CiviliansMod.LOGGER.warn(
                    "[CiviliansMod] isSlim called with invalid index {} (skins.size={}), defaulting to wide",
                    index, skins.size()
            );
            return false;
        }

        return skins.get(index).slim();
    }


    private static void registerDefaultSkins() {
        MinecraftClient.getInstance().getResourceManager().findResources("textures/entity/npc/wide", path -> path.toString().endsWith(".png")).forEach((id, resource) -> {
            skins.add(new SkinIdentifier(id, false, false));
        });
    }

    private static void registerSlimSkins() {
        MinecraftClient.getInstance().getResourceManager().findResources("textures/entity/npc/slim", path -> path.toString().endsWith(".png")).forEach((id, resource) -> {
            skins.add(new SkinIdentifier(id, true, false));
        });
    }

    private static void registerDefaultCustomSkins() {
        try (var files = Files.list(FolderUtil.WIDE_SKIN_PATH)) {
            searchAndConvertSkins(files, false);
        } catch (
                IOException e) {
            CiviliansMod.LOGGER.error("error while listing skin files");
            e.printStackTrace();
        }
    }

    private static void registerSlimCustomSkins() {
        try (var files = Files.list(FolderUtil.SLIM_SKIN_PATH)) {
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
            texture = 0;
        }

        return skins.get(texture);
    }

    public static void sortSkins() {
        if (sortedForNewSystem) return;

        skins.sort(Comparator
                .comparing((SkinIdentifier s) -> s.custom())
                .thenComparing(s -> s.id().toString())
                .thenComparing(s -> s.slim())
        );
        sortedForNewSystem = true;

        CiviliansMod.LOGGER.info("[CiviliansMod] Skins sorted for NEW NPC system");
    }

    /**
     * method to refresh all the npc textures.
     */
    public static void refreshTextures() {
        skins.clear();
        sortedForNewSystem = false;
        registerDefaultSkins();
        registerSlimSkins();
        registerDefaultCustomSkins();
        registerSlimCustomSkins();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        CiviliansMod.LOGGER.info("[CiviliansMod] Re-synced NPC skins after texture refresh");
    }


    /**
     * Method to convert an image to an Identifier used to display skins.
     * The method will verify for each file that it is a png file and that has the good dimension({@code 64x64} pixels
     *
     * @param files the {@code Stream<Path>} that represents the files in the directory.
     */
    private static void searchAndConvertSkins(Stream<@NotNull Path> files, boolean slim) {
        AtomicInteger i = new AtomicInteger();
        files.sorted().forEach((file) -> {
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
                        String originalFileName = file.getFileName().toString(); // skinfile name -> Sunny_(classic_texture)_JE1.png
                        String safeName = originalFileName.toLowerCase().replaceAll("[^a-z0-9._-]", "_"); // attention! don't use similar symbols in identifier!!
                        String textureName = "custom_skin_" + safeName;
                        NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(() -> textureName, image);
                        Identifier textureId = Identifier.of(CiviliansMod.MOD_ID, textureName);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, dynamicTexture);
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

    public static void registerSkin(){
    }

    @Environment(EnvType.CLIENT)
    public static void registerCustomSkinFromBytes(SkinIdentifier skin, byte[] skinBytes) {
        try {
            NativeImage image = NativeImage.read(skinBytes);

            String textureName = skin.id().getPath();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> textureName, image);

            MinecraftClient.getInstance().getTextureManager().registerTexture(skin.id(), texture);

            images.put(skin, skinBytes);
            if (!skins.contains(skin)) {
                skins.add(skin);
            }

        } catch (IOException e) {
            CiviliansMod.LOGGER.error("Failed to re-register NPC skin {}", skin.id(), e);
        }
    }
    public static int getDeterministicSkinIndex(UUID uuid) {
        if (getSkins().isEmpty()) return -1;
        return Math.floorMod(uuid.hashCode(), getSkins().size());
    }
    public static void ensureSkinsLoaded() {
        if (!getSkins().isEmpty()) return;

        CiviliansMod.LOGGER.info("[FLASHBACK] Loading NPC skins manually");
        refreshTextures();
    }
}

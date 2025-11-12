package net.asian.civiliansmod.trades;

import com.google.common.collect.ImmutableMap;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffers;

import java.util.Map;

// This class will manage the preset trades for NPCs
public class TradeManager {

    // A map where the key is the preset name (e.g., "blacksmith") and the value is a list of trades.
    public static final Map<String, TradeOffers.Factory[]> PRESET_TRADES;

    static {
        PRESET_TRADES = ImmutableMap.<String, TradeOffers.Factory[]>builder()
                .put("none", new TradeOffers.Factory[]{}) // An empty preset
                .put("florist", new TradeOffers.Factory[]{
                        // FIX: Changed BuyForOneEmeraldFactory to SellItemFactory
                        // Parameters: (item, emeraldCost, count, maxUses, experience)
                        new TradeOffers.SellItemFactory(Items.DANDELION, 1, 12, 10, 2),
                        new TradeOffers.SellItemFactory(Items.POPPY, 1, 12, 10, 2),
                        new TradeOffers.SellItemFactory(Items.LILAC, 1, 8, 10, 2),
                        new TradeOffers.SellItemFactory(Items.FLOWER_POT, 1, 1, 10, 1)
                })
                .put("blacksmith", new TradeOffers.Factory[]{
                        // FIX: Changed BuyForOneEmeraldFactory to SellItemFactory
                        new TradeOffers.SellItemFactory(Items.COAL, 1, 15, 16, 5),
                        // FIX: Changed generic Factory to BuyItemFactory
                        // Parameters: (item, price, maxUses, experience)
                        new TradeOffers.BuyItemFactory(Items.IRON_INGOT, 4, 12, 10),
                        new TradeOffers.SellItemFactory(Items.IRON_AXE, 3, 1, 12, 10),
                        new TradeOffers.SellItemFactory(Items.IRON_SWORD, 4, 1, 12, 10),
                        new TradeOffers.SellEnchantedToolFactory(Items.DIAMOND_PICKAXE, 18, 3, 15, 0.2f)
                })
                .build();
    }

    public static String[] getPresetNames() {
        return PRESET_TRADES.keySet().toArray(new String[0]);
    }
}
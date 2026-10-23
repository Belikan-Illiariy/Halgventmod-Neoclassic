package com.nrdhvnstd.halgventmod.client.pottery;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Посуда и сырец как предметы — плоские иконки (textures/item/pottery/ware, greenware; рисует
 * tools/icon_painter/Vessels.cs, модели — tools/pottery_models.sh). Какую картинку взять, модели предмета узнают
 * по свойству {@code halgventmod:look}: номер в {@link #WARE_STATES} (у горящего светильника — плюс {@link #LIT},
 * у треснувшей посуды — плюс {@link #CRACKED}),
 * у сырца — форма × {@link #GREEN_STATES} + состояние.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PotteryIcons {
    public static final ResourceLocation LOOK = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "look");
    // Порядок — как в Vessels.WareStates и GreenStates и в overrides моделей
    public static final String[] WARE_STATES = {"terracotta", "underfired", "overfired", "burnished", "black"};
    public static final String[] GREEN_STATES = {"wet", "dry", "cracked", "burnished"};
    public static final int LIT = WARE_STATES.length;
    public static final int CRACKED = 2 * WARE_STATES.length;

    private PotteryIcons() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item item : new Item[] {ModItems.CLAY_CUP.get(), ModItems.CLAY_BOWL.get(), ModItems.CLAY_JUG.get(),
                    ModItems.CLAY_POT.get(), ModItems.AMPHORA.get(), ModItems.OIL_LAMP.get(), ModItems.CLAY_CRUCIBLE.get()}) {
                ItemProperties.register(item, LOOK, (stack, level, entity, seed) -> ware(stack));
            }
            ItemProperties.register(ModItems.GREENWARE.get(), LOOK, (stack, level, entity, seed) -> greenware(stack));
        });
    }

    /** Состояние обожжённой посуды — те же приметы и тот же порядок, что у цвета в {@link VoxelRender#color}. */
    public static int ware(ItemStack stack) {
        int state;
        if (Pottery.has(stack, Pottery.BLACK)) {
            state = 4;
        } else if (Pottery.has(stack, Pottery.UNDERFIRED)) {
            state = 1;
        } else if (Pottery.has(stack, Pottery.OVERFIRED)) {
            state = 2;
        } else if (Pottery.has(stack, Pottery.BURNISHED)) {
            state = 3;
        } else {
            state = 0;
        }
        return Pottery.cracked(stack) ? state + CRACKED : WareItem.lit(stack) ? state + LIT : state;
    }

    public static int greenware(ItemStack stack) {
        int state = Pottery.has(stack, Pottery.CRACKED) ? 2
                : Pottery.has(stack, Pottery.BURNISHED) ? 3
                : Pottery.has(stack, Pottery.DRIED) ? 1 : 0;
        return Pottery.vessel(stack).vesselForm().ordinal() * GREEN_STATES.length + state;
    }
}

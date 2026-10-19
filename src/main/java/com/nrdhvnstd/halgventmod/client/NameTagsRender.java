package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.NameTags;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/** Прячет ники над головами игроков по режиму /nametags. Ники мобов и именованных предметов не трогает. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class NameTagsRender {
    private NameTagsRender() {}

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        Player viewer = Minecraft.getInstance().player;
        if (event.getEntity() instanceof Player && viewer != null && !NameTags.clientMode.visibleTo(viewer)) {
            event.setCanRender(TriState.FALSE);
        }
    }
}

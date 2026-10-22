package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.armor.FaceCover;
import com.nrdhvnstd.halgventmod.world.NameTags;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Ники над головами игроков: прячет по режиму /nametags, а у игрока в капюшоне или с опущенным забралом — всегда
 * (лица не видно; администраторы видят). Ники мобов и именованных предметов не трогает.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class NameTagsRender {
    private NameTagsRender() {}

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        Player viewer = Minecraft.getInstance().player;
        if (!(event.getEntity() instanceof Player target) || viewer == null) {
            return;
        }
        boolean admin = Permissions.isAdmin(viewer);
        if (!NameTags.clientMode.visibleTo(viewer) || (FaceCover.hidden(target) && !admin)) {
            event.setCanRender(TriState.FALSE);
        }
    }
}

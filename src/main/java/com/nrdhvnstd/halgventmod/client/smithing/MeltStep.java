package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.Metal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Плавка в тигле на кузнечном горне. Тигель стоит на углях: мышь влево-вправо — двигать его от края горна к самому
 * жару (у края прохладнее), ЛКМ — качнуть мехи. Сначала металл плавится — куски тают, пока жар выше точки плавления;
 * потом расплав надо подержать в зелёной полосе «лить» ({@link #NEED} секунд). Олово и свинец плавятся у края без
 * мехов, медь и золото — только в самом жару с мехами.
 * <ul>
 *   <li>Перегрев — металл набирает газ (пузыри в отливке) и сильнее окисляется: больше шлака;</li>
 *   <li>шлак всплывает на поверхность — ПКМ по нему, снять; не сняли — уйдёт в расплав;</li>
 *   <li>латунь выше {@link Metal#ZINC_FUMES}° теряет цинк белым дымом;</li>
 *   <li>остудили ниже плавления — расплав схватывается коркой, плавить заново.</li>
 * </ul>
 */
final class MeltStep extends SmithStep {
    private static final float NEED = 3.0F;          // ⚠️ секунд в полосе «лить»
    private static final float MAX_T = 1350;
    private static final float BOOST_PER_PUMP = 55;
    private static final float BOOST_MAX = 340;
    private static final float BOOST_DECAY = 70;     // ° в секунду
    private static final float FOLLOW = 0.55F;       // как быстро жар тигля тянется к жару места
    private static final float OVERHEAT = 45;        // ° выше полосы — газ

    private final Metal metal;
    private final int chunks;
    private final List<float[]> dross = new ArrayList<>();
    private final List<float[]> fumes = new ArrayList<>();
    private float temp = 30;
    private float boost;
    private float place = 0.15F;                     // 0 — край горна, 1 — середина
    private float solid = 1;
    private float ready;
    private float gas;
    private float zinc;
    private float time;
    private int left;
    private float nextDross = 1.5F;
    private boolean molten;
    private int cx;
    private int cy;
    private int bedX;
    private int bedW;

    MeltStep(SmithWorkScreen screen, Random random, Metal metal, int parts) {
        super(screen, random);
        this.metal = metal;
        this.chunks = Math.max(2, Math.min(9, parts / 10 + 1));
    }

    @Override
    boolean done() {
        return ready >= NEED;
    }

    @Override
    int rawScore() {
        return clampScore(100 - gas * 7 - (dross.size() + left) * 5 - zinc * 9 - Math.max(0, time - 25) * 0.8F);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.melt_status", Math.round(temp),
                Math.round((1 - solid) * 100), dross.size());
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.melt_hint", metal.pourLow(), metal.pourHigh());
    }

    /** Жар места на углях: от края к середине, плюс мехи. */
    private float target() {
        float forge = 300 + 950 * screen.forgeHeat() + (screen.coal() ? 40 : 0);
        float factor = 0.3F + 0.7F * place;
        return 20 + (forge - 20) * factor + boost * (0.4F + 0.6F * place);
    }

    @Override
    void tick(float dt, long now) {
        time += dt;
        boost = Math.max(0, boost - BOOST_DECAY * dt);
        temp += (target() - temp) * Math.min(1, FOLLOW * dt);
        temp = Math.min(MAX_T, temp);
        if (temp > metal.melt()) {
            solid = Math.max(0, solid - (temp - metal.melt()) / 260F * dt);
        } else if (molten && temp < metal.melt() - 20) {
            // Схватывается коркой
            solid = Math.min(1, solid + (metal.melt() - temp) / 400F * dt);
        }
        if (solid <= 0 && !molten) {
            molten = true;
            screen.show("melt_molten", GOOD);
            screen.sound(SoundEvents.LAVA_POP, 0.9F);
        }
        if (solid > 0.3F && molten) {
            molten = false;
            screen.show("melt_froze", BAD);
        }
        if (molten && solid <= 0 && temp >= metal.pourLow() && temp <= metal.pourHigh()) {
            ready += dt;
        }
        boolean over = temp > metal.pourHigh() + OVERHEAT;
        if (over && solid < 1) {
            gas += dt;
        }
        if (metal == Metal.BRASS && temp > Metal.ZINC_FUMES && solid < 1) {
            zinc += dt;
            if (fumes.size() < 14 && random.nextFloat() < dt * 8) {
                fumes.add(new float[] {(random.nextFloat() - 0.5F) * 40, 0});
            }
        }
        for (float[] f : fumes) {
            f[1] += dt * 20;
        }
        fumes.removeIf(f -> f[1] > 50);
        // Шлак: пока металл жидкий, жарче — чаще; олово и свинец окисляются охотнее
        nextDross -= dt * (over ? 2.2F : 1) * (metal.melt() < 500 ? 1.4F : 1);
        if (nextDross <= 0 && solid < 0.6F) {
            nextDross = 1.6F + random.nextFloat() * 1.8F;
            if (dross.size() < 7) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * 0.75;
                dross.add(new float[] {(float) (Math.cos(a) * r), (float) (Math.sin(a) * r)});
            }
        }
        if (done()) {
            left = dross.size();
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int gaugeX = x + w - 16;
        bedX = x + 12;
        bedW = gaugeX - 18 - bedX;
        int bedY = y + h - 26;
        // Угли: у края тусклые, к середине (справа) — жарче
        for (int i = 0; i < bedW; i += 3) {
            float share = i / (float) bedW;
            int glow = glow(0.45F + share * 0.4F * (0.6F + screen.forgeHeat()) + boost / BOOST_MAX * 0.15F * share);
            int top = bedY + 2 + random.nextInt(3);
            g.fill(bedX + i, top, bedX + i + 3, bedY + 18, i % 9 == 0 ? shade(glow, 0.6F) : glow);
        }
        g.fill(bedX - 4, bedY + 18, bedX + bedW + 4, bedY + 22, IRON_DARK);
        small(g, Component.translatable("gui.halgventmod.smith_work.melt_edge"), bedX, bedY + 23, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.smith_work.melt_center"), bedX + bedW - 30, bedY + 23, TEXT_DIM);
        // Тигель
        int radius = 30;
        cx = bedX + 20 + Math.round(place * (bedW - 40));
        cy = bedY - 22;
        int wall = 0xFF7A4A2A;
        g.fill(cx - radius - 4, cy - 14, cx + radius + 4, cy + 22, shade(wall, 0.8F));
        g.fill(cx - radius - 2, cy - 12, cx + radius + 2, cy + 20, wall);
        // Расплав: цвет металла, светится по жару
        int surface = mix(metal.color(), glow(Math.min(1, temp / MAX_T * 1.05F)), Math.min(1, temp / 900F));
        g.fill(cx - radius, cy - 10, cx + radius, cy + 18, solid >= 1 ? 0xFF2A1E14 : surface);
        // Куски, что ещё не растаяли
        int pieces = Math.round(chunks * solid);
        for (int i = 0; i < pieces; i++) {
            int px = cx - radius + 4 + (i * 13) % (radius * 2 - 14);
            int py = cy - 6 + (i / 4) * 8;
            g.fill(px, py, px + 10, py + 6, shade(metal.color(), 0.85F));
            g.fill(px, py, px + 10, py + 1, mix(metal.color(), 0xFFFFFFFF, 0.3F));
        }
        for (float[] d : dross) {
            int dx = cx + Math.round(d[0] * (radius - 4));
            int dy = cy + 4 + Math.round(d[1] * 10);
            g.fill(dx - 3, dy - 2, dx + 3, dy + 2, SLAG);
        }
        for (float[] f : fumes) {
            int alpha = Math.max(0, 200 - Math.round(f[1] * 4)) << 24;
            g.fill(cx + Math.round(f[0]) - 2, cy - 14 - Math.round(f[1]), cx + Math.round(f[0]) + 3,
                    cy - 10 - Math.round(f[1]), alpha | 0xE8E8E8);
        }
        // Градусник: метка плавления и полоса «лить»
        int gy = y + 10;
        int gh = h - 24;
        gauge(g, gaugeX, gy, 6, gh, temp / MAX_T, metal.pourLow() / MAX_T, metal.pourHigh() / MAX_T,
                temp > metal.pourHigh() + OVERHEAT ? BAD : TEXT);
        int my = gy + gh - Math.round(gh * metal.melt() / MAX_T);
        g.fill(gaugeX - 4, my, gaugeX + 10, my + 1, WARN);
        bar(g, x + 14, y + 8, 80, 4, Math.min(1, ready / NEED), 0, 0, GOOD);
        small(g, Component.translatable("gui.halgventmod.smith_work.melt_ready"), x + 98, y + 6, TEXT_DIM);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(screen.font(), text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (bedW > 40) {
            place = (float) Math.max(0, Math.min(1, (mouseX - bedX - 20) / (bedW - 40)));
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boost = Math.min(BOOST_MAX, boost + BOOST_PER_PUMP * (screen.coal() ? 1.15F : 1));
            screen.sound(SoundEvents.FIRECHARGE_USE, 0.5F + boost / BOOST_MAX * 0.5F);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (int i = 0; i < dross.size(); i++) {
                float[] d = dross.get(i);
                double dx = cx + d[0] * 26;
                double dy = cy + 4 + d[1] * 10;
                if (Math.hypot(mouseX - dx, mouseY - dy) <= 6) {
                    dross.remove(i);
                    screen.sound(SoundEvents.LAVA_POP, 1.4F);
                    return true;
                }
            }
            return true;
        }
        return false;
    }
}

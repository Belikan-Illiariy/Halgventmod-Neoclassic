package com.nrdhvnstd.halgventmod.armorial;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Книга-гербовник: название и страницы (ArmorialPage). Хранится на сервере (ArmorialBooks). */
public record ArmorialBook(String title, List<ArmorialPage> pages) {
    public static final int TITLE_MAX = 32;
    public static final int PAGES_MAX = 200; // ⚠️

    public static final ArmorialBook EMPTY = new ArmorialBook("", List.of());

    public static final StreamCodec<FriendlyByteBuf, ArmorialBook> STREAM_CODEC = StreamCodec.of(
            (buf, book) -> book.write(buf), ArmorialBook::read);

    public ArmorialBook {
        pages = List.copyOf(pages);
    }

    public ArmorialBook withTitle(String newTitle) {
        return new ArmorialBook(newTitle, pages);
    }

    public ArmorialBook withPages(List<ArmorialPage> newPages) {
        return new ArmorialBook(title, newPages);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(title, TITLE_MAX * 4);
        int count = Math.min(pages.size(), PAGES_MAX);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            pages.get(i).write(buf);
        }
    }

    private static ArmorialBook read(FriendlyByteBuf buf) {
        String title = buf.readUtf(TITLE_MAX * 4);
        int count = Math.min(buf.readVarInt(), PAGES_MAX);
        List<ArmorialPage> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(ArmorialPage.read(buf));
        }
        return new ArmorialBook(title, pages);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title);
        ListTag list = new ListTag();
        for (ArmorialPage page : pages) {
            list.add(page.save());
        }
        tag.put("pages", list);
        return tag;
    }

    static ArmorialBook load(CompoundTag tag) {
        List<ArmorialPage> pages = new ArrayList<>();
        for (Tag each : tag.getList("pages", Tag.TAG_COMPOUND)) {
            pages.add(ArmorialPage.load((CompoundTag) each));
        }
        return new ArmorialBook(tag.getString("title"), pages);
    }
}

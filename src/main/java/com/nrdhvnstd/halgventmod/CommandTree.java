package com.nrdhvnstd.halgventmod;

import java.lang.reflect.Field;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

/**
 * Удаление ванильных команд. У Brigadier нет способа убрать команду: чистим внутренние таблицы детей корня.
 * Звать из RegisterCommandsEvent с приоритетом LOWEST — после ванильных команд и команд других модов.
 */
public final class CommandTree {
    private static final Field CHILDREN = field("children");
    private static final Field LITERALS = field("literals");

    private CommandTree() {}

    public static void remove(CommandDispatcher<?> dispatcher, String... names) {
        CommandNode<?> root = dispatcher.getRoot();
        for (String name : names) {
            try {
                ((Map<?, ?>) CHILDREN.get(root)).remove(name);
                ((Map<?, ?>) LITERALS.get(root)).remove(name);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Не удалось убрать команду /" + name, e);
            }
        }
    }

    private static Field field(String name) {
        try {
            Field field = CommandNode.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("В Brigadier нет поля " + name, e);
        }
    }
}

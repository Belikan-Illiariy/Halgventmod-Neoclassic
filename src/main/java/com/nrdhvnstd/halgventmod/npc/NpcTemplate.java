package com.nrdhvnstd.halgventmod.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.character.Appearance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Шаблон НПС (data/&lt;пространство&gt;/npc/templates/&lt;id&gt;.json, docs/TZ_npc.md, 2.3): из него получается
 * личность — имя, облик, скин. Поля шаблона разбирает NpcTemplates.
 *
 * @param weight     вес при случайном выборе среди шаблонов роли
 * @param male       имена мужчин; female — женщин; surnames — прозвища после имени (необязательно)
 * @param skins      готовые скины «пространство:путь»; пусто — облик собирается из частей
 * @param appearance допустимые варианты частей облика; часть не названа — любой вариант
 */
public record NpcTemplate(ResourceLocation id, NpcRole role, int weight, List<String> male, List<String> female,
                          List<String> surnames, List<String> skins, Map<String, List<String>> appearance) {

    /** Личность одного НПС: имя, облик, скин (пусто — по облику) и тонкие руки. */
    public record Identity(String name, Map<String, String> appearance, String skin, boolean slim) {}

    public Identity generate(RandomSource random) {
        Map<String, String> look = look(random);
        boolean female = Appearance.female(look);
        List<String> firstNames = female ? this.female : male;
        if (firstNames.isEmpty()) {
            firstNames = female ? male : this.female;
        }
        StringBuilder name = new StringBuilder(firstNames.isEmpty() ? "" : pick(firstNames, random));
        if (!surnames.isEmpty()) {
            name.append(name.isEmpty() ? "" : " ").append(pick(surnames, random));
        }
        String skin = skins.isEmpty() ? "" : pick(skins, random);
        // Тонкие руки — от телосложения облика, у готового скина их задаёт администрация
        boolean slim = skin.isEmpty() && "slim".equals(Appearance.get(look, Appearance.Part.BUILD));
        return new Identity(name.toString(), look, skin, slim);
    }

    // Каждая часть — случайный допустимый вариант; голос и борода потом подгоняются под пол
    private Map<String, String> look(RandomSource random) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Appearance.Part part : Appearance.Part.values()) {
            List<String> allowed = appearance.get(part.id());
            map.put(part.id(), pick(allowed == null || allowed.isEmpty() ? part.options() : allowed, random));
        }
        return Appearance.matchSex(map);
    }

    private static String pick(List<String> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
    }
}

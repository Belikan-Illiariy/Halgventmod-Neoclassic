#!/usr/bin/env bash
# Модели печи 2×2×2 (холодной и топящейся) — ванильные текстуры: каменный под с нишей, где сложены дрова, на нём
# кирпичный свод, спереди арочное устье в каменной обрамовке, над устьем — дымоход. Большая средневековая печь.
# Запуск из корня репозитория: bash tools/oven_models.sh
# Пишет models/block/oven{,_lit}.json, blockstates/baking_oven.json, models/item/baking_oven.json и
# loot_table/blocks/baking_oven.json. Модель — от главной части (передняя левая нижняя) на 32×32×32, лицо (устье) —
# на север (z = 0), печь уходит на восток (x) и на юг (z).
set -e
cd "$(dirname "$0")/../src/main/resources/assets/halgventmod/models/block"

TEX='"particle":"minecraft:block/bricks","stone":"minecraft:block/stone_bricks","rough":"minecraft:block/cobblestone","brick":"minecraft:block/bricks","log":"minecraft:block/spruce_log","log_end":"minecraft:block/spruce_log_top","dark":"minecraft:block/black_concrete","glow":"minecraft:block/magma","soot":"minecraft:block/coal_block"'

# model <файл> <топится: 0/1>
model() {
  awk -v lit="$2" -v tex="$TEX" '
  function uv(a, b) { return sprintf("[0,0,%g,%g]", (a > 16 ? 16 : a), (b > 16 ? 16 : b)) }
  # Кубик; ends — текстура торцов (север и юг), если у них своя (поленья)
  function el(x1, y1, z1, x2, y2, z2, t, extra, ends,   w, h, d) {
    w = x2 - x1; h = y2 - y1; d = z2 - z1
    if (ends == "") ends = t
    out[n++] = sprintf("{\"from\":[%g,%g,%g],\"to\":[%g,%g,%g]%s,\"faces\":{" \
      "\"down\":{\"texture\":\"#%s\",\"uv\":%s},\"up\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"north\":{\"texture\":\"#%s\",\"uv\":%s},\"south\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"west\":{\"texture\":\"#%s\",\"uv\":%s},\"east\":{\"texture\":\"#%s\",\"uv\":%s}}}",
      x1, y1, z1, x2, y2, z2, extra, t, uv(w, d), t, uv(w, d), ends, uv(w, h), ends, uv(w, h), t, uv(d, h), t, uv(d, h))
  }
  # Ярус свода: восьмигранник радиуса r вокруг (16, cz)
  function ring(y1, y2, r, t) {
    el(16 - r, y1, cz - r * 0.5, 16 + r, y2, cz + r * 0.5, t)
    el(16 - r * 0.5, y1, cz - r, 16 + r * 0.5, y2, cz + r, t)
    el(16 - r * 0.8, y1, cz - r * 0.8, 16 + r * 0.8, y2, cz + r * 0.8, t)
  }
  BEGIN {
    glow = ",\"shade\":false,\"neoforge_data\":{\"block_light\":15,\"sky_light\":15}"
    inner = lit == 1 ? "glow" : "dark"; g = lit == 1 ? glow : ""
    cz = 17.5
    # Под: каменный короб, спереди ниша для дров
    el(0, 0, 0, 8, 11, 32, "rough"); el(24, 0, 0, 32, 11, 32, "rough")
    el(8, 0, 11, 24, 11, 32, "rough"); el(8, 8, 0, 24, 11, 11, "rough"); el(8, 0, 0, 24, 1, 11, "rough")
    el(8, 1, 10.8, 24, 8, 11, "soot")
    # Поленья в нише: торцами наружу
    el(9, 1, 2, 13, 4.5, 10, "log", "", "log_end"); el(13.5, 1, 2, 17.5, 4.5, 10, "log", "", "log_end")
    el(18, 1, 2, 22, 4.5, 10, "log", "", "log_end"); el(11, 4.5, 2.5, 15, 8, 10, "log", "", "log_end")
    el(15.5, 4.5, 2.5, 19.5, 8, 10, "log", "", "log_end")
    # Карниз пода
    el(0, 10.5, -0.5, 32, 12, 32, "stone")
    # Свод из кирпича ярусами
    r0 = 14.5
    for (y = 12; y < 28; y += 2) { r = r0 * sqrt(1 - ((y - 12) / 17.5) ^ 2); if (r < 3) r = 3; ring(y, y + 2, r, "brick") }
    ring(28, 29, 4, "brick")
    # Устье: каменная обрамовка арки, внутри жар или темнота
    el(10, 12, 0.5, 12, 21, 5, "stone"); el(20, 12, 0.5, 22, 21, 5, "stone")
    el(10, 21, 0.5, 22, 23, 5, "stone"); el(12, 20, 0.5, 13, 21, 5, "stone"); el(19, 20, 0.5, 20, 21, 5, "stone")
    el(12, 12, 2.4, 20, 20, 2.9, inner, g)
    el(12, 12, 1, 20, 12.5, 2.4, "stone")
    # Дымоход над устьем
    el(13, 23, 2, 19, 31, 8, "brick"); el(12.5, 31, 1.5, 19.5, 32, 8.5, "stone")
    el(14, 31.9, 3, 18, 32, 7, "soot")
    printf "{\"parent\":\"minecraft:block/block\",\"textures\":{%s},\"elements\":[", tex
    for (i = 0; i < n; i++) printf "%s%s\n", (i ? "," : ""), out[i]
    printf "],\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[-3.5,-3.5,0],\"scale\":[0.31,0.31,0.31]},"
    printf "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.12,0.12,0.12]},\"fixed\":{\"translation\":[-4,-4,0],\"scale\":[0.25,0.25,0.25]},"
    printf "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.18,0.18,0.18]},"
    printf "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.2,0.2,0.2]}}}\n"
  }' > "$1"
}

model oven.json 0
model oven_lit.json 1

# Блокстейт: модель рисует главная часть, остальные пустые (RenderShape.INVISIBLE)
{
  echo '{'
  echo '  "variants": {'
  first=1
  for f in north:0 east:90 south:180 west:270; do
    d=${f%%:*}; y=${f##*:}; rot=""; [ "$y" != 0 ] && rot=", \"y\": $y"
    for lit in false true; do
      suffix=""; [ $lit = true ] && suffix=_lit
      [ $first = 0 ] && echo ','
      first=0
      printf '    "facing=%s,lit=%s": { "model": "halgventmod:block/oven%s"%s }' $d $lit "$suffix" "$rot"
    done
  done
  echo
  echo '  }'
  echo '}'
} > ../../blockstates/baking_oven.json
echo '{ "parent": "halgventmod:block/oven" }' > ../item/baking_oven.json
cat > ../../../../data/halgventmod/loot_table/blocks/baking_oven.json <<LOOT
{
  "type": "minecraft:block",
  "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "halgventmod:baking_oven" } ], "conditions": [ { "condition": "minecraft:survives_explosion" }, { "condition": "minecraft:block_state_property", "block": "halgventmod:baking_oven", "properties": { "part": "0" } } ] } ],
  "random_sequence": "halgventmod:blocks/baking_oven"
}
LOOT

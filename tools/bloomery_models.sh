#!/usr/bin/env bash
# Модели сыродутного горна 2×2×3 (холодный и горящий) — ванильные текстуры: массивная глиняная шахта-конус, внизу
# по кругу глиняные сопла, спереди устье для крицы, на шахте два пояса верёвки, наверху колошник с углями.
# Как на фото африканских горнов и на разрезах «руда — древесный уголь — крица — шлак».
# Запуск из корня репозитория: bash tools/bloomery_models.sh
# Пишет models/block/smithing/bloomery_{lower,upper}{,_lit}.json, models/block/empty.json, blockstates/bloomery.json,
# models/item/bloomery.json и loot_table/blocks/bloomery.json. Горн —
# 32×48×32 от главной части (передняя левая нижняя), лицо (устье) — на север (z = 0): нижние 32 px рисует главная
# часть (lower), верхние 16 — часть над ней (upper, её y = 0 — это 32 горна).
set -e
cd "$(dirname "$0")/../src/main/resources/assets/halgventmod/models/block/smithing"

TEX='"particle":"minecraft:block/terracotta","clay":"minecraft:block/terracotta","base":"minecraft:block/packed_mud","band":"minecraft:block/brown_terracotta","rope":"minecraft:block/dark_oak_log","pipe":"minecraft:block/white_terracotta","dark":"minecraft:block/coal_block","glow":"minecraft:block/magma"'

# model <файл> <горит: 0/1> <ярус: lower/upper>
model() {
  awk -v lit="$2" -v tier="$3" -v tex="$TEX" '
  function uv(a, b) { return sprintf("[0,0,%g,%g]", (a > 16 ? 16 : a), (b > 16 ? 16 : b)) }
  # Кубик в координатах горна (y 0–48); в файл идёт только часть своего яруса
  function el(x1, y1, z1, x2, y2, z2, t, extra,   lo, hi, w, h, d) {
    lo = tier == "lower" ? 0 : 32; hi = lo + 32
    if (tier == "upper") hi = 48
    if (y2 <= lo || y1 >= hi) return
    if (y1 < lo) y1 = lo
    if (y2 > hi) y2 = hi
    y1 -= lo; y2 -= lo
    w = x2 - x1; h = y2 - y1; d = z2 - z1
    out[n++] = sprintf("{\"from\":[%g,%g,%g],\"to\":[%g,%g,%g]%s,\"faces\":{" \
      "\"down\":{\"texture\":\"#%s\",\"uv\":%s},\"up\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"north\":{\"texture\":\"#%s\",\"uv\":%s},\"south\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"west\":{\"texture\":\"#%s\",\"uv\":%s},\"east\":{\"texture\":\"#%s\",\"uv\":%s}}}",
      x1, y1, z1, x2, y2, z2, extra, t, uv(w, d), t, uv(w, d), t, uv(w, h), t, uv(w, h), t, uv(d, h), t, uv(d, h))
  }
  # Ярус шахты: восьмигранник радиуса r вокруг (16, 16)
  function ring(y1, y2, r, t) {
    el(16 - r, y1, 16 - r * 0.5, 16 + r, y2, 16 + r * 0.5, t)
    el(16 - r * 0.5, y1, 16 - r, 16 + r * 0.5, y2, 16 + r, t)
    el(16 - r * 0.8, y1, 16 - r * 0.8, 16 + r * 0.8, y2, 16 + r * 0.8, t)
  }
  # Радиус шахты на высоте y: внизу почти во всю ширину, к колошнику уже
  function radius(y) { return 15.2 - y * 0.15 }
  BEGIN {
    glow = ",\"shade\":false,\"neoforge_data\":{\"block_light\":15,\"sky_light\":15}"
    inner = lit == 1 ? "glow" : "dark"; g = lit == 1 ? glow : ""
    # Утоптанная площадка и наплыв глины у основания
    el(0, 0, 0, 32, 1, 32, "base")
    ring(1, 3, radius(1) + 0.6, "band")
    # Шахта ярусами по 2 px
    for (y = 3; y < 46; y += 2) ring(y, y + 2, radius(y + 1), "clay")
    # Колошник: рамка, внутри угли
    s = radius(46) * 0.8; o = radius(46) * 0.5
    el(16 - s, 46, 16 - s, 16 + s, 48, 16 - o, "band"); el(16 - s, 46, 16 + o, 16 + s, 48, 16 + s, "band")
    el(16 - s, 46, 16 - o, 16 - o, 48, 16 + o, "band"); el(16 + o, 46, 16 - o, 16 + s, 48, 16 + o, "band")
    el(16 - o, 46.2, 16 - o, 16 + o, 46.5, 16 + o, inner, g)
    # Устье спереди — арка, сюда выгребают крицу
    f = 16 - radius(5)
    el(12, 1, f - 0.7, 20, 10, f - 0.2, inner, g)
    el(11, 1, f - 1.2, 12, 11, f + 0.5, "band"); el(20, 1, f - 1.2, 21, 11, f + 0.5, "band")
    el(11, 10, f - 1.2, 21, 11.5, f + 0.5, "band"); el(12, 9, f - 0.8, 13, 10, f, "band"); el(19, 9, f - 0.8, 20, 10, f, "band")
    # Сопла по кругу: глиняные трубки торчат наружу (слева, справа, сзади, по задним углам)
    el(-2, 3, 14.5, 2, 6, 17.5, "pipe"); el(29, 3, 14.5, 32, 6, 17.5, "pipe"); el(14.5, 3, 29, 17.5, 6, 32, "pipe")
    el(3, 3, 25, 6.5, 5.5, 28.5, "pipe"); el(25.5, 3, 25, 29, 5.5, 28.5, "pipe")
    el(-2.2, 3.8, 15.3, -2, 5.2, 16.7, inner, g); el(31.8, 3.8, 15.3, 32, 5.2, 16.7, inner, g)
    el(15.3, 3.8, 31.8, 16.7, 5.2, 32, inner, g)
    # Два пояса верёвки
    ring(27, 28.2, radius(27.6) + 0.8, "rope")
    ring(31, 32.2, radius(31.6) + 0.8, "rope")
    printf "{\"parent\":\"minecraft:block/block\",\"textures\":{%s},\"elements\":[", tex
    for (i = 0; i < n; i++) printf "%s%s\n", (i ? "," : ""), out[i]
    printf "],\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[-3.5,-3.5,0],\"scale\":[0.28,0.28,0.28]},"
    printf "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.12,0.12,0.12]},\"fixed\":{\"translation\":[-4,-4,0],\"scale\":[0.25,0.25,0.25]},"
    printf "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.16,0.16,0.16]},"
    printf "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.18,0.18,0.18]}}}\n"
  }' > "$1"
}

model bloomery_lower.json 0 lower
model bloomery_lower_lit.json 1 lower
model bloomery_upper.json 0 upper
model bloomery_upper_lit.json 1 upper
rm -f bloomery.json bloomery_lit.json

# Пустая модель — у частей, что ничего не рисуют
echo '{ "textures": { "particle": "minecraft:block/terracotta" } }' > ../empty.json

# Блокстейт: главная часть — нижние ярусы, часть 8 — верхний, остальные — пусто
assets=../../..
{
  echo '{'
  echo '  "variants": {'
  for f in north:0 east:90 south:180 west:270; do
    d=${f%%:*}; y=${f##*:}; rot=""; [ "$y" != 0 ] && rot=", \"y\": $y"
    for lit in false true; do
      suffix=""; [ $lit = true ] && suffix=_lit
      echo "    \"facing=$d,lit=$lit,part=0\": { \"model\": \"halgventmod:block/smithing/bloomery_lower$suffix\"$rot },"
      echo "    \"facing=$d,lit=$lit,part=8\": { \"model\": \"halgventmod:block/smithing/bloomery_upper$suffix\"$rot },"
    done
  done
  parts=(1 2 3 4 5 6 7 9 10 11)
  for i in "${!parts[@]}"; do
    sep=','; [ $((i + 1)) = ${#parts[@]} ] && sep=''
    echo "    \"part=${parts[$i]}\": { \"model\": \"halgventmod:block/empty\" }$sep"
  done
  echo '  }'
  echo '}'
} > $assets/blockstates/bloomery.json
echo '{ "parent": "halgventmod:block/smithing/bloomery_lower" }' > $assets/models/item/bloomery.json
# Добыча — только с главной части
cat > ../../../../../data/halgventmod/loot_table/blocks/bloomery.json <<LOOT
{
  "type": "minecraft:block",
  "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "halgventmod:bloomery" } ], "conditions": [ { "condition": "minecraft:survives_explosion" }, { "condition": "minecraft:block_state_property", "block": "halgventmod:bloomery", "properties": { "part": "0" } } ] } ],
  "random_sequence": "halgventmod:blocks/bloomery"
}
LOOT

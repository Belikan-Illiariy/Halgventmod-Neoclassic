#!/usr/bin/env bash
# Модели стекловаренной печи 2×2×2 (холодная и горящая) — ванильные текстуры: каменный цоколь-приступок, кирпичный
# корпус, по кругу три арочных рабочих окна (спереди и по бокам), внизу спереди топка, выше — колпак уступами
# и дымник. Как на старых гравюрах стекольных заводов.
# Запуск из корня репозитория: bash tools/glass_furnace_models.sh
# Пишет src/main/resources/assets/halgventmod/models/block/glass_furnace*.json. Модель — от главной части
# (передняя левая нижняя) на 32×32×32: лицо — на север (z = 0), печь уходит на восток (x) и на юг (z).
set -e
cd "$(dirname "$0")/../src/main/resources/assets/halgventmod/models/block"

TEX='"particle":"minecraft:block/bricks","base":"minecraft:block/stone_bricks","body":"minecraft:block/bricks","trim":"minecraft:block/mud_bricks","top":"minecraft:block/deepslate_bricks","dark":"minecraft:block/coal_block","glow":"minecraft:block/magma"'

# model <файл> <горит: 0/1>
model() {
  awk -v lit="$2" -v tex="$TEX" '
  function uv(a, b) { return sprintf("[0,0,%g,%g]", (a > 16 ? 16 : a), (b > 16 ? 16 : b)) }
  function el(x1, y1, z1, x2, y2, z2, t, extra,   w, h, d) {
    w = x2 - x1; h = y2 - y1; d = z2 - z1
    out[n++] = sprintf("{\"from\":[%g,%g,%g],\"to\":[%g,%g,%g]%s,\"faces\":{" \
      "\"down\":{\"texture\":\"#%s\",\"uv\":%s},\"up\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"north\":{\"texture\":\"#%s\",\"uv\":%s},\"south\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"west\":{\"texture\":\"#%s\",\"uv\":%s},\"east\":{\"texture\":\"#%s\",\"uv\":%s}}}",
      x1, y1, z1, x2, y2, z2, extra, t, uv(w, d), t, uv(w, d), t, uv(w, h), t, uv(w, h), t, uv(d, h), t, uv(d, h))
  }
  # Ярус: восьмигранник радиуса r вокруг середины (16, 16)
  function ring(y1, y2, r, t) {
    el(16 - r, y1, 16 - r * 0.5, 16 + r, y2, 16 + r * 0.5, t)
    el(16 - r * 0.5, y1, 16 - r, 16 + r * 0.5, y2, 16 + r, t)
    el(16 - r * 0.8, y1, 16 - r * 0.8, 16 + r * 0.8, y2, 16 + r * 0.8, t)
  }
  # Арочное окно на грани радиуса r: side 0 — спереди (север), 1 — слева (запад), 2 — справа (восток)
  function hole(side, y1, y2, r, half,   a, b) {
    a = 16 - half; b = 16 + half
    if (side == 0) {
      el(a, y1, 16 - r - 0.6, b, y2, 16 - r - 0.1, inner, g)
      el(a - 1, y1 - 0.5, 16 - r - 0.9, a, y2 + 1, 16 - r, "trim"); el(b, y1 - 0.5, 16 - r - 0.9, b + 1, y2 + 1, 16 - r, "trim")
      el(a - 1, y2, 16 - r - 0.9, b + 1, y2 + 1, 16 - r, "trim")
      el(a, y2 - 1, 16 - r - 0.7, a + 1, y2, 16 - r, "trim"); el(b - 1, y2 - 1, 16 - r - 0.7, b, y2, 16 - r, "trim")
    } else {
      x = side == 1 ? 16 - r - 0.6 : 16 + r + 0.1; xo = side == 1 ? 16 - r - 0.9 : 16 + r
      el(x, y1, a, x + 0.5, y2, b, inner, g)
      el(xo, y1 - 0.5, a - 1, xo + 0.9, y2 + 1, a, "trim"); el(xo, y1 - 0.5, b, xo + 0.9, y2 + 1, b + 1, "trim")
      el(xo, y2, a - 1, xo + 0.9, y2 + 1, b + 1, "trim")
    }
  }
  BEGIN {
    glow = ",\"shade\":false,\"neoforge_data\":{\"block_light\":15,\"sky_light\":15}"
    inner = lit == 1 ? "glow" : "dark"; g = lit == 1 ? glow : ""
    # Цоколь-приступок, на нём стоят стеклодувы
    el(0, 0, 0, 32, 2, 32, "base")
    ring(2, 3, 15, "base")
    # Нижний ярус с топкой
    ring(3, 13, 13, "body")
    hole(0, 3, 8, 13, 3)
    # Пояс и ярус рабочих окон
    ring(12.5, 13.5, 13.5, "trim")
    ring(13.5, 20, 12.5, "body")
    hole(0, 14.5, 18.5, 12.5, 2.5); hole(1, 14.5, 18.5, 12.5, 2.5); hole(2, 14.5, 18.5, 12.5, 2.5)
    # Колпак уступами и дымник
    ring(20, 22.5, 11, "body")
    ring(22.5, 25, 9, "body")
    ring(25, 27, 7, "body")
    ring(27, 28.5, 5, "body")
    el(13, 28.5, 13, 19, 31.5, 19, "top")
    el(14, 31.4, 14, 18, 31.6, 18, inner, g)
    printf "{\"parent\":\"minecraft:block/block\",\"textures\":{%s},\"elements\":[", tex
    for (i = 0; i < n; i++) printf "%s%s\n", (i ? "," : ""), out[i]
    printf "],\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[-3.5,-3.5,0],\"scale\":[0.31,0.31,0.31]},"
    printf "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.12,0.12,0.12]},\"fixed\":{\"translation\":[-4,-4,0],\"scale\":[0.25,0.25,0.25]},"
    printf "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.18,0.18,0.18]},"
    printf "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.2,0.2,0.2]}}}\n"
  }' > "$1"
}

model glass_furnace.json 0
model glass_furnace_lit.json 1

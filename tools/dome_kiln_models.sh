#!/usr/bin/env bash
# Модели купольного горна 2×2×2 (целый, горящий, развалившийся) — ванильные текстуры: глиняный купол уступами над
# топкой с продухом наверху, спереди устье с кирпичной аркой.
# Запуск из корня репозитория: bash tools/dome_kiln_models.sh
# Пишет src/main/resources/assets/halgventmod/models/block/dome_kiln*.json. Модель — от главной части (передняя левая
# нижняя) на 32×32×32: лицо (устье) — на север (z = 0), горн уходит на восток (x) и на юг (z).
set -e
cd "$(dirname "$0")/../src/main/resources/assets/halgventmod/models/block"

TEX='"particle":"minecraft:block/packed_mud","ground":"minecraft:block/coarse_dirt","body":"minecraft:block/packed_mud","trim":"minecraft:block/mud_bricks","arch":"minecraft:block/bricks","dark":"minecraft:block/coal_block","glow":"minecraft:block/magma"'

# model <файл> <горит: 0/1> <развалился: 0/1>
model() {
  awk -v lit="$2" -v broken="$3" -v tex="$TEX" '
  # Кубик со всеми гранями; uv — по размеру грани, больше 16 — растягивается
  function uv(a, b) { return sprintf("[0,0,%g,%g]", (a > 16 ? 16 : a), (b > 16 ? 16 : b)) }
  function el(x1, y1, z1, x2, y2, z2, t, top, extra,   w, h, d) {
    w = x2 - x1; h = y2 - y1; d = z2 - z1
    if (top == "") top = t
    out[n++] = sprintf("{\"from\":[%g,%g,%g],\"to\":[%g,%g,%g]%s,\"faces\":{" \
      "\"down\":{\"texture\":\"#%s\",\"uv\":%s},\"up\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"north\":{\"texture\":\"#%s\",\"uv\":%s},\"south\":{\"texture\":\"#%s\",\"uv\":%s}," \
      "\"west\":{\"texture\":\"#%s\",\"uv\":%s},\"east\":{\"texture\":\"#%s\",\"uv\":%s}}}",
      x1, y1, z1, x2, y2, z2, extra, top, uv(w, d), top, uv(w, d), t, uv(w, h), t, uv(w, h), t, uv(d, h), t, uv(d, h))
  }
  # Ярус купола: восьмигранник радиуса r вокруг середины (16, 16) — три пересекающихся бруска
  function ring(y1, y2, r, t) {
    el(16 - r, y1, 16 - r * 0.5, 16 + r, y2, 16 + r * 0.5, t)
    el(16 - r * 0.5, y1, 16 - r, 16 + r * 0.5, y2, 16 + r, t)
    el(16 - r * 0.8, y1, 16 - r * 0.8, 16 + r * 0.8, y2, 16 + r * 0.8, t)
  }
  # Ярус с проёмом спереди: x 13..19 и ближе z = 7 — пусто (там устье топки)
  function ringMouth(y1, y2, r, t,   a, c) {
    el(16 - r, y1, 16 - r * 0.5, 16 + r, y2, 16 + r * 0.5, t)
    a = 16 - r; c = 16 - r * 0.8
    el(16 - r * 0.5, y1, a, 13, y2, 16 + r, t); el(19, y1, a, 16 + r * 0.5, y2, 16 + r, t); el(13, y1, 7, 19, y2, 16 + r, t)
    el(c, y1, c, 13, y2, 32 - c, t); el(19, y1, c, 32 - c, y2, 32 - c, t)
  }
  BEGIN {
    glow = ",\"shade\":false,\"neoforge_data\":{\"block_light\":15,\"sky_light\":15}"
    inner = lit == 1 ? "glow" : "dark"; g = lit == 1 ? glow : ""
    # Утоптанная площадка
    el(1, 0, 1, 31, 1, 31, "ground")
    # Нижний ярус — топка: кольцо пошире, пояс из сырцового кирпича
    ringMouth(1, 8, 13.5, "body")
    ring(7, 8.5, 14, "trim")
    if (broken == 1) {
      # Купол обвалился: обломки внутри и по краю
      ring(8.5, 11, 12.5, "body")
      el(9, 8.5, 9, 15, 10.5, 14, "trim"); el(17, 8.5, 15, 23, 12, 21, "body"); el(12, 8.5, 18, 17, 9.5, 24, "trim")
      el(4, 1, 26, 8, 3, 29, "body"); el(25, 1, 4, 29, 2.5, 8, "trim")
    } else {
      # Купол уступами, наверху продух
      ring(8.5, 13, 12.5, "body")
      ring(13, 17, 11.5, "body")
      ring(17, 20, 10, "body")
      ring(20, 22.5, 8, "body")
      ring(22.5, 24.5, 5.5, "body")
      el(13, 24.5, 13, 19, 25.5, 19, "trim")
      el(14.5, 25.4, 14.5, 17.5, 25.6, 17.5, inner, "", g)
    }
    # Устье топки спереди: щёки, свод, кирпичная арка и огонь (угли) внутри
    el(10.5, 1, 0.5, 13, 8, 5, "body")
    el(19, 1, 0.5, 21.5, 8, 5, "body")
    el(10.5, 8, 0.5, 21.5, 10, 5, "body")
    el(10, 1, 0, 11, 8.5, 1, "arch"); el(21, 1, 0, 22, 8.5, 1, "arch"); el(10, 8.5, 0, 22, 9.5, 1, "arch")
    el(13, 6.5, 0.6, 14, 8, 1.2, "arch"); el(18, 6.5, 0.6, 19, 8, 1.2, "arch")
    el(13, 1, 6.5, 19, 8, 7, inner, "", g)
    el(13, 1, 1, 19, 1.5, 6.5, inner, "", g)
    el(13, 7, 1, 19, 8, 7, "body")
    printf "{\"parent\":\"minecraft:block/block\",\"render_type\":\"minecraft:cutout\",\"textures\":{%s},\"elements\":[", tex
    for (i = 0; i < n; i++) printf "%s%s\n", (i ? "," : ""), out[i]
    printf "],\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[-3.5,-3.5,0],\"scale\":[0.31,0.31,0.31]},"
    printf "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.12,0.12,0.12]},\"fixed\":{\"translation\":[-4,-4,0],\"scale\":[0.25,0.25,0.25]},"
    printf "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.18,0.18,0.18]},"
    printf "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.2,0.2,0.2]}}}\n"
  }' > "$1"
}

model dome_kiln.json 0 0
model dome_kiln_lit.json 1 0
model dome_kiln_broken.json 0 1

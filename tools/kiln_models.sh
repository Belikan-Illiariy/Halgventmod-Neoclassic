#!/usr/bin/env bash
# Модели гончарного горна (малый, большой, горящие и развалившийся) — ванильные текстуры, восьмигранник с куполом.
# Запуск из корня репозитория: bash tools/kiln_models.sh
# Пишет src/main/resources/assets/halgventmod/models/block/kiln_*.json. Лицо горна (топка) — на север.
set -e
cd "$(dirname "$0")/../src/main/resources/assets/halgventmod/models/block"

# Кубик: x1 y1 z1 x2 y2 z2 текстура [доп. поля]
el() {
  local t="#$7"
  printf '{"from":[%s,%s,%s],"to":[%s,%s,%s]%s,"faces":{"down":{"texture":"%s"},"up":{"texture":"%s"},"north":{"texture":"%s"},"south":{"texture":"%s"},"west":{"texture":"%s"},"east":{"texture":"%s"}}}' \
    "$1" "$2" "$3" "$4" "$5" "$6" "${8:-}" $t $t $t $t $t $t
}
# Плоскость лицом на север и юг: x1 y1 x2 y2 z текстура [доп.]
plane_z() {
  printf '{"from":[%s,%s,%s],"to":[%s,%s,%s]%s,"faces":{"north":{"texture":"#%s"},"south":{"texture":"#%s"}}}' \
    "$1" "$2" "$5" "$3" "$4" "$5" "${7:-}" "$6" "$6"
}
# Плоскость лицом на запад и восток: z1 y1 z2 y2 x текстура [доп.]
plane_x() {
  printf '{"from":[%s,%s,%s],"to":[%s,%s,%s]%s,"faces":{"west":{"texture":"#%s"},"east":{"texture":"#%s"}}}' \
    "$5" "$2" "$1" "$5" "$4" "$3" "${7:-}" "$6" "$6"
}

# Светится сам: огонь и угли в топке
GLOW=',"shade":false,"neoforge_data":{"block_light":15,"sky_light":15}'
TEX='"particle":"minecraft:block/mud_bricks","base":"minecraft:block/cobblestone","body":"minecraft:block/mud_bricks","trim":"minecraft:block/bricks","dark":"minecraft:block/coal_block","glow":"minecraft:block/magma","fire":"minecraft:block/fire_0","log":"minecraft:block/spruce_log"'

# kiln <файл> <верх нижнего яруса> <верх топки> <горит: 0/1>
kiln() {
  local name=$1 H=$2 F=$3 lit=$4
  local h2=$((H + 4)) h3=$((H + 7)) h4=$((H + 8)) ch=$((H + 12))
  local inner=dark g=""
  if [ "$lit" = 1 ]; then inner=glow; g="$GLOW"; fi
  {
    printf '{"parent":"minecraft:block/block","render_type":"minecraft:cutout","textures":{%s},"elements":[' "$TEX"
    el 0 0 0 16 2 16 base; echo ','
    # Нижний ярус — восьмигранник, спереди топка
    el 3 2 3 13 $H 14 body; echo ','
    el 1 2 4 15 $H 13 body; echo ','
    el 3 2 1 5.5 $H 3 body; echo ','
    el 10.5 2 1 13 $H 3 body; echo ','
    el 5.5 $F 1 10.5 $H 3 body; echo ','
    el 5.5 2 2.9 10.5 $F 3 $inner "$g"; echo ','
    # Кирпичная арка топки и пояс над ней
    el 5 $F 0.6 11 $((F + 1)) 1 trim; echo ','
    el 5 2 0.6 5.5 $F 1 trim; echo ','
    el 10.5 2 0.6 11 $F 1 trim; echo ','
    # Скругление арки — кирпичи в верхних углах топки
    el 5.5 $((F - 1)) 1 6.5 $F 1.6 trim; echo ','
    el 9.5 $((F - 1)) 1 10.5 $F 1.6 trim; echo ','
    # Пояс — восьмигранник чуть шире тела
    el 2.5 $((H - 1)) 0.5 13.5 $H 14.5 trim; echo ','
    el 0.5 $((H - 1)) 3.5 15.5 $H 13.5 trim; echo ','
    # Купол уступами
    el 3.5 $H 2.5 12.5 $h2 13.5 body; echo ','
    el 2.5 $H 3.5 13.5 $h2 12.5 body; echo ','
    el 5 $h2 4 11 $h3 12 body; echo ','
    el 4 $h2 5 12 $h3 11 body; echo ','
    el 6 $h3 6 10 $h4 10 body; echo ','
    # Глазок над топкой
    el 7 $((H + 1)) 2.4 9 $((H + 2)) 2.5 $inner "$g"; echo ','
    # Труба
    el 6.5 $h4 6.5 9.5 $ch 9.5 trim; echo ','
    el 7 $ch 7 9 $ch.01 9 dark; echo ','
    # Поленница у топки справа
    el 13.2 2 0.5 15.8 3.6 3 log; echo ','
    el 13.6 3.6 0.8 15.4 5 2.8 log
    if [ "$lit" = 1 ]; then
      echo ','; plane_z 6 2 10 $((F - 1)) 2 fire "$g"
      echo ','; plane_x 1.5 2 2.9 $((F - 1)) 8 fire "$g"
      echo ','; el 6 2 1.6 10 2.6 2.8 glow "$g"
    fi
    echo ']}'
  } > "$name.json"
}

kiln kiln_small 8 6 0
kiln kiln_small_lit 8 6 1
kiln kiln_tall 14 7 0
kiln kiln_tall_lit 14 7 1

# Развалившийся горн (общий для малого и большого): низ без купола, кирпичи вокруг
{
  printf '{"parent":"minecraft:block/block","textures":{%s},"elements":[' "$TEX"
  el 0 0 0 16 2 16 base; echo ','
  el 3 2 3 13 6 14 body; echo ','
  el 1 2 4 9 7 13 body; echo ','
  el 9 2 6 15 4 13 body; echo ','
  el 3 2 1 5.5 5 3 body; echo ','
  el 5.5 2 2.9 10.5 4 3 dark; echo ','
  el 5 2 0.6 5.5 4 1 trim; echo ','
  el 10 2 0.5 12 3 2.5 trim ',"rotation":{"angle":22.5,"axis":"y","origin":[11,2,1.5]}'; echo ','
  el 12.5 2 2 14 3 3.5 trim ',"rotation":{"angle":-22.5,"axis":"y","origin":[13,2,3]}'; echo ','
  el 2 2 13.5 4.5 3 15 trim; echo ','
  el 6.5 6 6.5 9.5 7 9.5 trim; echo ','
  el 13.2 2 0.5 15.8 3.6 3 log
  echo ']}'
} > kiln_broken.json

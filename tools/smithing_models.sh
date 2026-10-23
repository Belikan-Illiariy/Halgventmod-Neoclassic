#!/usr/bin/env bash
# Блокстейты, модели блоков и предметов-блоков, таблицы добычи кузнечества (smithing/SmithingBlocks).
# Жилы, ил и россыпь — куб со своей текстурой на каждую ступень богатства (рисует tools/icon_painter/SmithIcons.cs,
# VeinTextures); станции — модели Conquest Reforged.
# Запуск из корня репозитория: bash tools/smithing_models.sh
set -e
assets=src/main/resources/assets/halgventmod
loot=src/main/resources/data/halgventmod/loot_table/blocks
mkdir -p "$assets/blockstates" "$assets/models/block/smithing" "$assets/models/item" "$loot"

loot_self() { # $1 — блок
  cat > "$loot/$1.json" <<EOF
{
  "type": "minecraft:block",
  "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "halgventmod:$1" } ], "conditions": [ { "condition": "minecraft:survives_explosion" } ] } ],
  "random_sequence": "halgventmod:blocks/$1"
}
EOF
}

# ---- Жилы, ил, россыпь: richness 0–3
for vein in iron_vein copper_vein tin_vein lead_vein silver_vein gold_vein zinc_vein cinnabar_vein limestone_vein bog_silt gold_placer; do
  {
    echo '{'
    echo '  "variants": {'
    for r in 0 1 2 3; do
      sep=','; [ $r = 3 ] && sep=''
      echo "    \"richness=$r\": { \"model\": \"halgventmod:block/smithing/${vein}_$r\" }$sep"
    done
    echo '  }'
    echo '}'
  } > "$assets/blockstates/$vein.json"
  for r in 0 1 2 3; do
    echo "{ \"parent\": \"minecraft:block/cube_all\", \"textures\": { \"all\": \"halgventmod:block/smithing/${vein}_$r\" } }" \
      > "$assets/models/block/smithing/${vein}_$r.json"
  done
  echo "{ \"parent\": \"halgventmod:block/smithing/${vein}_3\" }" > "$assets/models/item/$vein.json"
  loot_self "$vein"
done

# ---- Станции: модель по сторонам света; $1 — блок, $2 — доп. свойства «ключ=знач:модель;…» или одна модель
facings=(north east south west)
rot() { case $1 in north) echo "";; east) echo ', "y": 90';; south) echo ', "y": 180';; west) echo ', "y": 270';; esac; }

# station <блок> <модель предмета> <состояние1> <модель1> [<состояние2> <модель2> …]; состояние без facing, "" — нет
station() {
  local block=$1 item=$2; shift 2
  local lines=()
  while [ $# -gt 0 ]; do
    local state=$1 model=$2; shift 2
    for f in "${facings[@]}"; do
      local key="facing=$f"; [ -n "$state" ] && key="$state,facing=$f"
      lines+=("    \"$key\": { \"model\": \"$model\"$(rot $f) }")
    done
  done
  {
    echo '{'
    echo '  "variants": {'
    local n=${#lines[@]}
    for i in "${!lines[@]}"; do
      if [ $((i + 1)) -lt $n ]; then echo "${lines[$i]},"; else echo "${lines[$i]}"; fi
    done
    echo '  }'
    echo '}'
  } > "$assets/blockstates/$block.json"
  echo "{ \"parent\": \"$item\" }" > "$assets/models/item/$block.json"
  loot_self "$block"
}

# Модели Conquest — через свою обёртку с render_type cutout: иначе прозрачное в текстурах рисуется сплошным
cr() { # $1 — путь модели Conquest без conquest:block/
  local name=cr_$(basename "$1")
  echo "{ \"parent\": \"conquest:block/$1\", \"render_type\": \"minecraft:cutout\" }" > "$assets/models/block/smithing/$name.json"
  echo "halgventmod:block/smithing/$name"
}
# Угольная куча: что видно — pile, горит — lit (дым частицами)
pile=()
for lit in false true; do
  pile+=("lit=$lit,pile=0" "$(cr stack_of_coal_layer_bottom_1)")
  pile+=("lit=$lit,pile=1" "$(cr stack_of_firewood_half1_1)")
  pile+=("lit=$lit,pile=2" "$(cr dirt_path_layer_height8)")
  pile+=("lit=$lit,pile=3" "$(cr pile_of_coal)")
done
station charcoal_pile "$(cr stack_of_firewood_half1_1)" "${pile[@]}"
station bloomery "$(cr roman_ovens/large_red_plastered_roman_oven_firewood)" \
  "lit=false" "$(cr roman_ovens/large_red_plastered_roman_oven_firewood)" "lit=true" "$(cr roman_ovens/large_red_plastered_roman_oven_firewood_coals)"
station cupel "$(cr cooking_brazier)" "lit=false" "$(cr cooking_brazier)" "lit=true" "$(cr cooking_brazier)"
station smithy_forge halgventmod:block/smithing/smithy_forge "lit=false" halgventmod:block/smithing/smithy_forge "lit=true" halgventmod:block/smithing/smithy_forge_lit
station smith_anvil "$(cr anvil_on_log)" "" "$(cr anvil_on_log)"
station sharpening_wheel "$(cr grindstone)" "" "$(cr grindstone)"
m=halgventmod:block/smithing
station cementation_box $m/cementation_lower "half=lower,lit=false" $m/cementation_lower "half=lower,lit=true" $m/cementation_lower_lit \
  "half=upper,lit=false" $m/cementation_upper "half=upper,lit=true" $m/cementation_upper
# Две части — добыча только с нижней
cat > "$loot/cementation_box.json" <<EOF
{
  "type": "minecraft:block",
  "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "halgventmod:cementation_box" } ], "conditions": [ { "condition": "minecraft:survives_explosion" }, { "condition": "minecraft:block_state_property", "block": "halgventmod:cementation_box", "properties": { "half": "lower" } } ] } ],
  "random_sequence": "halgventmod:blocks/cementation_box"
}
EOF
station casting_table "$(cr cutting_table)" "" "$(cr cutting_table)"
station mortar "$(cr bronze_cooking_pot)" "" "$(cr bronze_cooking_pot)"
station sluice "$(cr tools/wooden_kneading_trough)" "" "$(cr tools/wooden_kneading_trough)"
echo "готово"

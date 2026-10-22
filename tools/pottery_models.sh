#!/usr/bin/env bash
# Модели посуды и сырца: плоская иконка, как у обычного предмета (textures/item/pottery/ware, greenware;
# рисует tools/icon_painter/Vessels.cs). Форма по вокселям видна только на полке для сырца и в окне круга.
# Иконку выбирает свойство halgventmod:look (client/pottery/PotteryIcons.java) — порядок состояний тот же.
# Запуск из корня репозитория: bash tools/pottery_models.sh
set -e
dir=src/main/resources/assets/halgventmod/models/item
mkdir -p "$dir/pottery"

ware_states=(terracotta underfired overfired burnished black)
green_states=(wet dry cracked burnished)
forms=(cup bowl jug pot amphora lamp)
declare -A item_of=([cup]=clay_cup [bowl]=clay_bowl [jug]=clay_jug [pot]=clay_pot [amphora]=amphora [lamp]=oil_lamp)

# Тело модели: плоская иконка
body() { # $1 — текстура
  cat <<EOF
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "$1" }
EOF
}

variant() { # $1 — файл, $2 — текстура
  { echo "{"; body "$2"; echo "}"; } > "$1"
}

# $1 — файл предмета, $2 — текстура по умолчанию, далее — пары «номер модель»
with_overrides() {
  local file=$1 tex=$2; shift 2
  {
    echo "{"
    body "$tex" | sed '$ s/$/,/'
    echo '  "overrides": ['
    local first=1
    while [ $# -gt 0 ]; do
      [ $first = 1 ] || echo ","
      printf '    { "predicate": { "halgventmod:look": %s }, "model": "%s" }' "$1" "$2"
      first=0; shift 2
    done
    echo
    echo "  ]"
    echo "}"
  } > "$file"
}

for form in "${forms[@]}"; do
  item=${item_of[$form]}
  args=()
  # Номер: состояние; горящий светильник — плюс число состояний, треснувшая — плюс два числа состояний
  count=${#ware_states[@]}
  suffixes=("" _cracked)
  [ "$form" = lamp ] && suffixes=("" _lit _cracked)
  for suffix in "${suffixes[@]}"; do
    case $suffix in
      _lit) n=$count ;;
      _cracked) n=$((2 * count)) ;;
      *) n=0 ;;
    esac
    for state in "${ware_states[@]}"; do
      tex="halgventmod:item/pottery/ware/${form}_${state}${suffix}"
      model="halgventmod:item/pottery/${item}_${state}${suffix}"
      variant "$dir/pottery/${item}_${state}${suffix}.json" "$tex"
      [ $n -gt 0 ] && args+=("$n" "$model")
      n=$((n + 1))
    done
  done
  with_overrides "$dir/$item.json" "halgventmod:item/pottery/ware/${form}_terracotta" "${args[@]}"
done

args=()
n=0
for form in "${forms[@]}"; do
  for state in "${green_states[@]}"; do
    tex="halgventmod:item/pottery/greenware/${form}_${state}"
    model="halgventmod:item/pottery/greenware_${form}_${state}"
    variant "$dir/pottery/greenware_${form}_${state}.json" "$tex"
    [ $n -gt 0 ] && args+=("$n" "$model")
    n=$((n + 1))
  done
done
with_overrides "$dir/greenware.json" "halgventmod:item/pottery/greenware/cup_wet" "${args[@]}"
echo "ok"

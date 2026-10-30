# ТЗ: бой по образцу Kingdom Come Combat

Источник: мод **Kingdom Come Combat** (github.com/xxxxxxur/minecraft-mod---kingdomcome-combat, лицензия MIT,
© 2026 ardrxrd). Берём устройство боя, анимации и код по частям, переписывая под NeoForge 1.21.1 и наши системы.
Сам мод целиком не ставим и не встраиваем: он требует Forgified Fabric API, Cloth Config и Player Animation Library
и приносит свои выносливость, броню, кровь, предметы и книги умений, которые спорят с нашими.

## Что остаётся нашим

| Наше | Где | Почему |
| --- | --- | --- |
| Выносливость | `needs/Stamina` | так решено: удары, блоки, уклон тратят нашу выносливость |
| Броня в 4 слоя, типы урона, зоны | `armor/`, `combat/DamagePipeline`, `HitZone` | вся экипировка мода на ней |
| Боль, болевой шок, «Повержен», кровотечение | `needs/Pain`, `downed/`, `combat/BleedEvent` | связаны со здоровьем и лечением |
| Классы оружия и их числа | `data/halgventmod/stats/weapons/*.json`, `WeaponProfiles` | расширяем полями KCC, а не заменяем |
| Стража, преступления, НПС | `npc/` | получат ИИ боя из этапа 6 |
| Опьянение, качество оружия, затупление | `brewing/Intoxication`, `quality/` | множители поверх |

## Что не берём

- **Звуки мода**: файлы с именами `Henry_sword_hit…`, `SFX_…` похожи на вырезанные из самой Kingdom Come — лицензия MIT
  репозитория на них не распространяется. Звуки — наши (`sound/CombatSounds`).
- Ручная пушка, боссы, «Hardship», книги и опыт умений (Modonomicon), дракон и прочие мобы-боссы, конь в бою —
  не наш сеттинг или отдельная задача.

## Этапы

Каждый этап собирается и работает сам; механика старого боя меняется только там, где этап её заменяет.

### Этап 1. Анимации KCC вместо наших поз — сделано (0.1.4-в работе)
Код: `client/anim/AnimClip`, `AnimLibrary`, `CombatClips`, `FirstPersonCombat`; миксины `LivingEntityRendererMixin`
(кость body), `ItemInHandLayerMixin` (кость item). Отражение от лат: `CombatHitPayload.metal`, `Combat.deflected` / `ring`,
`client/particle/SparkParticle`, `registry/ModParticles`.
- Загрузчик анимаций Bedrock-формата (`format_version 1.8.0`: ключи `vector` / `pre` / `post`, `catmullrom` и
  линейные) — свой код, файлы анимаций — из KCC (`player_animations/*.json`, MIT; в `assets/halgventmod/combat_anim/`).
- Перевод в позу ванильной модели — как в KCC (`VanillaSkeletonGeckoAnimationApplier`): `torso` → корпус,
  `head`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`; поворот X и Z — как есть, Y — к исходному; сдвиг Y — с минусом;
  кость `body` — поворот всей модели; кость `item` — поворот предмета в кулаке (удар лезвием).
- Выбор анимации: сторона удара → `attack_<сторона>`; одноручное — без приставки, заряженный — `_heavy`,
  двуручный меч — `_longsword`, древковое, двуручный топор и молот — `_longweapon` (нет — `_longsword`);
  блок — `block_<сторона>`; оглушение — `stunded`.
- Сшивка с нашими фазами: окно попадания берётся из кости `hitbox` (масштаб не ноль) — до него замах, в нём удар,
  после — возврат. Время каждой части растягивается под наши длительности фаз; заряд — держится кадр начала удара.
- **Стойка**: оружие в руке и боевой режим — `stance_<сторона>` по выбранной мышью стороне (как в KCD видно,
  откуда будет удар).
- **От первого лица** — свои руки: модель рук игрока в позе той же анимации, камера в глазах (как «firstperson»
  в KCC), обе руки у двуручного; ванильная рука в бою не рисуется.
- Укол и пинок — пока наши позы (в KCC своих нет).

### Этап 2. Попадание по анимированному клинку — сделано
Код: `combat/anim/BladePose` (цепочка глаза → корпус → плечо → кулак → предмет), `combat/anim/CombatMoves` (выбор удара,
общий), `combat/anim/AnimLibrary` (анимации и на сервере — из файла мода), `Combat.blade`; части тела — `HitZone`
(11 частей, покрытие бронёй по части), `FaceCover.armored`. Столкновение клинков — ещё не сделано (этап 4).
- Клинок на сервере — по кадрам той же анимации (как `AnimatedAttackHitboxLibrary`): кость руки и `item` → отрезок
  клинка, трасса между тиками — вместо дуг `SwingPath`. Анимации лежат и на сервере (`data/`).
- Части тела цели по позе (`HumanoidHurtboxLibrary`): голова, шея, плечо, рука, кисть, грудь, живот, бедро, колено,
  голень, ступня → наши зоны брони.
- Столкновение клинков: удар в клинок противника — звон и отброс (`hit_weapon` в KCC).

### Этап 3. Стойки, направления и захват цели — сделано
Код: `client/CombatInput` (стойка мышью, виртуальная мышь в захвате), `client/CombatLock`, `mixin/MouseHandlerMixin`,
`client/CombatHud.star`; сервер — `CombatInputPayload.STANCE_ON/OFF`, `CombatState.engaged`, флаг `ENGAGED`.
- Индикатор сторон у прицела (как в KCD): какая сторона выбрана, откуда идёт удар противника
  (`IncomingAttackWarningState`).
- Захват цели (средняя кнопка) и мягкий захват (Tab): камера и корпус держат противника, переключение жестом мыши.
- Скорость и движение в стойке (`CombatMovementConfig`): с оружием наготове шаг медленнее, бег закрыт.

### Этап 4. Блок, идеальный блок, мастер-удар — сделано
Идеальный блок = наше парирование (`Defense.PARRY_WINDOW`); мастер-удар = рипост с анимацией `master_strike_*`
(`CombatMoves.attack(..., master)`, `Combat.MASTER_DAMAGE`, `Defense` — обычный блок не держит); отдача неидеального блока —
`block_unprefect_*` (`CombatState.reaction`, клиент); столкновение клинков — `Combat.clash`, исход `CLASH`.
- Блок по стороне; **идеальный блок** (поднят в последний миг) — без урона и выносливости, открывает
  **мастер-удар** (`master_strike`) — контрудар, который не блокируется обычно.
- Неидеальный блок — анимации `block_unprefect_1/2`, отдача выносливостью и ударом (`base_impact`,
  `block_impact_mitigation` в профиле оружия).

### Этап 5. Комбо — сделано (добивания — решено не делать)
Код: `combat/ComboMoves` (data/halgventmod/combat_combos/*.json), `CombatState.history/comboMove/move`, `Combat.begin` / `strike` /
`release`; клиент — `CombatMoves.move`, анимация жертвы и реакция тела (`CombatAnimation.hurt`).
- Комбо по последовательности направлений (`ComboMoveConfigs`, data-pack JSON): своя анимация, урон, зоны,
  притяжение и выпад, несколько ударов в цепочке; комбо по классам оружия.
- Реакция на попадание по частям тела (`ClientHitReactionState`); добивание поверженного (`execution_*`) — в связке
  с нашим «Повержен».

### Этап 6. ИИ боя для людей — сделано
Код: `combat/CombatAiGoal` (умения GUARD / VETERAN / COMMON), `combat/CombatAiAttach` (зомби, скелеты, разбойники, пиглины),
`combat/Fighter` (выносливость не-игроков); `Combat.input`, `Defense` — для любого `LivingEntity`. Профили ИИ из JSON — пока в коде.
- `HumanoidCombatAiTicker`: стражники, бандиты, скелеты и разбойники держат дистанцию, блокируют, бьют с разных
  сторон, делают комбо, отступают уставшими. Профили — data-pack JSON. Стражники НПС — на нём.

### Этап 7. Кровь на снаряжении и травмы — сделано
Код: `combat/Blood` (компонент `halgventmod:blood`, отмывание), `combat/Injuries` + `InjuryEffect`, `ModEffects.INJURY_*`;
клиент — пятна в `ArmorCompositor.stain`, `client/BloodClient` (подсказка, капля в ячейке, капли с оружия).
- Окровавленные текстуры брони и оружия (`BloodiedEquipment`), травмы частей тела — в связке с нашей болью.

## Лицензия
В репозиторий кладём `docs/licenses/KingdomComeCombat-LICENSE.txt` (текст MIT с копирайтом) и упоминание в
CHANGELOG. Взятые файлы и куски кода помечаем в Javadoc: «по образцу KCC (MIT)».

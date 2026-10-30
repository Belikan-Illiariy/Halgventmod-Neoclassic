package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayDeque;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Состояние бойца (ТЗ боя, 3) — вложение ModAttachments.COMBAT на любом LivingEntity, на диск не пишется: после входа — покой.
 * На сервере — правда, на клиенте — копия из CombatStatePayload (для анимаций и интерфейса).
 */
public final class CombatState {
    public Phase phase = Phase.IDLE;
    public AttackKind attack = AttackKind.SLASH;
    public StrikeSide side = StrikeSide.RIGHT;      // сторона удара (по мыши); укол — без стороны
    public StrikeSide blockSide = StrikeSide.UP;    // сторона блока (по мыши)
    public boolean heavy;        // заряженный
    public boolean charging;     // кнопка удара ещё зажата
    public boolean riposte;
    public boolean counter;
    public int combo;            // номер удара в комбо, 0 — первый
    public long phaseStart;      // серверный тик начала фазы
    public long phaseEnd;        // и конца
    public boolean blocking;     // блок поднят (Defense)
    public long blockStart;      // с этого тика блок держит удары (подъём кончился); окно парирования — от него
    public long blockLockedUntil;
    public long riposteUntil;
    public long sprintLockUntil; // после удара с разбега бег закрыт (и на клиенте — для запрета бега)

    // Только сервер
    public final IntSet hit = new IntOpenHashSet(); // кого уже задел текущий удар
    public Item weapon = Items.AIR;                 // чем начат удар: сменил предмет — удар отменён
    public WeaponProfile.Attack current;            // параметры текущего удара
    public WeaponProfile profile;
    public boolean bash;                            // удар щитом: бьёт и изнашивается щит во второй руке
    public long windupEnd;                          // конец замаха без заряда (заряд тянет phaseEnd дальше)
    public AttackKind queued;                       // кнопку удара нажали во время удара — следующий в серии, или null
    public StrikeSide queuedSide = StrikeSide.RIGHT;
    public boolean queuedCharging;                  // и её ещё держат
    public Vec3[] bladePrev;                        // плечо и точки клинка на прошлом тике удара (трасса)
    public boolean blockHeld;                       // кнопка блока зажата
    public long gripUntil;                          // меняет хват: ни бить, ни блокировать до этого тика

    // Только клиент (анимации, client/CombatAnimation)
    public Phase prevPhase = Phase.IDLE;            // фаза до нынешней: возврат после пинка — своя поза
    public long guardSince;                         // когда поднялся блок
    public boolean gripSeen;                        // какой хват был в руке в прошлом кадре
    public long gripChangedAt = Long.MIN_VALUE / 2; // когда сменился
    public long hitStopAt = Long.MIN_VALUE / 2;     // удар попал: анимация на миг замирает с этого тика
    public long recoilAt = Long.MIN_VALUE / 2;      // клинок отбросило (блок, парирование): с этого тика
    public float recoilPower;                       // и насколько сильно, 0…1
    public long engagedUntil;                       // в бою до этого тика: оружие в стойке (client/anim/CombatClips)
    public boolean engaged;                         // захватил цель: оружие в стойке state.side (сервер и клиенты)
    public String move = "";                        // приём (комбо) текущего удара — для клиентов (ComboMoves.Combo.move)
    @Nullable public ComboMoves.Combo comboMove;    // только сервер: приём текущего удара
    public final ArrayDeque<StrikeSide> history = new ArrayDeque<>(); // стороны последних ударов
    public long lastStrikeAt = Long.MIN_VALUE / 2;  // когда начат последний удар
    public String reaction = "";                    // только клиент: короткая анимация отдачи (неидеальный блок)
    public long reactionAt = Long.MIN_VALUE / 2;    // … с этого тика
    @Nullable public HitZone hurtZone;              // только клиент: куда попали (реакция тела)
    public long hurtAt = Long.MIN_VALUE / 2;        // … когда
    // Выносливость бойца-не-игрока (НПС, мобы с ИИ боя — Fighter): шкала, пауза до восстановления, одышка до тика
    public float stamina = Fighter.NPC_MAX;
    public int staminaDelay;
    public long windedUntil;
    long inputTick;                                 // ограничение частоты пакетов ввода
    int inputCount;

    public boolean idle() {
        return phase == Phase.IDLE;
    }
}

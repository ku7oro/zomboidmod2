package com.example.zomboidmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.function.BiPredicate;

/**
 * Здоровье в духе Project Zomboid (по pzwiki.net: Health, First Aid, Injured, Pain).
 * Времена заживления даны в игровых часах PZ; 1 час PZ = hourSeconds реальных секунд
 * (в PZ по умолчанию 1 игровой день = 1 реальный час, то есть 150 секунд на час).
 * Остальные числа (боль, штрафы, скорости кровотечения) - оценки: в вики их нет.
 */
public class Wounds {
    /** 1 игровой час PZ в секундах. Менять командой /zomboid hour <секунды> (для проверки). */
    public static volatile int hourSeconds = 150;

    private static final float BANDAGE_LIFE_HOURS = 10f;
    private static final float PAINKILLER_HOURS = 2f;
    private static final float ANTIBIOTIC_HOURS = 6f;

    private static final int TOTAL_WEIGHT;

    static {
        int t = 0;
        for (BodyPart bp : BodyPart.values()) t += bp.weight;
        TOTAL_WEIGHT = t;
    }

    public static PlayerHealth health(ServerPlayerEntity p) {
        return HealthState.get(p.getServer()).of(p.getUuid());
    }

    private static void markDirty(ServerPlayerEntity p) {
        HealthState.get(p.getServer()).markDirty();
    }

    public static void reset(ServerPlayerEntity p) {
        HealthState st = HealthState.get(p.getServer());
        st.players.remove(p.getUuid());
        st.markDirty();
        sync(p);
    }

    // ===== Получение ран =====
    public static void onDamage(ServerPlayerEntity p, DamageSource source, float amount) {
        if (amount < 1.0f) return;
        Random r = p.getRandom();
        Entity attacker = source.getAttacker();

        if (source.isIn(DamageTypeTags.IS_FALL)) {
            BodyPart leg = randomLeg(r);
            if (amount >= 9.0f && r.nextFloat() < 0.2f) addFracture(p, BodyPart.UPPER_TORSO);
            else if (amount >= 5.0f && r.nextFloat() < 0.4f) addFracture(p, leg);
            else if (amount >= 3.0f) addWound(p, leg, r.nextFloat() < 0.3f ? WoundType.DEEP : WoundType.CUT);
            else addWound(p, leg, WoundType.SCRATCH);
        } else if (source.isIn(DamageTypeTags.IS_FIRE)) {
            if (r.nextFloat() < 0.3f) addWound(p, randomPart(r), WoundType.BURN);
        } else if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            addWound(p, randomPart(r), amount >= 5.0f ? WoundType.DEEP : WoundType.CUT);
        } else if (attacker instanceof ZombieEntity) {
            zombieHit(p, attacker, r);
        } else if (attacker instanceof IronGolemEntity && r.nextFloat() < 0.25f) {
            BodyPart limb = r.nextBoolean() ? randomLeg(r) : randomArm(r);
            addFracture(p, limb); // удар тупым предметом
        } else if (attacker instanceof LivingEntity living) {
            if (living.getMainHandStack().getItem() instanceof AxeItem) {
                addWound(p, randomPart(r), WoundType.DEEP); // удар топором
            } else if (r.nextFloat() < 0.6f) {
                addWound(p, randomPart(r),
                        amount >= 6.0f ? WoundType.DEEP : (amount >= 3.0f ? WoundType.CUT : WoundType.SCRATCH));
            }
        }
    }

    /** Шансы по pzwiki: спереди царапина 57% / рваная 29% / укус 14%; сбоку 31/48/21; сзади укусов больше. */
    private static void zombieHit(ServerPlayerEntity p, Entity zombie, Random r) {
        int side = facing(p, zombie); // 0 спереди, 1 сбоку, 2 сзади
        float scratch = side == 0 ? 0.57f : (side == 1 ? 0.31f : 0.25f);
        float cut = side == 0 ? 0.29f : (side == 1 ? 0.48f : 0.40f);
        float roll = r.nextFloat();
        WoundType type = roll < scratch ? WoundType.SCRATCH : (roll < scratch + cut ? WoundType.CUT : WoundType.BITE);
        addWound(p, randomPart(r), type);
        // Вирус Нокс: царапина 7%, рваная рана 25%, укус 100%
        float virus = type == WoundType.BITE ? 1.0f : (type == WoundType.CUT ? 0.25f : 0.07f);
        if (r.nextFloat() < virus) infectZombie(p);
    }

    private static int facing(ServerPlayerEntity p, Entity attacker) {
        double dx = attacker.getX() - p.getX();
        double dz = attacker.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        Vec3d look = p.getRotationVec(1.0f);
        double llen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-4 || llen < 1.0E-4) return 1;
        double dot = (look.x * dx + look.z * dz) / (len * llen);
        return dot > 0.5 ? 0 : (dot < -0.5 ? 2 : 1);
    }

    private static BodyPart randomPart(Random r) {
        int n = r.nextInt(TOTAL_WEIGHT);
        for (BodyPart bp : BodyPart.values()) {
            n -= bp.weight;
            if (n < 0) return bp;
        }
        return BodyPart.UPPER_TORSO;
    }

    private static BodyPart randomLeg(Random r) {
        BodyPart[] legs = {BodyPart.L_THIGH, BodyPart.R_THIGH, BodyPart.L_SHIN, BodyPart.R_SHIN,
                BodyPart.L_FOOT, BodyPart.R_FOOT};
        return legs[r.nextInt(legs.length)];
    }

    private static BodyPart randomArm(Random r) {
        BodyPart[] arms = {BodyPart.L_UPPER_ARM, BodyPart.R_UPPER_ARM, BodyPart.L_FOREARM, BodyPart.R_FOREARM,
                BodyPart.L_HAND, BodyPart.R_HAND};
        return arms[r.nextInt(arms.length)];
    }

    public static void addWound(ServerPlayerEntity p, BodyPart part, WoundType type) {
        PartState s = health(p).parts[part.ordinal()];
        WoundType cur = s.wound();
        if (cur != WoundType.NONE && cur.rank >= type.rank) return; // уже есть не слабее
        s.clearWound();
        s.type = type.ordinal();
        s.healLeft = type.rollHours(p.getRandom());
        p.sendMessage(Text.literal("Рана: " + type.title + " (" + part.title.toLowerCase() + ")"), true);
        markDirty(p);
        sync(p);
    }

    public static void addFracture(ServerPlayerEntity p, BodyPart part) {
        if (!part.canFracture()) return;
        PartState s = health(p).parts[part.ordinal()];
        if (s.fracture) return;
        s.fracture = true;
        s.splinted = false;
        s.fractureLeft = 50f + p.getRandom().nextFloat() * 30f; // 50-80 часов
        p.sendMessage(Text.literal("Перелом! (" + part.title.toLowerCase() + ")"), true);
        markDirty(p);
        sync(p);
    }

    public static void infectZombie(ServerPlayerEntity p) {
        PlayerHealth h = health(p);
        if (h.zombieInfected) return;
        h.zombieInfected = true;
        h.zombieHours = 0;
        h.zombieDeathAt = 60f + p.getRandom().nextInt(25); // в среднем 3 суток (72 часа)
        markDirty(p);
        sync(p);
    }

    // ===== Лечение =====

    /** Лечение предметом из руки: часть тела подбирается автоматически. */
    public static boolean treat(ServerPlayerEntity p, MedItem.Kind kind) {
        PlayerHealth h = health(p);
        int idx;
        switch (kind) {
            case BANDAGE -> idx = pick(h, (i, s) -> s.type != 0 && (!s.bandaged || s.dirty));
            case ANTISEPTIC -> idx = pick(h, (i, s) -> s.type != 0 && !s.disinfected);
            case SUTURE -> idx = pick(h, (i, s) -> s.wound() == WoundType.DEEP && !s.stitched);
            case SPLINT -> idx = pick(h, (i, s) -> s.fracture && !s.splinted && BodyPart.values()[i].canSplint());
            default -> idx = 0;
        }
        if (idx < 0) return fail(p, noTarget(kind));
        return treatPart(p, kind, idx);
    }

    private static String noTarget(MedItem.Kind kind) {
        return switch (kind) {
            case BANDAGE -> "Нечего перевязывать";
            case ANTISEPTIC -> "Нечего обрабатывать";
            case SUTURE -> "Зашивать нечего (нужна глубокая рана)";
            case SPLINT -> "Нет перелома, который можно зашинировать";
            default -> "Не подходит";
        };
    }

    private static float bandageLife(WoundType t) {
        return switch (t) {
            case DEEP -> BANDAGE_LIFE_HOURS / 2f;
            case BITE, BURN -> BANDAGE_LIFE_HOURS * 0.6f;
            default -> BANDAGE_LIFE_HOURS;
        };
    }

    /** Лечение конкретной части тела (из меню панели здоровья или из руки). */
    public static boolean treatPart(ServerPlayerEntity p, MedItem.Kind kind, int idx) {
        PlayerHealth h = health(p);
        PartState s = h.parts[idx];
        BodyPart part = BodyPart.values()[idx];
        String name = part.title.toLowerCase();
        switch (kind) {
            case BANDAGE -> {
                if (s.type == 0) return fail(p, "На этой части тела нет раны");
                if (s.bandaged && !s.dirty) return fail(p, "Рана уже перевязана");
                s.bandaged = true;
                s.dirty = false;
                s.bandageLeft = bandageLife(s.wound());
                done(p, "Бинт наложен: " + name);
                return true;
            }
            case ANTISEPTIC -> {
                if (s.type == 0) return fail(p, "На этой части тела нет раны");
                if (s.disinfected) return fail(p, "Рана уже обработана");
                s.disinfected = true;
                done(p, "Рана обработана: " + name);
                return true;
            }
            case SUTURE -> {
                if (s.wound() != WoundType.DEEP) return fail(p, "Зашивать можно только глубокую рану");
                if (s.stitched) return fail(p, "Рана уже зашита");
                s.stitched = true; // шов закрывает рану сразу и останавливает кровь
                done(p, "Швы наложены: " + name);
                return true;
            }
            case SPLINT -> {
                if (!s.fracture) return fail(p, "Здесь нет перелома");
                if (!part.canSplint()) return fail(p, "Перелом торса и головы шиной не зафиксировать");
                if (s.splinted) return fail(p, "Шина уже наложена");
                s.splinted = true;
                done(p, "Шина наложена: " + name);
                return true;
            }
            case PAINKILLERS -> {
                h.painLeft = PAINKILLER_HOURS;
                done(p, "Боль утихла");
                return true;
            }
            case ANTIBIOTICS -> {
                h.antibioticLeft = ANTIBIOTIC_HOURS;
                done(p, "Антибиотики приняты: инфекция в ранах отступит (вирус Нокс они не лечат)");
                return true;
            }
        }
        return false;
    }

    /** Лечение из меню панели здоровья: проверяем предмет в инвентаре и тратим его. */
    public static void treatFromMenu(ServerPlayerEntity p, int part, int action) {
        if (part < 0 || part >= BodyPart.values().length) return;
        if (action < 0 || action >= MedItem.Kind.values().length) return;
        MedItem.Kind kind = MedItem.Kind.values()[action];
        Item item = ZomboidMod.itemFor(kind);
        if (!p.isCreative() && !takeItem(p, item, true)) {
            fail(p, "В инвентаре нет нужного предмета");
            return;
        }
        if (treatPart(p, kind, part)) {
            if (kind.drinkSound) {
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK,
                        SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            if (!p.isCreative()) takeItem(p, item, false);
        }
    }

    private static boolean takeItem(ServerPlayerEntity p, Item item, boolean onlyCheck) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) {
                if (!onlyCheck) st.decrement(1);
                return true;
            }
        }
        return false;
    }

    /** Самая срочная подходящая часть тела: сначала кровоточащие, потом по тяжести, шея в приоритете. */
    private static int pick(PlayerHealth h, BiPredicate<Integer, PartState> filter) {
        int best = -1, bestScore = -1;
        for (int i = 0; i < h.parts.length; i++) {
            PartState s = h.parts[i];
            if (!filter.test(i, s)) continue;
            int score = (s.isBleeding() ? 100 : 0) + s.wound().rank * 10 + (s.fracture ? 5 : 0)
                    + (i == BodyPart.NECK.ordinal() ? 50 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static boolean fail(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg), true);
        return false;
    }

    private static void done(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg), true);
        markDirty(p);
        sync(p);
    }

    // ===== Время =====

    /** Раз в секунду. */
    public static void tickSecond(ServerPlayerEntity p, int serverTicks) {
        step(p, serverTicks, 1.0 / hourSeconds, false);
    }

    /** После сна: проматываем прошедшие игровые часы одним шагом (сон ускоряет выздоровление). */
    public static void advanceSleep(ServerPlayerEntity p, double hours) {
        step(p, p.getServer().getTicks(), Math.min(hours, 12.0), true);
    }

    /** Потеря здоровья в % от максимума. Смерть наступает при нуле. */
    private static void drain(ServerPlayerEntity p, double percent) {
        if (percent <= 0) return;
        float hp = p.getHealth() - (float) (p.getMaxHealth() * percent / 100.0);
        if (hp <= 0.05f) p.damage(p.getDamageSources().generic(), Float.MAX_VALUE);
        else p.setHealth(hp);
    }

    private static void step(ServerPlayerEntity p, int serverTicks, double dtHours, boolean sleeping) {
        PlayerHealth h = health(p);
        Random r = p.getRandom();
        double seconds = dtHours * hourSeconds;
        int sec = serverTicks / 20;

        int thirst = ZomboidMod.getThirst(p);
        int food = p.getHungerManager().getFoodLevel();
        boolean wellFed = food >= 16 && thirst >= 60;
        boolean starving = food < 6 || thirst < 20;

        h.painLeft = Math.max(0f, h.painLeft - (float) dtHours);
        h.antibioticLeft = Math.max(0f, h.antibioticLeft - (float) dtHours);

        double bleedPct = 0;     // потеря здоровья от кровотечения, % в секунду
        double drainPct = 0;     // прочая постоянная потеря, % в секунду
        double[] groupPain = new double[BodyGroup.values().length];
        int penalty = 0;
        boolean legFracUns = false, legFracSpl = false, armFracUns = false, armFracSpl = false, torsoFrac = false;

        for (int i = 0; i < h.parts.length; i++) {
            PartState s = h.parts[i];
            BodyPart part = BodyPart.values()[i];
            WoundType t = s.wound();
            double pain = 0;

            if (t != WoundType.NONE) {
                // бинт со временем становится грязным
                if (s.bandaged && !s.dirty) {
                    s.bandageLeft -= (float) dtHours;
                    if (s.bandageLeft <= 0) {
                        s.dirty = true;
                        p.sendMessage(Text.literal("Бинт испачкался, замените: " + part.title.toLowerCase()), false);
                    }
                }

                // кровотечение (рана на шее кровит в разы сильнее)
                if (s.isBleeding()) {
                    double rate = t.bleedPerSecond();
                    if (t == WoundType.DEEP && s.bandaged) rate *= 0.5;
                    if (part == BodyPart.NECK) rate *= 10.0;
                    bleedPct += rate;
                }
                // глубокая рана без швов выматывает даже когда кровь остановлена
                if (t == WoundType.DEEP && !s.stitched) drainPct += 0.03;

                // заражение раны
                if (!s.infected && h.antibioticLeft <= 0f) {
                    double perHour = !s.bandaged ? 0.03 : (s.dirty ? 0.05 : 0.003);
                    if (s.disinfected) perHour *= 0.2;
                    if (t == WoundType.DEEP || t == WoundType.BITE) perHour *= 1.5;
                    if (r.nextDouble() < Math.min(1.0, perHour * dtHours)) {
                        s.infected = true;
                        p.sendMessage(Text.literal("Рана заражена: " + part.title.toLowerCase()), false);
                    }
                }
                if (s.infected) {
                    if (!s.bandaged || s.dirty) drainPct += 0.01; // совсем небольшая потеря здоровья
                    if (h.antibioticLeft > 0f && r.nextDouble() < Math.min(1.0, dtHours / 3.0)) {
                        s.infected = false;
                        p.sendMessage(Text.literal("Инфекция в ране отступила: " + part.title.toLowerCase()), false);
                    }
                }

                // заживление
                double rate;
                if (t == WoundType.DEEP) {
                    if (s.stitched) rate = s.bandaged ? 2.0 : 1.0;   // бинт сокращает срок вдвое
                    else rate = s.bandaged ? 0.025 : 0.01;           // без швов: около месяца
                } else {
                    rate = s.bandaged ? 1.0 : 0.25;
                }
                if (s.infected) rate *= 0.5;
                if (wellFed) rate *= 1.3;
                s.healLeft -= (float) (rate * dtHours);
                if (s.healLeft <= 0f) {
                    p.sendMessage(Text.literal("Рана зажила: " + part.title.toLowerCase()), false);
                    s.clearWound();
                    t = WoundType.NONE;
                }
            }

            if (t != WoundType.NONE) {
                pain += t.pain;
                if (t == WoundType.DEEP && s.stitched) pain *= 0.5;
                if (s.bandaged) pain *= 0.6; // повязка уменьшает боль
                if (s.infected) pain *= s.bandaged ? 1.3 : 2.0; // непролеченная инфекция удваивает боль
                penalty += t.capPenalty;
            }

            // перелом
            if (s.fracture) {
                double fr = s.splinted ? 1.0 : 0.5;
                if (wellFed) fr *= 1.3;
                s.fractureLeft -= (float) (fr * dtHours);
                if (s.fractureLeft <= 0f) {
                    s.fracture = false;
                    s.splinted = false;
                    p.sendMessage(Text.literal("Перелом сросся: " + part.title.toLowerCase()), false);
                } else {
                    if (part == BodyPart.UPPER_TORSO) {
                        pain += 45;
                        penalty += 40; // перелом верхней части туловища держит максимум здоровья около 60%
                        torsoFrac = true;
                    } else {
                        pain += s.splinted ? 18 : 40;
                        penalty += s.splinted ? 10 : 15;
                        if (part.isLeg()) {
                            if (s.splinted) legFracSpl = true; else legFracUns = true;
                        } else if (part.isArm()) {
                            if (s.splinted) armFracSpl = true; else armFracUns = true;
                        }
                    }
                }
            }

            groupPain[part.group.ordinal()] += pain;
        }

        // Боль: общая сумма, обезболивающее вычитает 35 пунктов
        double sumPain = 0;
        for (double g : groupPain) sumPain += g;
        double factor = 1.0;
        if (h.painLeft > 0f && sumPain > 0) factor = Math.max(0.0, (sumPain - 35.0) / sumPain);
        for (int g = 0; g < groupPain.length; g++) groupPain[g] *= factor;
        h.painValue = (int) Math.round(Math.min(100.0, sumPain * factor));

        // Уровень кровотечения
        h.bleedLevel = bleedPct >= 0.4 ? 3 : (bleedPct >= 0.15 ? 2 : (bleedPct > 0 ? 1 : 0));

        // Максимум здоровья из-за травм (шея ограничение игнорирует: её кровь выедает здоровье напрямую)
        h.capPercent = Math.max(20, 100 - penalty);
        float maxHp = p.getMaxHealth();
        float capHp = maxHp * h.capPercent / 100f;
        if (p.getHealth() > capHp) {
            p.setHealth(Math.max(capHp, p.getHealth() - maxHp * 0.0003f * (float) seconds));
        }

        // Вирус Нокс
        double knoxHours = 0;
        if (h.zombieInfected) {
            h.zombieHours += (float) dtHours;
            knoxHours = h.zombieHours;
            if (knoxHours >= 24) drain(p, 0.0004 * (knoxHours - 24) * seconds); // потеря здоровья всё быстрее
        }

        // Потери здоровья от ран
        drain(p, (bleedPct + drainPct) * seconds);

        // ===== Дебафы =====
        double hpPct = p.getHealth() / maxHp * 100.0;
        int slow = -1, weak = -1, miningFatigue = -1;

        // Ноги: боль и переломы замедляют
        double leg = groupPain[BodyGroup.LEG.ordinal()];
        if (legFracUns) slow = 3;
        else if (legFracSpl) slow = 1;
        if (leg >= 75) slow = Math.max(slow, 2);
        else if (leg >= 50) slow = Math.max(slow, 1);
        else if (leg >= 20) slow = Math.max(slow, 0);

        // Руки: боль и переломы снижают скорость и силу ударов
        double arm = groupPain[BodyGroup.ARM.ordinal()];
        if (armFracUns) {
            miningFatigue = 2;
            weak = Math.max(weak, 1);
        } else if (armFracSpl) {
            miningFatigue = 0;
        }
        if (arm >= 75) {
            miningFatigue = Math.max(miningFatigue, 1);
            weak = Math.max(weak, 1);
        } else if (arm >= 50) {
            miningFatigue = Math.max(miningFatigue, 0);
            weak = Math.max(weak, 0);
        } else if (arm >= 20) {
            miningFatigue = Math.max(miningFatigue, 0);
        }

        // Торс: выносливость
        double torso = groupPain[BodyGroup.TORSO.ordinal()];
        if (torsoFrac) weak = Math.max(weak, 0);
        if (torso >= 75) weak = Math.max(weak, 1);
        else if (torso >= 50) weak = Math.max(weak, 0);

        // Голова: тошнота при сильной боли
        double head = groupPain[BodyGroup.HEAD.ordinal()];
        if (head >= 50 && sec % 30 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, true, false, false));
        }

        // Моодл "Травмирован": по общему здоровью (<60 / <40 / <25)
        if (hpPct < 40) weak = Math.max(weak, 1);
        else if (hpPct < 60) weak = Math.max(weak, 0);
        if (hpPct < 25) slow = Math.max(slow, 0);

        // Моодл "Кровотечение": со 2 уровня сила падает
        if (h.bleedLevel == 3) weak = Math.max(weak, 1);
        else if (h.bleedLevel == 2) weak = Math.max(weak, 0);

        // Вирус Нокс: через сутки болезнь, на 2-е сутки сильнее
        if (knoxHours >= 48) weak = Math.max(weak, 1);
        else if (knoxHours >= 24) weak = Math.max(weak, 0);
        if (knoxHours >= 24 && sec % 15 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false, false));
        }

        if (slow >= 0) effect(p, StatusEffects.SLOWNESS, slow);
        if (weak >= 0) effect(p, StatusEffects.WEAKNESS, weak);
        if (miningFatigue >= 0) effect(p, StatusEffects.MINING_FATIGUE, miningFatigue);

        // ===== Восстановление здоровья =====
        boolean canHeal = bleedPct == 0 && knoxHours < 24 && !starving;
        if (canHeal && p.getHealth() < capHp) {
            double pct = 2.0; // % максимума за игровой час
            if (wellFed) pct *= 1.3;
            if (food < 12 || thirst < 40) pct *= 0.5;
            double now = p.getHealth() / maxHp * 100.0;
            if (now < 10 || now > 80) pct *= 0.5; // быстрее всего между 10% и 80%
            if (sleeping) pct *= 3.0;
            p.setHealth(Math.min(capHp, p.getHealth() + (float) (maxHp * pct / 100.0 * dtHours)));
        }

        // Смерть от вируса Нокс
        if (h.zombieInfected && h.zombieHours >= h.zombieDeathAt) {
            p.damage(p.getDamageSources().generic(), Float.MAX_VALUE);
        }

        markDirty(p);
        sync(p);
    }

    private static void effect(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        p.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
    }

    // ===== Синхронизация с клиентом =====
    public static void sync(ServerPlayerEntity p) {
        PlayerHealth h = health(p);
        StringBuilder sb = new StringBuilder();
        for (PartState s : h.parts) {
            int code = s.type
                    | (s.bandaged ? 1 << 4 : 0)
                    | (s.dirty ? 1 << 5 : 0)
                    | (s.stitched ? 1 << 6 : 0)
                    | (s.infected ? 1 << 7 : 0)
                    | (s.fracture ? 1 << 8 : 0)
                    | (s.splinted ? 1 << 9 : 0)
                    | (s.disinfected ? 1 << 10 : 0)
                    | (s.isBleeding() ? 1 << 11 : 0);
            sb.append(code).append(',');
        }
        sb.append(h.zombieInfected ? 1 : 0).append(',')
          .append(h.painLeft > 0f ? 1 : 0).append(',')
          .append(h.capPercent).append(',')
          .append(h.painValue).append(',')
          .append(h.bleedLevel);
        ServerPlayNetworking.send(p, new WoundsPayload(sb.toString()));
    }

    // ===== Команды =====
    public static String describe(ServerPlayerEntity p) {
        PlayerHealth h = health(p);
        StringBuilder sb = new StringBuilder("Состояние:");
        boolean any = false;
        for (int i = 0; i < h.parts.length; i++) {
            PartState s = h.parts[i];
            if (!s.hasAnything()) continue;
            any = true;
            sb.append("\n").append(BodyPart.values()[i].title).append(": ");
            if (s.type != 0) {
                sb.append(s.wound().title);
                if (s.isBleeding()) sb.append(", кровь");
                if (s.bandaged) sb.append(s.dirty ? ", грязный бинт" : ", бинт");
                if (s.stitched) sb.append(", швы");
                if (s.disinfected) sb.append(", обработана");
                if (s.infected) sb.append(", ИНФЕКЦИЯ");
            }
            if (s.fracture) sb.append(s.type != 0 ? "; " : "").append(s.splinted ? "перелом (в шине)" : "перелом");
        }
        if (!any) sb.append("\nРан нет");
        if (h.zombieInfected) sb.append("\nЗаражение вирусом: да");
        if (h.painLeft > 0f) sb.append("\nОбезболивающее действует ещё ").append(Math.round(h.painLeft * 10) / 10.0).append(" ч");
        return sb.toString();
    }

    public static void testWounds(ServerPlayerEntity p) {
        addWound(p, BodyPart.HEAD, WoundType.SCRATCH);
        addWound(p, BodyPart.UPPER_TORSO, WoundType.CUT);
        addWound(p, BodyPart.L_FOREARM, WoundType.DEEP);
        addWound(p, BodyPart.R_UPPER_ARM, WoundType.BURN);
        addFracture(p, BodyPart.L_SHIN);
        addWound(p, BodyPart.R_THIGH, WoundType.CUT);
        addWound(p, BodyPart.NECK, WoundType.SCRATCH);
    }
}

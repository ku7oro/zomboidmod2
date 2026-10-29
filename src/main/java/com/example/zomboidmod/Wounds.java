package com.example.zomboidmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.function.Predicate;

/** Система ран и лечения в духе Project Zomboid. */
public class Wounds {
    // ===== Настройки (в секундах) =====
    private static final int BANDAGE_DIRTY_AFTER = 300; // бинт пачкается
    private static final int FRACTURE_HEAL = 1800;      // перелом срастается в шине
    private static final int PAINKILLER_TIME = 300;     // действие обезболивающего

    // Вирус зомби
    private static final int VIRUS_WEAK_AT = 180;
    private static final int VIRUS_SICK_AT = 360;
    private static final int VIRUS_DEATH_AT = 600;

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
            BodyPart leg = r.nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
            if (amount >= 5.0f && r.nextFloat() < 0.35f) addFracture(p, leg);
            else if (amount >= 3.0f) addWound(p, leg, WoundType.CUT);
            else addWound(p, leg, WoundType.SCRATCH);
        } else if (source.isIn(DamageTypeTags.IS_FIRE)) {
            if (r.nextFloat() < 0.3f) addWound(p, randomPart(r), WoundType.BURN);
        } else if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            addWound(p, randomPart(r), amount >= 5.0f ? WoundType.DEEP : WoundType.CUT);
        } else if (attacker instanceof ZombieEntity) {
            float roll = r.nextFloat();
            WoundType type = roll < 0.25f ? WoundType.BITE : (roll < 0.55f ? WoundType.CUT : WoundType.SCRATCH);
            addWound(p, randomPart(r), type);
            float virus = type == WoundType.BITE ? 1.0f : (type == WoundType.CUT ? 0.25f : 0.07f);
            if (r.nextFloat() < virus) infectZombie(p);
        } else if (attacker instanceof LivingEntity) {
            if (r.nextFloat() < 0.6f) {
                addWound(p, randomPart(r),
                        amount >= 6.0f ? WoundType.DEEP : (amount >= 3.0f ? WoundType.CUT : WoundType.SCRATCH));
            }
        }
    }

    private static BodyPart randomPart(Random r) {
        int n = r.nextInt(100);
        if (n < 10) return BodyPart.HEAD;
        if (n < 45) return BodyPart.TORSO;
        if (n < 60) return BodyPart.LEFT_ARM;
        if (n < 75) return BodyPart.RIGHT_ARM;
        if (n < 88) return BodyPart.LEFT_LEG;
        return BodyPart.RIGHT_LEG;
    }

    public static void addWound(ServerPlayerEntity p, BodyPart part, WoundType type) {
        PartState s = health(p).parts[part.ordinal()];
        WoundType cur = s.wound();
        if (cur != WoundType.NONE && cur.rank >= type.rank) return; // уже есть не слабее
        s.clearWound();
        s.type = type.ordinal();
        s.bleedLeft = type.bleedSeconds;
        s.heal = type.healSeconds;
        p.sendMessage(Text.literal("Рана: " + type.title + " (" + part.title.toLowerCase() + ")"), true);
        markDirty(p);
        sync(p);
    }

    public static void addFracture(ServerPlayerEntity p, BodyPart part) {
        PartState s = health(p).parts[part.ordinal()];
        if (s.fracture) return;
        s.fracture = true;
        s.splinted = false;
        s.fractureHeal = FRACTURE_HEAL;
        p.sendMessage(Text.literal("Перелом! (" + part.title.toLowerCase() + ")"), true);
        markDirty(p);
        sync(p);
    }

    public static void infectZombie(ServerPlayerEntity p) {
        PlayerHealth h = health(p);
        if (h.zombieInfected) return;
        h.zombieInfected = true;
        h.zombieTimer = 0;
        markDirty(p);
        sync(p);
    }

    // ===== Лечение =====
    public static boolean treat(ServerPlayerEntity p, MedItem.Kind kind) {
        PlayerHealth h = health(p);
        switch (kind) {
            case BANDAGE -> {
                int i = pick(h, s -> s.type != 0 && (!s.bandaged || s.dirty));
                if (i < 0) return fail(p, "Нечего перевязывать");
                PartState s = h.parts[i];
                s.bandaged = true;
                s.dirty = false;
                s.bandageAge = 0;
                done(p, "Бинт наложен: " + BodyPart.values()[i].title.toLowerCase());
                return true;
            }
            case ANTISEPTIC -> {
                int i = pick(h, s -> s.type != 0 && !s.disinfected);
                if (i < 0) return fail(p, "Нечего обрабатывать");
                h.parts[i].disinfected = true;
                done(p, "Рана обработана: " + BodyPart.values()[i].title.toLowerCase());
                return true;
            }
            case SUTURE -> {
                int i = pick(h, s -> s.wound() == WoundType.DEEP && !s.stitched);
                if (i < 0) return fail(p, "Зашивать нечего (нужна глубокая рана)");
                h.parts[i].stitched = true;
                done(p, "Швы наложены: " + BodyPart.values()[i].title.toLowerCase());
                return true;
            }
            case SPLINT -> {
                int i = pick(h, s -> s.fracture && !s.splinted);
                if (i < 0) return fail(p, "Нет перелома без шины");
                h.parts[i].splinted = true;
                done(p, "Шина наложена: " + BodyPart.values()[i].title.toLowerCase());
                return true;
            }
            case PAINKILLERS -> {
                h.painLeft = PAINKILLER_TIME;
                done(p, "Боль утихла");
                return true;
            }
            case ANTIBIOTICS -> {
                boolean any = false;
                for (PartState s : h.parts) {
                    if (s.infected) {
                        s.infected = false;
                        any = true;
                    }
                }
                if (!any) return fail(p, "Инфекции в ранах нет");
                done(p, "Инфекция в ранах побеждена");
                return true;
            }
        }
        return false;
    }

    /** Выбирает самую срочную подходящую часть тела: сначала кровоточащие, потом по тяжести. */
    private static int pick(PlayerHealth h, Predicate<PartState> filter) {
        int best = -1, bestScore = -1;
        for (int i = 0; i < h.parts.length; i++) {
            PartState s = h.parts[i];
            if (!filter.test(s)) continue;
            int score = (s.isBleeding() ? 100 : 0) + s.wound().rank * 10 + (s.fracture ? 5 : 0);
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

    // ===== Раз в секунду =====
    public static void tickSecond(ServerPlayerEntity p, int serverTicks) {
        PlayerHealth h = health(p);
        int sec = serverTicks / 20;
        boolean suppressed = h.painLeft > 0;
        if (h.painLeft > 0) h.painLeft--;

        double bleed = 0;
        int legPain = 0, armPain = 0, headPain = 0, torsoPain = 0;
        boolean legFracture = false, armFracture = false, anyInfected = false;
        Random r = p.getRandom();

        for (int i = 0; i < h.parts.length; i++) {
            PartState s = h.parts[i];
            BodyPart part = BodyPart.values()[i];
            WoundType t = s.wound();

            if (t != WoundType.NONE) {
                // бинт пачкается со временем
                if (s.bandaged && !s.dirty) {
                    s.bandageAge++;
                    if (s.bandageAge >= BANDAGE_DIRTY_AFTER) {
                        s.dirty = true;
                        p.sendMessage(Text.literal("Бинт испачкался: " + part.title.toLowerCase()), false);
                    }
                }

                // кровотечение
                if (s.bleedLeft > 0) s.bleedLeft--;
                if (s.isBleeding()) bleed += bleedRate(s, t);

                // заражение раны
                if (!s.infected && sec % 30 == 0 && !(s.bandaged && !s.dirty)) {
                    float chance = s.disinfected ? 0.01f : 0.04f;
                    if (t == WoundType.DEEP || t == WoundType.BITE) chance *= 2;
                    if (r.nextFloat() < chance) {
                        s.infected = true;
                        p.sendMessage(Text.literal("Рана заражена: " + part.title.toLowerCase()
                                + " (нужны антибиотики)"), false);
                    }
                }

                // заживление
                if (s.infected) {
                    anyInfected = true;
                } else {
                    int step = (s.bandaged && !s.dirty) ? 2 : 1;
                    if (t == WoundType.DEEP && !s.stitched) step = (sec % 4 == 0) ? 1 : 0;
                    s.heal -= step;
                    if (s.heal <= 0) {
                        s.clearWound();
                        p.sendMessage(Text.literal("Рана зажила: " + part.title.toLowerCase()), false);
                    }
                }
            }

            int pain = s.wound().rank;

            // перелом
            if (s.fracture) {
                if (s.splinted) {
                    s.fractureHeal--;
                    if (s.fractureHeal <= 0) {
                        s.fracture = false;
                        s.splinted = false;
                        p.sendMessage(Text.literal("Перелом срёсся: " + part.title.toLowerCase()), false);
                    }
                }
                pain += s.splinted ? 2 : 4;
                if (!s.splinted) {
                    if (part.isLeg()) legFracture = true;
                    if (part.isArm()) armFracture = true;
                }
            }

            if (part.isLeg()) legPain += pain;
            else if (part.isArm()) armPain += pain;
            else if (part == BodyPart.HEAD) headPain += pain;
            else torsoPain += pain;
        }

        // кровопотеря
        h.bleedAcc += bleed;
        if (h.bleedAcc >= 1.0) {
            h.bleedAcc -= 1.0;
            p.damage(p.getDamageSources().generic(), 1.0f);
        }

        // инфекция в ранах
        if (anyInfected) {
            effect(p, StatusEffects.WEAKNESS, 1);
            if (sec % 20 == 0) p.damage(p.getDamageSources().generic(), 1.0f);
            if (sec % 40 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false, false));
            }
        }

        // боль от ран (обезболивающее её глушит, но не неподвижный перелом ноги или руки)
        if (legFracture) effect(p, StatusEffects.SLOWNESS, 2);
        else if (!suppressed) {
            if (legPain >= 3) effect(p, StatusEffects.SLOWNESS, 1);
            else if (legPain >= 1) effect(p, StatusEffects.SLOWNESS, 0);
        }
        if (armFracture) {
            effect(p, StatusEffects.MINING_FATIGUE, 1);
            effect(p, StatusEffects.WEAKNESS, 0);
        } else if (!suppressed) {
            if (armPain >= 3) {
                effect(p, StatusEffects.MINING_FATIGUE, 1);
                effect(p, StatusEffects.WEAKNESS, 0);
            } else if (armPain >= 1) {
                effect(p, StatusEffects.MINING_FATIGUE, 0);
            }
        }
        if (!suppressed && headPain >= 2 && sec % 20 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, true, false, false));
        }
        if (!suppressed && torsoPain >= 2) effect(p, StatusEffects.WEAKNESS, 0);

        // вирус зомби
        if (h.zombieInfected) {
            h.zombieTimer++;
            if (h.zombieTimer >= VIRUS_WEAK_AT) effect(p, StatusEffects.WEAKNESS, 0);
            if (h.zombieTimer >= VIRUS_SICK_AT) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false, false));
                if (h.zombieTimer % 10 == 0) p.damage(p.getDamageSources().generic(), 1.0f);
            }
            if (h.zombieTimer >= VIRUS_DEATH_AT) {
                p.damage(p.getDamageSources().generic(), Float.MAX_VALUE);
            }
        }

        sync(p);
    }

    private static double bleedRate(PartState s, WoundType t) {
        return switch (t) {
            case SCRATCH -> 0.05;
            case CUT -> 0.10;
            case DEEP -> s.bandaged ? 0.12 : 0.25;
            case BITE -> 0.15;
            default -> 0.0;
        };
    }

    private static void effect(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amplifier) {
        p.addStatusEffect(new StatusEffectInstance(effect, 60, amplifier, true, false, true));
    }

    // ===== Синхронизация с клиентом (для кукольного HUD) =====
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
        sb.append(h.zombieInfected ? 1 : 0).append(',').append(h.painLeft > 0 ? 1 : 0);
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
        if (h.painLeft > 0) sb.append("\nОбезболивающее действует ещё ").append(h.painLeft).append(" сек");
        return sb.toString();
    }

    public static void testWounds(ServerPlayerEntity p) {
        addWound(p, BodyPart.HEAD, WoundType.SCRATCH);
        addWound(p, BodyPart.TORSO, WoundType.CUT);
        addWound(p, BodyPart.LEFT_ARM, WoundType.DEEP);
        addWound(p, BodyPart.RIGHT_ARM, WoundType.BURN);
        addFracture(p, BodyPart.LEFT_LEG);
        addWound(p, BodyPart.RIGHT_LEG, WoundType.CUT);
    }
}

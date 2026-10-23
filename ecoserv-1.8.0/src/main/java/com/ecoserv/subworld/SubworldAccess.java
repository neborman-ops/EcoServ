package com.ecoserv.subworld;

import com.ecoserv.dates.AgeFilter;
import com.ecoserv.dates.DatesManager;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.role.RoleManager;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/** Доступ к подмиру (п. 3): всегда, за оплату, за достижение, наградой в квестах, по роли + возраст. */
public final class SubworldAccess {

    /** Итог проверки. */
    public static final class Result {
        public final boolean ok;
        /** Почему нельзя (для игрока), null — можно. */
        @Nullable
        public final String reason;
        /** Можно купить доступ (цена). */
        public final boolean canBuy;

        private Result(boolean ok, @Nullable String reason, boolean canBuy) {
            this.ok = ok;
            this.reason = reason;
            this.canBuy = canBuy;
        }

        static Result yes() {
            return new Result(true, null, false);
        }

        static Result no(String reason) {
            return new Result(false, reason, false);
        }

        static Result buy(String reason) {
            return new Result(false, reason, true);
        }
    }

    private SubworldAccess() {
    }

    public static Result check(EntityPlayerMP player, Subworld sw) {
        if (Perms.isAdmin(player)) {
            return Result.yes();
        }
        if (!sw.enabled) {
            return Result.no("&cПодмир «" + sw.name + "&c» закрыт.");
        }
        AgeFilter age = AgeFilter.parse(sw.age);
        if (age != null && !DatesManager.checkAge(player.getUniqueID(), age)) {
            return Result.no("&cПодмир «" + sw.name + "&c» доступен по возрасту: &f" + age.describe()
                    + (DatesManager.hasBirthday(player.getUniqueID()) ? "" : " &7(укажи день рождения: /birthday)"));
        }
        String uuid = player.getUniqueID().toString();
        if (SubworldRegistry.unlocked(uuid, sw.id)) {
            return Result.yes();
        }
        switch (sw.access) {
            case Subworld.ACCESS_PRICE:
                return Result.buy("&eДоступ в «" + sw.name + "&e» стоит &6" + Fmt.money(sw.price) + "&e.");
            case Subworld.ACCESS_ADVANCEMENT:
                if (hasAdvancement(player, sw.advancement)) {
                    return Result.yes();
                }
                return Result.no("&cНужно достижение: &f" + advancementTitle(player, sw.advancement));
            case Subworld.ACCESS_QUEST:
                return Result.no("&cДоступ в «" + sw.name + "&c» открывается наградой в квестах.");
            case Subworld.ACCESS_ROLE:
                if (!sw.roles.isEmpty() && RoleManager.hasAny(player, sw.roles)) {
                    return Result.yes();
                }
                return Result.no("&cНужна роль: &f" + String.join(", ", sw.roles));
            default:
                return Result.yes();
        }
    }

    /** Купить доступ (для «за оплату»). */
    public static boolean buy(EntityPlayerMP player, Subworld sw) {
        if (!SubworldRegistry.isCurrent(sw) || SubworldRegistry.loadFailed()
                || !Subworld.ACCESS_PRICE.equals(sw.access) || SubworldRegistry.unlocked(player.getUniqueID().toString(), sw.id)) {
            // удалённый подмир (старое меню) или реестр, который нельзя сохранить, — денег не берём
            return false;
        }
        if (sw.price > 0 && !EconomyManager.take(player.getUniqueID(), sw.price)) {
            return false;
        }
        SubworldRegistry.unlock(player.getUniqueID().toString(), sw.id);
        return true;
    }

    public static boolean hasAdvancement(EntityPlayerMP player, String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        try {
            Advancement adv = player.getServerWorld().getMinecraftServer().getAdvancementManager()
                    .getAdvancement(new ResourceLocation(id.trim()));
            if (adv == null) {
                return false;
            }
            AdvancementProgress progress = player.getAdvancements().getProgress(adv);
            return progress != null && progress.isDone();
        } catch (Exception e) {
            return false;
        }
    }

    public static String advancementTitle(EntityPlayerMP player, String id) {
        try {
            Advancement adv = player.getServerWorld().getMinecraftServer().getAdvancementManager()
                    .getAdvancement(new ResourceLocation(id.trim()));
            if (adv != null && adv.getDisplay() != null) {
                return adv.getDisplay().getTitle().getUnformattedText();
            }
        } catch (Exception ignored) {
            // id как есть
        }
        return id;
    }

    /** Строка статуса для меню. */
    public static String status(EntityPlayerMP player, Subworld sw) {
        Result r = check(player, sw);
        if (r.ok) {
            return "&aдоступен";
        }
        if (r.canBuy) {
            return "&eза &6" + Fmt.money(sw.price);
        }
        return "&cзакрыт";
    }
}

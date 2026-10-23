package com.ecoserv.command;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.PlayerStats;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.server.RewardItems;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /ecoadmin rewards — награды за игру и ежедневки прямо из игры (или RCON).
 * Значения пишутся в config/ecoserv/ecoserv.cfg, предметы — в rewards.json.
 */
public final class RewardsCommand {

    private RewardsCommand() {
    }

    public static void handle(ICommandSender sender, String[] args) {
        // args[0] = "rewards"
        if (args.length < 2) {
            overview(sender);
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        if ("afk".equals(kind)) {
            if (args.length < 3) {
                ChatUtil.send(sender, "&cИспользование: &f/ecoadmin rewards afk <минут, 0 = не проверять>");
                return;
            }
            set(sender, EcoConfig.CAT_REWARDS, "afk_minutes", args[2]);
            return;
        }
        if ("reset".equals(kind)) {
            if (args.length < 3) {
                ChatUtil.send(sender, "&cИспользование: &f/ecoadmin rewards reset <ник> &7— снова дать забрать ежедневку");
                return;
            }
            UUID id = PlayerUtil.uuidOf(args[2]);
            if (id == null) {
                ChatUtil.send(sender, "&cИгрок &f" + args[2] + " &cне найден.");
                return;
            }
            PlayerStats stats = StatsStore.get(id);
            stats.lastDailyDay = -1L;
            StatsStore.markDirty();
            EntityPlayerMP online = PlayerUtil.online(id);
            if (online != null) {
                com.ecoserv.network.PacketHandler.sendPlayerState(online);
            }
            ChatUtil.send(sender, "&aИгрок &f" + args[2] + " &aснова может забрать ежедневную награду.");
            return;
        }
        if (!"playtime".equals(kind) && !"daily".equals(kind)) {
            overview(sender);
            return;
        }
        boolean daily = "daily".equals(kind);
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        String prefix = daily ? "daily_" : "playtime_";

        if ("on".equals(action) || "off".equals(action)) {
            set(sender, EcoConfig.CAT_REWARDS, prefix + "enabled", "on".equals(action) ? "true" : "false");
            return;
        }
        if (("money".equals(action) || "amount".equals(action)) && args.length > 3) {
            set(sender, EcoConfig.CAT_REWARDS, prefix + "amount", args[3]);
            return;
        }
        if (!daily && "minutes".equals(action) && args.length > 3) {
            set(sender, EcoConfig.CAT_REWARDS, "playtime_minutes", args[3]);
            return;
        }
        if (daily && "streak".equals(action) && args.length > 3) {
            set(sender, EcoConfig.CAT_REWARDS, "daily_streak_bonus", args[3]);
            if (args.length > 4) {
                set(sender, EcoConfig.CAT_REWARDS, "daily_streak_max", args[4]);
            }
            return;
        }
        if ("item".equals(action) || "items".equals(action)) {
            items(sender, args, daily);
            return;
        }
        ChatUtil.send(sender, "&cИспользование:");
        if (daily) {
            ChatUtil.sendRaw(sender, " &f/ecoadmin rewards daily on|off | money <сумма> | streak <бонус> [макс. дней] | item add|clear|list");
        } else {
            ChatUtil.sendRaw(sender, " &f/ecoadmin rewards playtime on|off | minutes <N> | money <сумма> | item add|clear|list");
        }
    }

    private static void items(ICommandSender sender, String[] args, boolean daily) {
        String op = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "list";
        String title = daily ? "ежедневной награды" : "награды за игру";
        if ("add".equals(op)) {
            if (!(sender instanceof EntityPlayerMP)) {
                ChatUtil.send(sender, "&cПредмет добавляется из руки — выполни в игре.");
                return;
            }
            ItemStack held = ((EntityPlayerMP) sender).getHeldItemMainhand();
            if (held.isEmpty()) {
                ChatUtil.send(sender, "&cВозьми предмет в руку (количество тоже учитывается).");
                return;
            }
            RewardItems.add(daily, held.copy());
            ChatUtil.send(sender, "&aДобавлено в предметы " + title + ": &f" + held.getDisplayName() + " x" + held.getCount());
            return;
        }
        if ("clear".equals(op)) {
            RewardItems.clear(daily);
            ChatUtil.send(sender, "&aПредметы " + title + " очищены.");
            return;
        }
        List<ItemStack> list = daily ? RewardItems.daily() : RewardItems.playtime();
        ChatUtil.send(sender, "&6Предметы " + title + ": &f" + ItemCodec.describe(list, 20));
    }

    private static void set(ICommandSender sender, String category, String key, String value) {
        if (EcoConfig.set(category, key, value.replace("_", "").trim())) {
            ChatUtil.send(sender, "&aГотово: &f" + category + "." + key + " = " + value);
        } else {
            ChatUtil.send(sender, "&cНекорректное значение: &f" + value);
        }
    }

    private static void overview(ICommandSender sender) {
        ChatUtil.send(sender, "&6=== Награды ===");
        ChatUtil.sendRaw(sender, " &7За игру: " + (EcoConfig.playtimeRewardEnabled ? "&aвкл" : "&cвыкл")
                + " &7каждые &f" + EcoConfig.playtimeRewardMinutes + " мин &7→ &e" + Fmt.money(EcoConfig.playtimeRewardAmount)
                + " &7+ предметы: &f" + ItemCodec.describe(RewardItems.playtime(), 3));
        ChatUtil.sendRaw(sender, " &7Ежедневка: " + (EcoConfig.dailyEnabled ? "&aвкл" : "&cвыкл")
                + " &7→ &e" + Fmt.money(EcoConfig.dailyAmount) + " &7+ за день серии &e" + Fmt.money(EcoConfig.dailyStreakBonus)
                + " &7(до " + EcoConfig.dailyStreakMax + " дн.) + предметы: &f" + ItemCodec.describe(RewardItems.daily(), 3));
        ChatUtil.sendRaw(sender, " &7АФК через: &f" + EcoConfig.afkMinutes + " мин &7| роли умножают деньги (&f/role set <роль> multiplier 1.5&7)");
        ChatUtil.sendRaw(sender, " &8/ecoadmin rewards playtime on|off | minutes <N> | money <сумма> | item add|clear|list");
        ChatUtil.sendRaw(sender, " &8/ecoadmin rewards daily on|off | money <сумма> | streak <бонус> [дней] | item add|clear|list");
        ChatUtil.sendRaw(sender, " &8/ecoadmin rewards afk <мин> | reset <ник>");
    }
}

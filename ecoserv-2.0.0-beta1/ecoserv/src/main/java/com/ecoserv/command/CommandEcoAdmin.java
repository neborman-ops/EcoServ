package com.ecoserv.command;

import com.ecoserv.backup.BackupManager;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.integration.FTBQuestsBridge;
import com.ecoserv.integration.Integrations;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.server.ItemBlacklist;
import com.ecoserv.server.LagReport;
import com.ecoserv.server.RestartScheduler;
import com.ecoserv.server.ServerServices;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** /ecoadmin — всё остальное управление сервером. */
public class CommandEcoAdmin extends CommandBase {

    @Override
    public String getName() {
        return "ecoadmin";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ecoadmin <reload|restart|cancelrestart|announce|lag|blacklist|restrict|save|info|ftbscan|blockinfo|adminshop|setowner|economy|moneytest|rewards|config|inventories|dates>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return Perms.isAdmin(sender) || super.checkPermission(server, sender);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
            return;
        }
        String sub = args[0].toLowerCase();

        if ("reload".equals(sub)) {
            ServerServices.reloadAll();
            ChatUtil.send(sender, "&aКонфиг, прайс-лист, чёрный список и запреты по мирам перезагружены.");
            return;
        }

        if ("restart".equals(sub)) {
            int minutes = 5;
            if (args.length > 1) {
                try {
                    minutes = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    ChatUtil.send(sender, "&cНекорректное число минут.");
                    return;
                }
            }
            RestartScheduler.startManualCountdown(Math.max(1, minutes), sender);
            return;
        }

        if ("cancelrestart".equals(sub)) {
            RestartScheduler.cancelCountdown(sender);
            return;
        }

        if ("announce".equals(sub)) {
            if (args.length < 2) {
                ChatUtil.send(sender, "&cИспользование: &f/ecoadmin announce <текст>");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            ChatUtil.broadcast(sb.toString());
            return;
        }

        if ("lag".equals(sub)) {
            List<String> lines = LagReport.build(10);
            for (int i = 0; i < lines.size(); i++) {
                ChatUtil.sendRaw(sender, lines.get(i));
            }
            return;
        }

        if ("blacklist".equals(sub)) {
            blacklist(sender, args);
            return;
        }

        if ("blockinfo".equals(sub) || "adminshop".equals(sub) || "setowner".equals(sub)) {
            blockCommand(sender, args, sub);
            return;
        }

        if ("economy".equals(sub)) {
            economyReport(sender);
            return;
        }

        if ("moneytest".equals(sub)) {
            moneyTest(sender);
            return;
        }

        if ("restrict".equals(sub)) {
            restrictCommand(sender, args);
            return;
        }

        if ("rewards".equals(sub)) {
            RewardsCommand.handle(sender, args);
            return;
        }

        if ("dates".equals(sub)) {
            datesCommand(sender, args);
            return;
        }

        if ("inventories".equals(sub) || "inv".equals(sub)) {
            inventoriesCommand(sender, args);
            return;
        }

        if ("config".equals(sub) || "settings".equals(sub)) {
            configCommand(sender, args);
            return;
        }

        if ("ftbscan".equals(sub)) {
            List<String> lines = com.ecoserv.integration.ApiScanner.report();
            for (int i = 0; i < lines.size(); i++) {
                ChatUtil.sendRaw(sender, lines.get(i));
                com.ecoserv.EcoServ.LOG.info(lines.get(i).replaceAll("§.", ""));
            }
            ChatUtil.send(sender, "&7Тот же список продублирован в лог сервера.");
            return;
        }

        if ("save".equals(sub)) {
            ServerServices.saveAll(true);
            ChatUtil.send(sender, "&aДанные EcoServ сохранены.");
            return;
        }

        if ("info".equals(sub)) {
            ChatUtil.send(sender, "&6=== EcoServ ===");
            ChatUtil.sendRaw(sender, " &7Экономика: &f" + EconomyManager.provider().getName()
                    + " &7(офлайн-выплаты: " + (EconomyManager.provider().supportsOffline() ? "напрямую" : "через очередь") + ")");
            if (EconomyManager.provider() instanceof com.ecoserv.integration.ReflectiveEconomy) {
                ChatUtil.sendRaw(sender, " &7Начисление: &f"
                        + ((com.ecoserv.integration.ReflectiveEconomy) EconomyManager.provider()).describeMode()
                        + " &7— проверить: &f/ecoadmin moneytest");
            }
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP self = (EntityPlayerMP) sender;
                ChatUtil.sendRaw(sender, " &7Твой баланс по версии мода: &a"
                        + Fmt.money(EconomyManager.get(self.getUniqueID())));
            }
            ChatUtil.sendRaw(sender, " &7FTB: money=" + Integrations.ftbMoney + ", quests=" + Integrations.ftbQuests
                    + " &7(мост квестов: " + (FTBQuestsBridge.isAvailable() ? "&aактивен" : "&8нет") + "&7)");
            ChatUtil.sendRaw(sender, " &7Прайс-лист: &f" + ShopList.get().size() + " позиций");
            ChatUtil.sendRaw(sender, " &7Запреты по мирам: &f" + com.ecoserv.restrict.ItemRules.all().size()
                    + " &7| зоны: &f" + com.ecoserv.zone.ZoneManager.all().size()
                    + " &7| кнопки с названиями: &f" + com.ecoserv.activator.ActivatorManager.all().size()
                    + " &7(условие «Активация»: " + (com.ecoserv.activator.ActivatorManager.hasQuestListener()
                    ? "&aподключено" : "&8нет") + "&7)");
            ChatUtil.sendRaw(sender, " &7Чёрный список: &f" + ItemBlacklist.list().size()
                    + (EcoConfig.blacklistEnabled ? "" : " &8(выключен)"));
            ChatUtil.sendRaw(sender, " &7Бэкапы: &f" + BackupManager.listBackups().size()
                    + " (" + Fmt.bytes(BackupManager.totalSize()) + ")");
            ChatUtil.sendRaw(sender, String.format(" &7TPS: &f%.1f", LagReport.tps()));
            long restart = RestartScheduler.nextRestart();
            if (restart > 0L) {
                ChatUtil.sendRaw(sender, " &7Перезапуск: &f" + Fmt.timestamp(restart)
                        + " &7(через " + Fmt.duration(restart - System.currentTimeMillis()) + ")");
            }
            return;
        }

        ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
    }

    /** /ecoadmin dates — значимые даты и дни рождения (в игре — меню). */
    private void datesCommand(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof EntityPlayerMP) {
                com.ecoserv.dates.DatesScreen.open((EntityPlayerMP) sender);
                return;
            }
            ChatUtil.send(sender, "&6=== Значимые даты (" + (EcoConfig.datesEnabled ? "вкл" : "выкл") + ") ===");
            for (com.ecoserv.dates.SpecialDate d : com.ecoserv.dates.DatesManager.dates()) {
                ChatUtil.sendRaw(sender, " &f" + d.id + " &7" + d.dateText() + " «" + d.name + "» монет: " + d.money
                        + (d.enabled ? "" : " (выкл)"));
            }
            ChatUtil.sendRaw(sender, "&7/ecoadmin dates add <дд.мм> <название> | remove <id> | money <id> <сумма> | toggle <id>");
            return;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("add".equals(action) && args.length > 2) {
            com.ecoserv.dates.SpecialDate d = com.ecoserv.dates.DatesManager.create(args[2], args.length > 3 ? buildString(args, 3) : "");
            ChatUtil.send(sender, d == null ? "&cНе понял дату (дд.мм)." : "&aСоздано: &f" + d.id + " " + d.dateText());
            return;
        }
        if ("remove".equals(action) && args.length > 2) {
            ChatUtil.send(sender, com.ecoserv.dates.DatesManager.remove(args[2]) ? "&aУдалено." : "&cНет такой даты.");
            return;
        }
        if (("money".equals(action) || "toggle".equals(action)) && args.length > 2) {
            com.ecoserv.dates.SpecialDate d = com.ecoserv.dates.DatesManager.get(args[2]);
            if (d == null) {
                ChatUtil.send(sender, "&cНет такой даты.");
                return;
            }
            if ("toggle".equals(action)) {
                d.enabled = !d.enabled;
            } else {
                try {
                    d.money = Math.max(0L, Long.parseLong(args.length > 3 ? args[3] : "x"));
                } catch (NumberFormatException e) {
                    ChatUtil.send(sender, "&cНужно число.");
                    return;
                }
            }
            com.ecoserv.dates.DatesManager.changed();
            ChatUtil.send(sender, "&aГотово: &f" + d.id + " монет " + d.money + (d.enabled ? "" : " (выкл)"));
            return;
        }
        ChatUtil.send(sender, "&cИспользование: &f/ecoadmin dates [add <дд.мм> <название> | remove <id> | money <id> <сумма> | toggle <id>]");
    }

    /** /ecoadmin inventories [ник] — группы раздельных инвентарей. */
    private void inventoriesCommand(ICommandSender sender, String[] args) {
        ChatUtil.send(sender, "&6=== Раздельные инвентари: " + (EcoConfig.invEnabled ? "&aвключены" : "&cвыключены") + " &6===");
        for (java.util.Map.Entry<String, List<String>> e : com.ecoserv.inventory.InventoryGroups.groups().entrySet()) {
            ChatUtil.sendRaw(sender, " &f" + e.getKey() + "&7: " + String.join(", ", e.getValue()));
        }
        ChatUtil.sendRaw(sender, " &f" + com.ecoserv.inventory.InventoryGroups.DEFAULT + "&7: все остальные миры");
        ChatUtil.sendRaw(sender, " &8Настройка: /ecoadmin config → «Раздельные инвентари» (inventories.groups)");
        if (args.length > 1) {
            java.util.UUID id = com.ecoserv.util.PlayerUtil.uuidOf(args[1]);
            if (id == null) {
                ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
                return;
            }
            EntityPlayerMP online = com.ecoserv.util.PlayerUtil.online(id);
            ChatUtil.sendRaw(sender, " &7" + args[1] + ": сейчас у игрока вещи группы &f"
                    + (online == null ? "? (не в сети)" : com.ecoserv.inventory.InventoryGroups.liveGroup(online))
                    + "&7, сохранены: &f" + String.join(", ", com.ecoserv.inventory.InventoryGroups.storedGroups(id)));
        }
    }

    /** /ecoadmin config — редактор конфига (в игре — меню, из консоли/RCON — подкоманды). */
    private void configCommand(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof EntityPlayerMP) {
                com.ecoserv.config.ConfigScreen.open((EntityPlayerMP) sender);
            } else {
                ChatUtil.send(sender, "&6Разделы: &f" + String.join(", ", EcoConfig.raw().getCategoryNames()));
                ChatUtil.sendRaw(sender, "&7/ecoadmin config list <раздел> | get <раздел> <ключ> | set <раздел> <ключ> <значение> | reset <раздел> <ключ>");
            }
            return;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("list".equals(action)) {
            if (args.length < 3) {
                ChatUtil.send(sender, "&6Разделы: &f" + String.join(", ", EcoConfig.raw().getCategoryNames()));
                return;
            }
            String cat = args[2].toLowerCase(java.util.Locale.ROOT);
            if (!EcoConfig.raw().hasCategory(cat)) {
                ChatUtil.send(sender, "&cНет раздела &f" + cat);
                return;
            }
            ChatUtil.send(sender, "&6=== " + com.ecoserv.config.ConfigScreen.titleOf(cat) + " (" + cat + ") ===");
            for (net.minecraftforge.common.config.Property p : EcoConfig.raw().getCategory(cat).getOrderedValues()) {
                if (EcoConfig.isHidden(cat, p.getName())) {
                    continue;
                }
                ChatUtil.sendRaw(sender, " &f" + p.getName() + " &7= &e" + EcoConfig.display(p)
                        + (p.isDefault() ? "" : " &8(по умолч.: " + EcoConfig.displayDefault(p) + ")"));
            }
            return;
        }
        if (args.length < 4 || !("get".equals(action) || "set".equals(action) || "reset".equals(action))) {
            ChatUtil.send(sender, "&cИспользование: &f/ecoadmin config [list <раздел> | get|reset <раздел> <ключ> | set <раздел> <ключ> <значение>]");
            return;
        }
        String cat = args[2].toLowerCase(java.util.Locale.ROOT);
        String key = args[3];
        net.minecraftforge.common.config.Property p = EcoConfig.property(cat, key);
        if (p == null) {
            ChatUtil.send(sender, "&cНет параметра &f" + cat + "." + key + " &7(список: /ecoadmin config list " + cat + ")");
            return;
        }
        if ("get".equals(action)) {
            ChatUtil.send(sender, "&f" + cat + "." + key + " &7= &e" + EcoConfig.display(p));
            if (p.getComment() != null && !p.getComment().isEmpty()) {
                ChatUtil.sendRaw(sender, " &7" + p.getComment());
            }
            return;
        }
        if ("reset".equals(action)) {
            EcoConfig.reset(cat, key);
            com.ecoserv.config.ConfigHooks.apply(cat);
            ChatUtil.send(sender, "&a" + cat + "." + key + " = &f" + EcoConfig.display(EcoConfig.property(cat, key)) + " &7(по умолчанию)");
            return;
        }
        if (args.length < 5) {
            ChatUtil.send(sender, "&cУкажи значение. Для списков — строки через «;».");
            return;
        }
        String error = EcoConfig.setChecked(cat, key, buildString(args, 4));
        if (error != null) {
            ChatUtil.send(sender, "&cНе записано: " + error);
            return;
        }
        com.ecoserv.config.ConfigHooks.apply(cat);
        ChatUtil.send(sender, "&aГотово: &f" + cat + "." + key + " = " + EcoConfig.display(EcoConfig.property(cat, key)));
    }

    /** Команды по блоку, на который смотрит админ. */
    private void blockCommand(ICommandSender sender, String[] args, String sub) {
        if (!(sender instanceof EntityPlayerMP)) {
            ChatUtil.send(sender, "&cЭти команды работают только в игре: посмотри на блок.");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        net.minecraft.util.math.BlockPos pos = com.ecoserv.util.PlayerUtil.lookingAt(player, 8.0D);
        if (pos == null) {
            ChatUtil.send(sender, "&cПосмотри на блок (не дальше 8 блоков).");
            return;
        }
        net.minecraft.tileentity.TileEntity te = player.world.getTileEntity(pos);

        if (te instanceof com.ecoserv.tile.TileVending) {
            com.ecoserv.tile.TileVending vending = (com.ecoserv.tile.TileVending) te;
            if ("adminshop".equals(sub)) {
                if (!EcoConfig.vendingAllowAdminShops) {
                    ChatUtil.send(sender, "&cАдминские автоматы отключены в конфиге.");
                    return;
                }
                vending.setAdminShop(!vending.isAdminShop());
                ChatUtil.send(sender, vending.isAdminShop()
                        ? "&dАвтомат стал админским: товар бесконечный, деньги исчезают."
                        : "&aАвтомат снова обычный.");
                return;
            }
            if ("setowner".equals(sub)) {
                setOwner(sender, args, vending, null, null);
                return;
            }
            ChatUtil.send(sender, "&6=== Блок продажи ===");
            ChatUtil.sendRaw(sender, " &7Владелец: &f" + vending.getOwnerName()
                    + (vending.isAdminShop() ? " &d(админский)" : ""));
            ChatUtil.sendRaw(sender, " &7Товар: &f" + (vending.getSample().isEmpty()
                    ? "не задан" : vending.getSample().getDisplayName()) + " x" + vending.getAmount()
                    + " &7за &e" + Fmt.money(vending.getPrice()));
            ChatUtil.sendRaw(sender, " &7Остаток: &f" + vending.countStock()
                    + " &7| в кассе: &2" + Fmt.money(vending.getStoredMoney())
                    + " &7| продано: &f" + vending.getSoldTotal());
            ChatUtil.sendRaw(sender, " &7Вход автоматики: &f" + vending.backSide().getName() + " &7(красная точка)");
            return;
        }

        if (te instanceof com.ecoserv.tile.TileVendor) {
            com.ecoserv.tile.TileVendor vendor = (com.ecoserv.tile.TileVendor) te;
            if ("setowner".equals(sub)) {
                setOwner(sender, args, null, vendor, null);
                return;
            }
            ChatUtil.send(sender, "&6=== Склад продавца ===");
            ChatUtil.sendRaw(sender, " &7Владелец: &f" + vendor.getOwnerName());
            ChatUtil.sendRaw(sender, " &7Заработано: &2" + Fmt.money(vendor.getEarnedTotal()));
            return;
        }

        if (te instanceof com.ecoserv.tile.TileMarket) {
            com.ecoserv.tile.TileMarket market = (com.ecoserv.tile.TileMarket) te;
            if ("setowner".equals(sub)) {
                setOwner(sender, args, null, null, market);
                return;
            }
            ChatUtil.send(sender, "&6=== Торговый терминал ===");
            ChatUtil.sendRaw(sender, " &7Получатель денег: &f" + market.getOwnerName());
            ChatUtil.sendRaw(sender, " &7Заработано: &2" + Fmt.money(market.getEarnedTotal())
                    + " &7| автопродажа: &f" + (market.isAutoSell() ? "вкл" : "выкл"));
            return;
        }

        ChatUtil.send(sender, "&cЭто не блок EcoServ.");
    }

    private void setOwner(ICommandSender sender, String[] args,
                          com.ecoserv.tile.TileVending vending,
                          com.ecoserv.tile.TileVendor vendor,
                          com.ecoserv.tile.TileMarket market) {
        if (args.length < 2) {
            ChatUtil.send(sender, "&cИспользование: &f/ecoadmin setowner <ник>");
            return;
        }
        java.util.UUID uuid = com.ecoserv.util.PlayerUtil.uuidOf(args[1]);
        if (uuid == null) {
            ChatUtil.send(sender, "&cИгрок &f" + args[1] + " &cне найден.");
            return;
        }
        if (vending != null) {
            vending.setOwner(uuid, args[1]);
        } else if (vendor != null) {
            vendor.setOwner(uuid, args[1]);
        } else if (market != null) {
            market.setOwner(uuid, args[1]);
        }
        ChatUtil.send(sender, "&aНовый владелец блока: &f" + args[1]);
    }

    /**
     * Частичные запреты предметов по мирам.
     * /ecoadmin restrict — редактор для предмета в руке;
     * /ecoadmin restrict list | edit &lt;ключ&gt; | remove [ключ] | reload
     */
    private void restrictCommand(ICommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "";
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;

        if ("list".equals(action)) {
            List<com.ecoserv.restrict.ItemRule> rules = com.ecoserv.restrict.ItemRules.all();
            ChatUtil.send(sender, "&6=== Частичные запреты (" + rules.size() + ") ===");
            if (rules.isEmpty()) {
                ChatUtil.sendRaw(sender, " &7пусто. Возьми предмет в руку и напиши &f/ecoadmin restrict");
            }
            for (int i = 0; i < rules.size(); i++) {
                com.ecoserv.restrict.ItemRule rule = rules.get(i);
                String name = rule.title();
                net.minecraft.util.text.ITextComponent line = ChatUtil.component(" &f" + name + " &c[" + rule.describeDeny()
                        + "] &7" + rule.describeWorlds());
                line.getStyle().setClickEvent(new net.minecraft.util.text.event.ClickEvent(
                        net.minecraft.util.text.event.ClickEvent.Action.RUN_COMMAND, "/ecoadmin restrict edit " + rule.item));
                line.getStyle().setHoverEvent(new net.minecraft.util.text.event.HoverEvent(
                        net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT,
                        ChatUtil.component("&7" + rule.item + "\n&eКлик — открыть редактор")));
                ChatUtil.send(sender, line);
            }
            return;
        }

        if ("reload".equals(action)) {
            com.ecoserv.restrict.ItemRules.load();
            ChatUtil.send(sender, "&aЗапреты перечитаны: &f" + com.ecoserv.restrict.ItemRules.all().size() + " правил");
            return;
        }

        if ("remove".equals(action)) {
            String key = args.length > 2 ? args[2] : "";
            if (key.isEmpty() && player != null && !player.getHeldItemMainhand().isEmpty()) {
                ItemStack held = player.getHeldItemMainhand();
                com.ecoserv.restrict.ItemRule found = com.ecoserv.restrict.ItemRules.findFor(held, ruleKey(held));
                key = found == null ? ruleKey(held) : found.item;
            }
            if (key.isEmpty()) {
                ChatUtil.send(sender, "&cИспользование: &f/ecoadmin restrict remove <ключ> &7или с предметом в руке");
                return;
            }
            ChatUtil.send(sender, com.ecoserv.restrict.ItemRules.remove(key)
                    ? "&aПравило &f" + key + " &aудалено." : "&cПравила &f" + key + " &cнет.");
            return;
        }

        if ("mods".equals(action)) {
            listMods(sender);
            return;
        }

        if (player == null) {
            ChatUtil.send(sender, "&cРедактор открывается только в игре. Файл: config/ecoserv/restrictions.json");
            return;
        }

        if ("mod".equals(action) || ("edit".equals(action) && args.length > 2 && args[2].startsWith("@"))) {
            String modId = args.length > 2 ? args[2].replace("@", "").trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (modId.isEmpty() || !net.minecraftforge.fml.common.Loader.isModLoaded(modId)) {
                ChatUtil.send(sender, modId.isEmpty() ? "&cИспользование: &f/ecoadmin restrict mod <modid>"
                        : "&cМод &f" + modId + " &cне загружен. Список: &f/ecoadmin restrict mods");
                return;
            }
            String key = com.ecoserv.restrict.ItemRules.modKey(modId);
            if (com.ecoserv.restrict.ItemRules.exact(key) == null) {
                // «выключить мод»: сразу запрещаем всё, админу остаётся выбрать миры
                com.ecoserv.restrict.ItemRule rule = com.ecoserv.restrict.ItemRules.getOrCreate(key);
                for (com.ecoserv.restrict.RestrictAction a : com.ecoserv.restrict.RestrictAction.values()) {
                    rule.setDenied(a, true);
                }
                com.ecoserv.restrict.ItemRules.changed();
            }
            new com.ecoserv.restrict.RuleScreen(player, key, ItemStack.EMPTY).open();
            return;
        }

        if ("edit".equals(action) && args.length > 2) {
            String key = args[2];
            ItemKey parsed = ItemKey.parse(key);
            if (!parsed.isValid()) {
                ChatUtil.send(sender, "&cПредмет &f" + key + " &cне найден.");
                return;
            }
            new com.ecoserv.restrict.RuleScreen(player, parsed.serialize(), parsed.toStack(1)).open();
            return;
        }

        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            ChatUtil.send(sender, "&eВозьми предмет в руку и напиши &f/ecoadmin restrict&e — откроется редактор запретов.");
            ChatUtil.sendRaw(sender, " &7Ещё: &f/ecoadmin restrict list | edit <ключ> | remove [ключ] | reload");
            ChatUtil.sendRaw(sender, " &7Выключить мод в мирах: &f/ecoadmin restrict mod <modid> &7(список: &f/ecoadmin restrict mods&7)");
            return;
        }
        // если правило для этого предмета уже есть (в том числе написанное вручную) — открываем его
        com.ecoserv.restrict.ItemRule existing = com.ecoserv.restrict.ItemRules.findFor(held, ruleKey(held));
        new com.ecoserv.restrict.RuleScreen(player, existing == null ? ruleKey(held) : existing.item, held).open();
    }

    /** Список модов для выключения по мирам (клик — редактор). */
    private void listMods(ICommandSender sender) {
        java.util.Map<String, Integer> items = new java.util.HashMap<String, Integer>();
        for (net.minecraft.util.ResourceLocation id : net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getKeys()) {
            Integer n = items.get(id.getNamespace());
            items.put(id.getNamespace(), n == null ? 1 : n + 1);
        }
        List<net.minecraftforge.fml.common.ModContainer> mods =
                new ArrayList<net.minecraftforge.fml.common.ModContainer>(net.minecraftforge.fml.common.Loader.instance().getActiveModList());
        ChatUtil.send(sender, "&6=== Моды (клик — выключить в мирах) ===");
        for (net.minecraftforge.fml.common.ModContainer mod : mods) {
            String id = mod.getModId();
            Integer count = items.get(id);
            if (count == null || id.equals("minecraft") || id.equals(com.ecoserv.Ref.MODID)) {
                continue;
            }
            com.ecoserv.restrict.ItemRule rule = com.ecoserv.restrict.ItemRules.exact(com.ecoserv.restrict.ItemRules.modKey(id));
            net.minecraft.util.text.ITextComponent line = ChatUtil.component(" &f" + mod.getName() + " &8(" + id + ") &7предметов: &f" + count
                    + (rule == null ? "" : " &c[" + rule.describeDeny() + "] &7" + rule.describeWorlds()));
            line.getStyle().setClickEvent(new net.minecraft.util.text.event.ClickEvent(
                    net.minecraft.util.text.event.ClickEvent.Action.RUN_COMMAND, "/ecoadmin restrict mod " + id));
            ChatUtil.send(sender, line);
        }
    }

    /** Ключ для запрета: у инструментов и брони износ не важен, у остальных важен meta. */
    private static String ruleKey(ItemStack held) {
        if (held == null || held.isEmpty()) {
            return "";
        }
        return ItemKey.of(held, false, !held.isItemStackDamageable()).serialize();
    }

    /**
     * Самопроверка денег на своём балансе: +1, затем -1. Если провайдер вместо
     * прибавления перезаписывает баланс, это сразу видно (и мост сам переключится
     * в безопасный режим). В итоге баланс остаётся прежним.
     */
    private void moneyTest(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            ChatUtil.send(sender, "&cЗапусти в игре: проверка идёт на твоём балансе.");
            return;
        }
        java.util.UUID id = ((EntityPlayerMP) sender).getUniqueID();
        long before = EconomyManager.get(id);
        if (before <= 0L) {
            ChatUtil.send(sender, "&eНа твоём балансе 0 — при нуле «прибавить» и «установить» дают одно и то же. "
                    + "Положи себе немного денег и повтори проверку.");
            return;
        }
        EconomyManager.give(id, 1L);
        long afterGive = EconomyManager.get(id);
        boolean took = EconomyManager.take(id, 1L);
        long afterTake = EconomyManager.get(id);
        boolean ok = afterGive == before + 1L && took && afterTake == before;
        ChatUtil.send(sender, "&6=== Проверка денег (" + EconomyManager.provider().getName() + ") ===");
        ChatUtil.sendRaw(sender, " &7Было: &f" + Fmt.money(before) + " &7→ +1 → &f" + Fmt.money(afterGive)
                + " &7→ -1 → &f" + Fmt.money(afterTake));
        ChatUtil.sendRaw(sender, ok
                ? " &aOK: деньги прибавляются и списываются правильно."
                : " &cОШИБКА: баланс меняется неправильно. Поставь economy_provider=internal "
                + "или пришли лог сервера (строки 'Мост экономики').");
        PacketHandler.sendPlayerState((EntityPlayerMP) sender);
    }

    /** Сводка по денежной массе — чтобы видеть инфляцию. */
    private void economyReport(ICommandSender sender) {
        java.util.Map<java.util.UUID, Long> balances = EconomyManager.internal().view();
        long total = 0L;
        for (Long value : balances.values()) {
            total += value == null ? 0L : value;
        }
        int accounts = Math.max(1, balances.size());
        ChatUtil.send(sender, "&6=== Экономика ===");
        ChatUtil.sendRaw(sender, " &7Источник денег: &f" + EconomyManager.provider().getName());
        ChatUtil.sendRaw(sender, " &7Счетов: &f" + balances.size());
        ChatUtil.sendRaw(sender, " &7Всего в обороте: &2" + Fmt.money(total));
        ChatUtil.sendRaw(sender, " &7В среднем на игрока: &f" + Fmt.money(total / accounts));
        ChatUtil.sendRaw(sender, " &7Лотов на аукционе: &f" + com.ecoserv.auction.AuctionManager.all().size());
        java.util.List<java.util.Map.Entry<java.util.UUID, Long>> top = EconomyManager.top(5);
        for (int i = 0; i < top.size(); i++) {
            ChatUtil.sendRaw(sender, " &e" + (i + 1) + ". &f"
                    + com.ecoserv.util.PlayerUtil.nameOf(top.get(i).getKey())
                    + " &7- &2" + Fmt.money(top.get(i).getValue()));
        }
    }

    private void blacklist(ICommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "list";

        if ("list".equals(action)) {
            List<String> list = ItemBlacklist.list();
            ChatUtil.send(sender, "&6=== Чёрный список (" + list.size() + ") ===");
            for (int i = 0; i < list.size() && i < 30; i++) {
                ChatUtil.sendRaw(sender, " &f" + list.get(i));
            }
            if (list.isEmpty()) {
                ChatUtil.sendRaw(sender, " &7пусто");
            }
            return;
        }

        if ("add".equals(action)) {
            String key = args.length > 2 ? args[2] : null;
            if (key == null) {
                if (!(sender instanceof EntityPlayerMP)) {
                    ChatUtil.send(sender, "&cИз консоли укажи id: /ecoadmin blacklist add <modid:item@meta>");
                    return;
                }
                ItemStack held = ((EntityPlayerMP) sender).getHeldItemMainhand();
                if (held.isEmpty()) {
                    ChatUtil.send(sender, "&cВозьми предмет в руку.");
                    return;
                }
                key = ItemKey.of(held, false, true).serialize();
            }
            ItemBlacklist.add(key);
            ChatUtil.send(sender, "&aДобавлено: &f" + key
                    + (EcoConfig.blacklistEnabled ? "" : " &e(чёрный список выключен в конфиге!)"));
            return;
        }

        if ("remove".equals(action)) {
            if (args.length < 3) {
                ChatUtil.send(sender, "&cИспользование: &f/ecoadmin blacklist remove <ключ>");
                return;
            }
            ChatUtil.send(sender, ItemBlacklist.remove(args[2]) ? "&aУдалено." : "&cНе найдено.");
            return;
        }

        ChatUtil.send(sender, "&cИспользование: &f/ecoadmin blacklist <add|remove|list>");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "restart", "cancelrestart", "announce",
                    "lag", "blacklist", "save", "info", "ftbscan",
                    "blockinfo", "adminshop", "setowner", "economy", "moneytest", "restrict", "rewards", "config", "inventories", "dates");
        }
        if (args.length == 2 && "blacklist".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "add", "remove", "list");
        }
        if ("config".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return getListOfStringsMatchingLastWord(args, "list", "get", "set", "reset");
            }
            if (args.length == 3) {
                return getListOfStringsMatchingLastWord(args, EcoConfig.raw().getCategoryNames());
            }
            if (args.length == 4 && !"list".equalsIgnoreCase(args[1])
                    && EcoConfig.raw().hasCategory(args[2].toLowerCase(java.util.Locale.ROOT))) {
                return getListOfStringsMatchingLastWord(args,
                        EcoConfig.raw().getCategory(args[2].toLowerCase(java.util.Locale.ROOT)).getValues().keySet());
            }
            return new ArrayList<String>();
        }
        if (args.length == 2 && "rewards".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "playtime", "daily", "afk", "reset");
        }
        if (args.length == 3 && "rewards".equalsIgnoreCase(args[0])
                && ("playtime".equalsIgnoreCase(args[1]) || "daily".equalsIgnoreCase(args[1]))) {
            return "daily".equalsIgnoreCase(args[1])
                    ? getListOfStringsMatchingLastWord(args, "on", "off", "money", "streak", "item")
                    : getListOfStringsMatchingLastWord(args, "on", "off", "minutes", "money", "item");
        }
        if (args.length == 4 && "rewards".equalsIgnoreCase(args[0]) && "item".equalsIgnoreCase(args[2])) {
            return getListOfStringsMatchingLastWord(args, "add", "clear", "list");
        }
        if (args.length == 3 && "rewards".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "restrict".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "list", "edit", "remove", "reload", "mod", "mods");
        }
        if (args.length == 3 && "restrict".equalsIgnoreCase(args[0]) && "mod".equalsIgnoreCase(args[1])) {
            List<String> ids = new ArrayList<String>();
            for (net.minecraftforge.fml.common.ModContainer mod : net.minecraftforge.fml.common.Loader.instance().getActiveModList()) {
                ids.add(mod.getModId());
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        if (args.length == 3 && "restrict".equalsIgnoreCase(args[0])
                && ("edit".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
            List<String> keys = new ArrayList<String>();
            for (com.ecoserv.restrict.ItemRule rule : com.ecoserv.restrict.ItemRules.all()) {
                keys.add(rule.item);
            }
            return getListOfStringsMatchingLastWord(args, keys);
        }
        return new ArrayList<String>();
    }
}

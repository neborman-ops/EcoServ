package com.ecoserv.subworld.menu;

import com.ecoserv.backup.BackupManager;
import com.ecoserv.init.ModRegistry;
import com.ecoserv.server.ServerServices;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/** Вкладка «Админ-панель»: все инструменты админа в одном месте, с пояснениями. */
final class AdminPanel {

    private AdminPanel() {
    }

    static void build(Ui ui, final WorldsScreen s) {
        final EntityPlayerMP player = s.player();
        ui.search("поиск инструмента");

        ui.header("Настройки");
        ui.row(new ItemStack(Items.COMPARATOR), "&fНастройки мода")
                .line("&7Все параметры ecoserv.cfg прямо в игре: переключатели, числа, списки")
                .line("&8изменения применяются сразу, без перезапуска")
                .onClick(() -> com.ecoserv.config.ConfigScreen.open(player))
                .button("Открыть", Ui.BLUE, () -> com.ecoserv.config.ConfigScreen.open(player));
        ui.row(new ItemStack(Items.NAME_TAG), "&fРоли и теги")
                .line("&7Тег в чате, команды без OP, права, миры для /world, множитель наград, кому выдана")
                .onClick(() -> com.ecoserv.role.RolesScreen.open(player))
                .button("Открыть", Ui.BLUE, () -> com.ecoserv.role.RolesScreen.open(player));
        ui.row(new ItemStack(Blocks.BARRIER), "&fЗапреты предметов по мирам")
                .line("&7Крафт, использование, установка, ношение — для предмета или целого мода")
                .onClick(() -> com.ecoserv.restrict.RuleListScreen.open(player))
                .button("Открыть", Ui.BLUE, () -> com.ecoserv.restrict.RuleListScreen.open(player));
        ui.row(new ItemStack(Blocks.CAKE), "&fДаты и дни рождения")
                .line("&7Подарки в значимые даты и в день рождения, проверка возраста")
                .onClick(() -> com.ecoserv.dates.DatesScreen.open(player))
                .button("Открыть", Ui.BLUE, () -> com.ecoserv.dates.DatesScreen.open(player));
        ui.row(new ItemStack(Items.EMERALD), "&fМагазин: цены и аукцион")
                .line("&7Окно магазина; вкладки «Цены» и «Сервер» видны только админам")
                .button("Открыть", Ui.BLUE, () -> {
                    com.ecoserv.shop.ShopScreen.open(player, com.ecoserv.shop.ShopScreen.Tab.PRICES);
                });

        ui.header("Сервер");
        boolean running = BackupManager.isRunning();
        ui.row(new ItemStack(Blocks.CHEST), "&fБэкап мира")
                .line("&7Последний: &f" + (BackupManager.lastBackupTime() > 0 ? Fmt.timestamp(BackupManager.lastBackupTime())
                        + " · " + Fmt.bytes(BackupManager.lastSize()) : "ещё не было"))
                .line("&8следующий по расписанию: " + (BackupManager.nextBackupTime() > 0
                        ? Fmt.timestamp(BackupManager.nextBackupTime()) : "выключено"))
                .chip(running ? "идёт" : "готов", running ? Ui.YELLOW : Ui.GREEN)
                .button("Сделать сейчас", Ui.GREEN, () -> {
                    if (BackupManager.isRunning()) {
                        throw new UiError("Бэкап уже идёт.");
                    }
                    BackupManager.start(player, "manual");
                    s.setToast("Бэкап запущен — сообщение придёт в чат.");
                }).disabled(running);
        ui.row(new ItemStack(Items.BOOK), "&fПеречитать файлы")
                .line("&7Конфиг, прайс, запреты, роли, даты, сценарии — если правил файлы руками")
                .button("Перечитать", Ui.BLUE, () -> {
                    ServerServices.reloadAll();
                    s.setToast("Файлы перечитаны.");
                }).confirm("Перечитать все файлы настроек с диска? Несохранённые правки в файлах вступят в силу.");
        ui.row(new ItemStack(Items.WRITABLE_BOOK), "&fСохранить данные")
                .line("&7Балансы, аукцион, запреты, подмиры — записать на диск прямо сейчас")
                .button("Сохранить", Ui.GREY, () -> {
                    ServerServices.saveAll(true);
                    s.setToast("Данные сохранены.");
                });

        ui.header("Игроки");
        ui.field("Буфер предметов игрока", "Ник — посмотреть, что у него лежит в буфере (/stash)", "", v -> {
            String name = v.trim();
            if (name.isEmpty()) {
                return;
            }
            UUID id = PlayerUtil.uuidOf(name);
            if (id == null) {
                throw new UiError("Игрок " + name + " не найден.");
            }
            new com.ecoserv.restrict.StashScreen(player, id, PlayerUtil.nameOf(id)).open();
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);
        ui.field("Положить в хранилище ГЛИТЧ", "Ник — предмет из твоей руки уйдёт в его ГЛИТЧ-хранилище", "", v -> {
            String name = v.trim();
            if (name.isEmpty()) {
                return;
            }
            UUID id = PlayerUtil.uuidOf(name);
            if (id == null) {
                throw new UiError("Игрок " + name + " не найден.");
            }
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new UiError("Возьми предмет в руку.");
            }
            com.ecoserv.subworld.GlitchStorage.add("player:" + id, com.ecoserv.subworld.GlitchStorage.ADMIN, held.copy(),
                    "от " + player.getName());
            s.setToast("Положено в хранилище ГЛИТЧ игрока " + PlayerUtil.nameOf(id));
        }).picker(Ui.PICK_PLAYER).placeholder("ник").max(16);

        ui.header("Инструменты и блоки", "&8получить");
        tool(ui, player, ModRegistry.PORTAL_WAND, "Палочка порталов", "ПКМ по двум углам — новый портал, затем название в чат");
        tool(ui, player, ModRegistry.TRIGGER_WAND, "Палочка триггеров", "Зона, которая срабатывает при входе: сообщение, звук, команды");
        tool(ui, player, ModRegistry.ZONE_WAND, "Палочка зоны BLOCK DESTROY", "Зона, где игроки могут ломать блоки");
        tool(ui, player, ModRegistry.ZONE_WAND_DANGER, "Палочка опасной зоны", "Зона BLOCK DESTROY с уроном");
        tool(ui, player, ModRegistry.ACTIVATOR_WAND, "Палка активации", "Кнопка-активатор для квестов и команд");
        tool(ui, player, ModRegistry.FILL_WAND, "Палочка заливки", "Залить выделенную область блоком");
        tool(ui, player, ModRegistry.GLITCH_SELECT, "Палка GLITCH SELECT", "ПКМ по ГЛИТЧ-сундуку — что в нём показывать");
        tool(ui, player, Item.getItemFromBlock(ModRegistry.GLITCH_CHEST), "ГЛИТЧ-сундук", "Каждый игрок видит в нём свои пропавшие вещи");
        tool(ui, player, Item.getItemFromBlock(ModRegistry.ROLE_BARRIER), "Барьер ролей", "Пропускает только игроков с выбранными ролями");
        tool(ui, player, Item.getItemFromBlock(ModRegistry.LOOT_CHEST), "Сундук добычи", "У каждого игрока своя добыча");
        tool(ui, player, Item.getItemFromBlock(ModRegistry.CHECKPOINT), "Контрольная точка", "Точка возрождения для паркура");
        tool(ui, player, Item.getItemFromBlock(ModRegistry.PLAYER_SENSOR), "Датчик игрока", "Редстоун-сигнал, когда рядом игрок");
    }

    private static void tool(Ui ui, final EntityPlayerMP player, final Item item, String title, String desc) {
        if (item == null) {
            return;
        }
        ui.row(new ItemStack(item), "&f" + title).line("&7" + desc)
                .button("Получить", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, new ItemStack(item)));
    }
}

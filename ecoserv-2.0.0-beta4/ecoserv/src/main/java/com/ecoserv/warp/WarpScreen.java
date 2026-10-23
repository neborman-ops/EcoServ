package com.ecoserv.warp;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Меню варпов (1.9.0): все доступные варпы с поиском, свои варпы, создание и настройка. */
public class WarpScreen extends UiScreen {

    private enum Tab {
        ALL, MINE
    }

    private Tab tab = Tab.ALL;
    /** Открыт для правки (null — список). */
    private String editing;

    public WarpScreen(EntityPlayerMP player) {
        super(player);
    }

    public static void open(EntityPlayerMP player) {
        new WarpScreen(player).open();
    }

    @Override
    protected void build(Ui ui) {
        Warp w = editing == null ? null : WarpManager.get(editing);
        if (w != null && WarpManager.canEdit(player, w)) {
            buildEdit(ui, w);
            return;
        }
        editing = null;
        ui.title("Варпы").icon(UiIcons.compass());
        ui.tab("Все варпы", UiIcons.compass(), tab == Tab.ALL, () -> tab = Tab.ALL);
        ui.tab("Мои варпы", UiIcons.map(), tab == Tab.MINE, () -> tab = Tab.MINE);
        ui.sideLabel("&8Ещё");
        ui.sideButton("Меню миров", UiIcons.planet(), Ui.GREY,
                () -> com.ecoserv.subworld.menu.WorldsScreen.open(player));
        if (!ExtraConfig.warpsEnabled) {
            ui.note(Ui.YELLOW, Perms.isAdmin(player) ? "Варпы выключены в настройках мода (видишь, потому что админ)."
                    : "Варпы выключены на сервере.");
        }
        if (tab == Tab.MINE) {
            buildMine(ui);
        } else {
            buildAll(ui);
        }
    }

    private void buildAll(Ui ui) {
        ui.search("поиск по названию, миру, владельцу");
        List<Warp> list = WarpManager.visibleTo(player);
        ui.header("Варпы", Integer.toString(list.size()));
        if (list.isEmpty()) {
            ui.note(Ui.BLUE, "Варпов пока нет." + (Perms.isAdmin(player) ? " Поставь первый: вкладка «Мои варпы»." : ""));
        }
        boolean headerPlayers = false;
        for (final Warp w : list) {
            if (!w.server() && !headerPlayers) {
                headerPlayers = true;
                ui.header("Варпы игроков");
            }
            row(ui, w);
        }
    }

    private void row(Ui ui, final Warp w) {
        Ui.Row row = ui.row(UiUtil.icon(w.icon), "&e" + w.title);
        if (!w.description.isEmpty()) {
            row.line("&7" + w.description);
        }
        row.line("&8" + WorldNames.displayName(w.dim) + " · " + (int) w.x + " " + (int) w.y + " " + (int) w.z
                + (w.server() ? "" : " · " + w.ownerName) + " · переходов: " + w.visits);
        if (w.price > 0) {
            row.chip(Fmt.money(w.price), Ui.YELLOW);
        }
        if (!w.open) {
            row.chip("закрытый", Ui.GREY);
        }
        if (!w.role.isEmpty()) {
            row.chip("роль " + w.role, Ui.PURPLE);
        }
        String no = WarpManager.check(player, w);
        if (no == null) {
            row.button("Перейти", Ui.GREEN, () -> {
                close();
                WarpManager.go(player, w);
            });
            row.onClick(() -> {
                close();
                WarpManager.go(player, w);
            });
        } else {
            row.line("&c" + UiUtil.plain(no));
        }
        if (WarpManager.canEdit(player, w)) {
            row.button("⚙", Ui.GREY, () -> editing = w.name).tip("Настроить варп");
        }
    }

    private void buildMine(Ui ui) {
        boolean admin = Perms.isAdmin(player);
        int mine = WarpManager.countOf(player.getUniqueID());
        ui.header("Поставить варп здесь", admin ? "&8без ограничений" : mine + " / " + ExtraConfig.warpsPerPlayer);
        ui.text("&7Варп ставится там, где ты стоишь, и смотрит туда же, куда ты."
                + (ExtraConfig.warpSetPrice > 0 && !admin ? " Цена: &6" + Fmt.money(ExtraConfig.warpSetPrice) : ""));
        ui.field("Новый варп", "Название: буквы, цифры, _ и -", "", v -> {
            String err = WarpManager.create(player, v, false);
            if (err != null) {
                throw new UiError(UiUtil.plain(err));
            }
            toast("Варп «" + v.trim() + "» поставлен.");
        }).placeholder("мой_дом").max(32);
        if (admin) {
            ui.field("Новый варп сервера", "Виден всем, принадлежит серверу", "", v -> {
                String err = WarpManager.create(player, v, true);
                if (err != null) {
                    throw new UiError(UiUtil.plain(err));
                }
                toast("Варп сервера «" + v.trim() + "» поставлен.");
            }).placeholder("спавн").max(32);
        }
        ui.header("Мои варпы");
        int shown = 0;
        for (Warp w : WarpManager.all()) {
            if (player.getUniqueID().toString().equals(w.owner) || (admin && w.server())) {
                row(ui, w);
                shown++;
            }
        }
        if (shown == 0) {
            ui.text("&8Своих варпов пока нет.");
        }
    }

    private void buildEdit(Ui ui, final Warp w) {
        ui.back(() -> editing = null);
        ui.title("Варп " + w.title).subtitle(w.server() ? "варп сервера" : "владелец " + w.ownerName).icon(UiUtil.icon(w.icon));
        ui.sideLabel("&8Действия");
        ui.sideButton("Перейти", new ItemStack(Items.ENDER_PEARL), Ui.GREEN, () -> {
            close();
            WarpManager.go(player, w);
        });
        ui.sideButton("Точка = здесь", new ItemStack(Items.COMPASS), Ui.BLUE, () -> {
            w.dim = player.dimension;
            w.x = player.posX;
            w.y = player.posY;
            w.z = player.posZ;
            w.yaw = player.rotationYaw;
            w.pitch = player.rotationPitch;
            WarpManager.save();
            toast("Варп перенесён сюда.");
        });
        ui.sideButton("Удалить", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            WarpManager.remove(w.name);
            editing = null;
            toast("Варп удалён.");
        }).confirm("Удалить варп «" + w.title + "»?");
        ui.field("Название в меню", "& — цвета", w.title, v -> {
            w.title = v.trim().isEmpty() ? w.name : v.trim();
            WarpManager.save();
        }).max(48);
        ui.field("Описание", "", w.description, v -> {
            w.description = v.trim();
            WarpManager.save();
        }).placeholder("нет").clearable().max(120);
        ui.field("Иконка", "Предмет: кнопка … — выбрать", w.icon, v -> {
            String id = UiUtil.parseItemId(v);
            if (id == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            w.icon = id;
            WarpManager.save();
        }).picker(Ui.PICK_ITEM).max(96);
        ui.toggle("Виден всем", "Иначе — только тебе и твоей команде", w.open, on -> {
            w.open = on;
            WarpManager.save();
        });
        ui.money("Цена перехода", w.server() ? "Уходит серверу" : "Уходит тебе", w.price, v -> {
            w.price = v;
            WarpManager.save();
        });
        if (Perms.isAdmin(player)) {
            ui.field("Нужна роль", "id роли (пусто — всем)", w.role, v -> {
                w.role = v.trim().toLowerCase(java.util.Locale.ROOT);
                WarpManager.save();
            }).placeholder("всем").clearable().max(32);
        }
        ui.row(new ItemStack(Items.MAP), "&f" + WorldNames.displayName(w.dim) + " " + (int) w.x + " " + (int) w.y + " " + (int) w.z)
                .line("&8переходов: " + w.visits);
    }
}

package com.ecoserv.subworld.menu;

import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/** Настройки своего мира (1.9.0): название, иконка, кто может войти, правила, удаление. */
public class PlayerWorldScreen extends UiScreen {

    private final String id;

    public PlayerWorldScreen(EntityPlayerMP player, String id) {
        super(player);
        this.id = id;
    }

    @Override
    protected boolean canView() {
        Subworld sw = SubworldRegistry.get(id);
        return sw == null || Perms.isAdmin(player) || player.getUniqueID().toString().equals(sw.owner);
    }

    @Override
    protected void build(Ui ui) {
        ui.back(() -> new WorldsScreen(player, WorldsScreen.Tab.MYWORLDS).open());
        final Subworld sw = SubworldRegistry.get(id);
        if (sw == null) {
            ui.title("Мир удалён");
            ui.note(Ui.RED, "Этого мира больше нет.");
            return;
        }
        ui.title(UiUtil.plain(sw.name)).subtitle("свой мир · " + sw.typeTitle()).icon(UiUtil.icon(sw.icon));
        ui.sideLabel("&8Действия");
        ui.sideButton("Войти", new ItemStack(Items.ENDER_PEARL), Ui.GREEN, () -> {
            close();
            SubworldTeleport.enter(player, sw, false);
        });
        ui.sideButton("Удалить мир", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            SubworldManager.delete(sw);
            new WorldsScreen(player, WorldsScreen.Tab.MYWORLDS).withToast("Мир удалён.").open();
        }).confirm("Удалить мир «" + sw.name + "» НАВСЕГДА вместе со всеми постройками?");

        ui.header("Основное");
        ui.field("Название", "", sw.name, v -> {
            if (v.trim().isEmpty()) {
                throw new UiError("Название не может быть пустым.");
            }
            sw.name = v.trim();
            changed();
        }).max(32);
        ui.field("Описание", "", sw.description, v -> {
            sw.description = v.trim();
            changed();
        }).clearable().max(120);
        ui.field("Иконка", "Предмет: кнопка … — выбрать", sw.icon, v -> {
            String item = UiUtil.parseItemId(v);
            if (item == null) {
                throw new UiError("Не нашёл такой предмет.");
            }
            sw.icon = item;
            changed();
        }).picker(Ui.PICK_ITEM).max(96);

        ui.header("Кто может войти", Subworld.ACCESS_PRIVATE.equals(sw.access) ? sw.members.size() + " / "
                + ExtraConfig.playerWorldMembers : "все");
        ui.toggle("Открыт для всех", "Иначе — только ты и приглашённые", !Subworld.ACCESS_PRIVATE.equals(sw.access), on -> {
            sw.access = on ? Subworld.ACCESS_ALWAYS : Subworld.ACCESS_PRIVATE;
            changed();
        });
        ui.field("Пригласить игрока", "Ник", "", v -> {
            UUID uid = PlayerUtil.uuidOf(v.trim());
            if (uid == null) {
                throw new UiError("Не знаю игрока «" + v.trim() + "».");
            }
            if (sw.members.size() >= ExtraConfig.playerWorldMembers && !Perms.isAdmin(player)) {
                throw new UiError("Больше приглашать нельзя (предел " + ExtraConfig.playerWorldMembers + ").");
            }
            if (!com.ecoserv.prefs.PlayerPrefs.accepts(uid, player, com.ecoserv.prefs.PlayerPrefs.Kind.INVITE)) {
                throw new UiError(UiUtil.plain(com.ecoserv.prefs.PlayerPrefs.refusal(PlayerUtil.nameOf(uid),
                        com.ecoserv.prefs.PlayerPrefs.Kind.INVITE)));
            }
            if (!sw.members.contains(uid.toString())) {
                sw.members.add(uid.toString());
                changed();
                EntityPlayerMP target = PlayerUtil.online(uid);
                if (target != null) {
                    com.ecoserv.util.ChatUtil.send(target, "&a" + player.getName() + " пригласил тебя в свой мир &f"
                            + sw.name + "&a — меню миров → «Мои миры».");
                }
            }
            toast(PlayerUtil.nameOf(uid) + " может заходить в мир.");
        }).picker(Ui.PICK_PLAYER).max(32);
        for (final String m : sw.members.toArray(new String[0])) {
            String name;
            try {
                name = PlayerUtil.nameOf(UUID.fromString(m));
            } catch (IllegalArgumentException e) {
                name = m;
            }
            ui.row(new ItemStack(Items.SKULL, 1, 3), "&f" + name).button("Убрать", Ui.RED, () -> {
                sw.members.remove(m);
                changed();
            });
        }

        ui.header("Правила");
        ui.toggle("PvP", "", sw.rules.pvp, on -> {
            sw.rules.pvp = on;
            changed();
        });
        ui.toggle("Мобы", "Враждебные мобы появляются", sw.rules.mobs, on -> {
            sw.rules.mobs = on;
            changed();
        });
        ui.toggle("Сохранять инвентарь при смерти", "", sw.rules.keepInventory, on -> {
            sw.rules.keepInventory = on;
            changed();
        });
        ui.select("Время", "", sw.rules.time, v -> {
            sw.rules.time = v;
            changed();
        }).option("cycle", "Обычная смена дня и ночи").option("day", "Всегда день").option("noon", "Полдень")
                .option("sunset", "Закат").option("night", "Ночь");
        ui.select("Погода", "", sw.rules.weather, v -> {
            sw.rules.weather = v;
            changed();
        }).option("cycle", "Как обычно").option("clear", "Всегда ясно").option("rain", "Дождь").option("thunder", "Гроза");
    }

    private static void changed() {
        SubworldRegistry.markDirty();
        SubworldRegistry.save();
    }
}

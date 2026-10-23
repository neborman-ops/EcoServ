package com.ecoserv.subworld.menu;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAccess;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Один локальный мир (сохранение) команды: войти, сделать активным, переименовать, удалить,
 * гости — пригласить по нику, разрешить строить, выгнать.
 */
public class SaveScreen extends UiScreen {

    /** Гостей на один мир. */
    public static final int MAX_GUESTS = 32;

    private final int dim;

    public SaveScreen(EntityPlayerMP player, int dim) {
        super(player);
        this.dim = dim;
    }

    @Nullable
    private SubworldDim save() {
        SubworldDim d = SubworldRegistry.dim(dim);
        return d != null && d.isInstance() ? d : null;
    }

    private boolean member(SubworldDim d) {
        return Perms.isAdmin(player) || SubworldTeams.isMember(player.getUniqueID(), d.owner);
    }

    private boolean leader(SubworldDim d) {
        return Perms.isAdmin(player) || SubworldTeams.isLeader(player.getUniqueID(), d.owner)
                || !SubworldTeams.available() && d.owner.equals("player:" + player.getUniqueID());
    }

    @Override
    protected void build(Ui ui) {
        ui.back(() -> new WorldsScreen(player, WorldsScreen.Tab.TEAM).open());
        final SubworldDim d = save();
        final Subworld sw = d == null ? null : SubworldRegistry.get(d.subworld);
        if (d == null || sw == null || !member(d)) {
            ui.title("Мир недоступен");
            ui.note(Ui.RED, "Этого мира больше нет или ты не участник его команды.");
            return;
        }
        SubworldDim active = SubworldRegistry.activeInstance(sw.id, d.owner);
        boolean isActive = active != null && active.dim == d.dim;
        ui.title(d.saveName).subtitle(UiUtil.plain(sw.name)).icon(UiUtil.icon(sw.icon));
        WorldServer w = DimensionManager.getWorld(d.dim);
        Ui.Row info = ui.row(UiUtil.icon(sw.icon), "&6" + d.saveName)
                .line("&7Подмир: &f" + UiUtil.plain(sw.name) + " &7· владелец: &f" + SubworldTeams.ownerName(d.owner))
                .line("&8создан " + Fmt.timestamp(d.created) + (d.border > 0 ? " · барьер " + d.border
                        + (sw.borderMax > 0 ? "/" + sw.borderMax : "") : "")
                        + (w != null ? " · сейчас играют: " + w.playerEntities.size() : ""));
        if (isActive) {
            info.chip("активный", Ui.GREEN);
        }
        if (player.dimension == d.dim) {
            info.chip("ты здесь", Ui.BLUE);
        }
        Ui.Buttons b = ui.buttons();
        b.add("Войти", Ui.GREEN, () -> {
            SubworldAccess.Result access = SubworldAccess.check(player, sw);
            if (!access.ok && !Perms.isAdmin(player)) {
                throw new UiError(access.reason == null ? "Нет доступа к подмиру." : UiUtil.plain(access.reason));
            }
            SubworldRegistry.setActive(sw.id, d.owner, d.dim);
            close();
            SubworldTeleport.teleportTo(player, d, false, null);
        });
        b.add(isActive ? "Уже активный" : "Сделать активным", Ui.BLUE, () -> {
            SubworldRegistry.setActive(sw.id, d.owner, d.dim);
            toast("Теперь вход в «" + UiUtil.plain(sw.name) + "» ведёт в этот мир.");
        }).disabled(isActive).tip("Активный мир — куда ведут вход в подмир и порталы");
        ui.field("Название мира", "Видно в меню миров", d.saveName, v -> {
            String n = v.trim();
            if (n.isEmpty()) {
                throw new UiError("Название не может быть пустым.");
            }
            d.saveName = n.length() > 32 ? n.substring(0, 32) : n;
            SubworldRegistry.save();
        }).max(32);

        // гости
        ui.header("Гости", sw.guests ? d.guests.size() + "/" + MAX_GUESTS : "&8выключены");
        if (!sw.guests) {
            ui.note(Ui.GREY, "В этом подмире гостей звать нельзя (так настроил админ).");
        } else {
            ui.text("&7Гость может заходить в этот мир (только в этот, не во все миры команды). С правом стройки — ещё "
                    + "и строить. Чтобы человек играл во всех ваших мирах — добавь его в команду (вкладка «Команда»).");
            for (final SubworldDim.Guest g : d.guests) {
                boolean online = PlayerUtil.online(safe(g.uuid)) != null;
                ui.row(new ItemStack(g.build ? Items.IRON_PICKAXE : Items.ENDER_EYE), (online ? "&b" : "&7") + g.name)
                        .chip(g.build ? "строит" : "смотрит", g.build ? Ui.GREEN : Ui.GREY)
                        .chip(online ? "в сети" : "не в сети", online ? Ui.GREEN : Ui.GREY)
                        .button(g.build ? "Запретить строить" : "Разрешить строить", Ui.BLUE, () -> {
                            g.build = !g.build;
                            SubworldRegistry.save();
                        })
                        .button("Выгнать", Ui.RED, () -> {
                            kick(d, g);
                            toast(g.name + " больше не гость.");
                        }).confirm("Убрать " + g.name + " из гостей этого мира?");
            }
            if (d.guests.size() < MAX_GUESTS) {
                ui.field("Позвать гостя", "Ник игрока — он сможет заходить в этот мир (команда: /world invite <ник>)",
                        "", v -> {
                            String name = v.trim();
                            if (name.isEmpty()) {
                                return;
                            }
                            String err = invite(player, d, name, false);
                            if (err != null) {
                                throw new UiError(UiUtil.plain(err));
                            }
                            toast("Приглашён: " + name);
                        }).picker(Ui.PICK_PLAYER).placeholder("ник игрока").max(16);
            }
        }

        // удаление
        ui.header("Опасно");
        boolean leader = leader(d);
        ui.buttons().add("Удалить этот мир", Ui.RED, () -> {
            if (!leader(d)) {
                throw new UiError("Удалить мир может только глава команды.");
            }
            SubworldManager.deleteDim(d, "&eЭтот локальный мир удалён.");
            ChatUtil.send(player, "&aМир «" + d.saveName + "» удалён.");
            new WorldsScreen(player, WorldsScreen.Tab.TEAM).withToast("Мир «" + d.saveName + "» удалён.").open();
        }).disabled(!leader).tip(leader ? "Мир будет стёрт безвозвратно" : "Только глава команды")
                .confirm("Стереть мир «" + d.saveName + "» навсегда? Всё построенное в нём пропадёт.");
    }

    @Nullable
    private static UUID safe(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    static void kick(SubworldDim d, SubworldDim.Guest g) {
        d.guests.remove(g);
        SubworldRegistry.save();
        UUID id = safe(g.uuid);
        EntityPlayerMP guest = id == null ? null : PlayerUtil.online(id);
        if (guest != null && guest.dimension == d.dim && !SubworldTeams.isMember(guest.getUniqueID(), d.owner)
                && !Perms.isAdmin(guest)) {
            SubworldTeleport.evict(guest, "&eТебя больше не ждут в этом мире.");
        }
    }

    /** Пригласить гостя в мир. Возвращает ошибку или null. */
    @Nullable
    public static String invite(EntityPlayerMP who, SubworldDim d, String name, boolean build) {
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null) {
            return "&cЭтого подмира уже нет.";
        }
        if (!sw.guests) {
            return "&cВ этом подмире гостей звать нельзя.";
        }
        if (!Perms.isAdmin(who) && !SubworldTeams.isMember(who.getUniqueID(), d.owner)) {
            return "&cЗвать гостей могут только участники команды этого мира.";
        }
        UUID uuid = PlayerUtil.knownUuid(name);
        if (uuid == null) {
            return "&cИгрок &f" + name + " &cне найден (он должен хотя бы раз зайти на сервер).";
        }
        if (SubworldTeams.isMember(uuid, d.owner)) {
            return "&e" + name + " и так в команде этого мира.";
        }
        SubworldDim.Guest existing = d.guest(uuid.toString());
        if (existing != null) {
            if (existing.build != build && build) {
                existing.build = true;
                SubworldRegistry.save();
                return null;
            }
            return "&e" + name + " уже приглашён.";
        }
        if (d.guests.size() >= MAX_GUESTS) {
            return "&cГостей уже " + MAX_GUESTS + " — больше нельзя. Выгони кого-нибудь.";
        }
        SubworldDim.Guest g = new SubworldDim.Guest();
        g.uuid = uuid.toString();
        g.name = PlayerUtil.nameOf(uuid);
        g.build = build;
        d.guests.add(g);
        SubworldRegistry.save();
        EntityPlayerMP guest = PlayerUtil.online(uuid);
        if (guest != null) {
            ChatUtil.send(guest, "&b" + who.getName() + " &aзовёт тебя в гости в мир «" + d.saveName + "» (" + sw.name + "&a)"
                    + (build ? ", можно строить" : "") + ".");
            ChatUtil.send(guest, ChatUtil.link("&b[Войти в гости]", "/world visit " + who.getName(), "Войти в мир " + d.saveName));
        }
        return null;
    }
}

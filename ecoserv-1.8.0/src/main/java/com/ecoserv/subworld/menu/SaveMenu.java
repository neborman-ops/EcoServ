package com.ecoserv.subworld.menu;

import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * Одно сохранение (локальный мир) команды: войти, сделать активным, переименовать, удалить,
 * гости (своя фишка 2): пригласить по нику, разрешить строить, выгнать.
 */
public class SaveMenu extends SubMenu {

    /** Гостей на одно сохранение — столько помещается в меню. */
    private static final int MAX_GUESTS = 16;

    private final int dim;
    private long deleteArmed;

    public SaveMenu(EntityPlayerMP player, int dim) {
        super(player, "Сохранение", 6);
        this.dim = dim;
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new SaveMenu(who, dim);
    }

    private SubworldDim save() {
        return SubworldRegistry.dim(dim);
    }

    private boolean member(SubworldDim d) {
        return Perms.isAdmin(player) || SubworldTeams.isMember(player.getUniqueID(), d.owner);
    }

    private boolean leader(SubworldDim d) {
        return Perms.isAdmin(player) || SubworldTeams.isLeader(player.getUniqueID(), d.owner)
                || !SubworldTeams.available();
    }

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        SubworldDim d = save();
        Subworld sw = d == null ? null : SubworldRegistry.get(d.subworld);
        if (d == null || sw == null || !member(d)) {
            setButton(22, Icons.red("&cСохранение недоступно"));
            setButton(45, Icons.back());
            return;
        }
        SubworldDim active = SubworldRegistry.activeInstance(sw.id, d.owner);
        boolean isActive = active != null && active.dim == d.dim;
        setButton(4, Icons.of(icon(sw.icon), "&6" + d.saveName, "&7Подмир: &f" + sw.name,
                "&7Владелец: &f" + SubworldTeams.ownerName(d.owner),
                isActive ? "&a✔ активное" : "&7не активное",
                d.border > 0 ? "&7Барьер: &f" + d.border + (sw.borderMax > 0 ? " / " + sw.borderMax : "") : "",
                "&7Создан: &f" + Fmt.timestamp(d.created)));
        setButton(10, Icons.of(Items.ENDER_PEARL, "&aВойти", "&8ЛКМ"));
        setButton(11, Icons.of(Items.NETHER_STAR, isActive ? "&aУже активное" : "&fСделать активным",
                "&7Вход через «Все миры» ведёт в активное"));
        setButton(12, Icons.of(Items.NAME_TAG, "&fПереименовать", "&8ЛКМ"));
        boolean armed = armed(deleteArmed);
        setButton(16, Icons.of(new ItemStack(Blocks.TNT), armed ? "&c&lЕщё раз Shift+ЛКМ — стереть мир" : "&cУдалить сохранение",
                "&7Мир будет стёрт безвозвратно", leader(d) ? "&8Shift+ЛКМ" : "&cТолько глава команды"));
        // гости
        if (sw.guests) {
            setButton(19, Icons.of(Items.WRITABLE_BOOK, "&bПригласить гостя", "&7Ник игрока — он сможет", "&7заходить в этот мир",
                    "&8ЛКМ — ввести ник"));
            int slot = 28;
            for (SubworldDim.Guest g : d.guests) {
                if (slot > 43) {
                    break;
                }
                setButton(slot++, Icons.of(g.build ? Items.IRON_PICKAXE : Items.ENDER_EYE, "&b" + g.name,
                        g.build ? "&aможет строить" : "&7только смотрит",
                        "&8ЛКМ — переключить стройку, Shift+ЛКМ — выгнать"));
            }
            if (d.guests.isEmpty()) {
                setButton(28, Icons.of(Items.PAPER, "&7Гостей нет"));
            } else if (d.guests.size() > MAX_GUESTS) {
                setButton(20, Icons.of(Items.PAPER, "&7И ещё гостей: &f" + (d.guests.size() - MAX_GUESTS),
                        "&7Выгони кого-нибудь из списка,", "&7чтобы увидеть остальных"));
            }
        } else {
            setButton(19, Icons.of(new ItemStack(Blocks.BARRIER), "&7Гости в этом подмире выключены"));
        }
        setButton(45, Icons.back());
        setButton(53, Icons.close());
    }

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        boolean shift = type == ClickType.QUICK_MOVE;
        final SubworldDim d = save();
        if (slot == 45) {
            new WorldsMenu(player, WorldsMenu.TAB_TEAM, 0).open();
            return;
        }
        if (slot == 53) {
            player.closeScreen();
            return;
        }
        if (d == null || !member(d)) {
            return;
        }
        final Subworld sw = SubworldRegistry.get(d.subworld);
        if (sw == null) {
            return;
        }
        switch (slot) {
            case 10: {
                com.ecoserv.subworld.SubworldAccess.Result access = com.ecoserv.subworld.SubworldAccess.check(player, sw);
                if (!access.ok) {
                    ChatUtil.send(player, access.reason == null ? "&cНет доступа к подмиру." : access.reason);
                    return;
                }
                SubworldRegistry.setActive(sw.id, d.owner, d.dim);
                player.closeScreen();
                SubworldTeleport.teleportTo(player, d, false, null);
                return;
            }
            case 11:
                SubworldRegistry.setActive(sw.id, d.owner, d.dim);
                return;
            case 12:
                prompt("&eНовое название сохранения:", new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        String n = text.trim();
                        if (!n.isEmpty()) {
                            d.saveName = n.length() > 32 ? n.substring(0, 32) : n;
                            SubworldRegistry.save();
                        }
                    }
                });
                return;
            case 16:
                if (!shift || !leader(d)) {
                    return;
                }
                if (confirmed(deleteArmed)) {
                    SubworldManager.deleteDim(d, "&eЭтот локальный мир удалён.");
                    ChatUtil.send(player, "&aСохранение «" + d.saveName + "» удалено.");
                    new WorldsMenu(player, WorldsMenu.TAB_TEAM, 0).open();
                } else {
                    deleteArmed = arm();
                }
                return;
            case 19:
                if (!sw.guests) {
                    return;
                }
                prompt("&eНик игрока, которого пригласить:", new Input() {
                    @Override
                    public void accept(EntityPlayerMP who, String text) {
                        invite(who, d, text.trim());
                    }
                });
                return;
            default:
        }
        if (slot >= 28 && slot <= 43 && sw.guests) {
            int index = slot - 28;
            if (index >= d.guests.size()) {
                return;
            }
            SubworldDim.Guest g = d.guests.get(index);
            if (shift) {
                d.guests.remove(g);
                SubworldRegistry.save();
                try {
                    EntityPlayerMP guest = PlayerUtil.online(UUID.fromString(g.uuid));
                    if (guest != null && guest.dimension == d.dim && !SubworldTeams.isMember(guest.getUniqueID(), d.owner)) {
                        SubworldTeleport.evict(guest, "&eТебя больше не ждут в этом мире.");
                    }
                } catch (IllegalArgumentException ignored) {
                    // старая запись без uuid
                }
                return;
            }
            g.build = !g.build;
            SubworldRegistry.save();
        }
    }

    private static void invite(EntityPlayerMP who, SubworldDim d, String name) {
        UUID uuid = PlayerUtil.knownUuid(name);
        if (uuid == null) {
            ChatUtil.send(who, "&cИгрок &f" + name + " &cне найден (он должен хотя бы раз зайти на сервер).");
            return;
        }
        if (SubworldTeams.isMember(uuid, d.owner)) {
            ChatUtil.send(who, "&e" + name + " и так в твоей команде.");
            return;
        }
        if (d.guest(uuid.toString()) != null) {
            ChatUtil.send(who, "&e" + name + " уже приглашён.");
            return;
        }
        if (d.guests.size() >= MAX_GUESTS) {
            ChatUtil.send(who, "&cГостей уже " + MAX_GUESTS + " — больше нельзя. Выгони кого-нибудь.");
            return;
        }
        SubworldDim.Guest g = new SubworldDim.Guest();
        g.uuid = uuid.toString();
        g.name = PlayerUtil.nameOf(uuid);
        d.guests.add(g);
        SubworldRegistry.save();
        ChatUtil.send(who, "&aПриглашён: &f" + g.name);
        EntityPlayerMP guest = PlayerUtil.online(uuid);
        Subworld sw = SubworldRegistry.get(d.subworld);
        if (guest != null && sw != null) {
            ChatUtil.send(guest, "&b" + who.getName() + " &aприглашает тебя в свой мир «" + d.saveName + "» (" + sw.name
                    + "&a). Меню миров → вкладка «Команда».");
        }
    }
}

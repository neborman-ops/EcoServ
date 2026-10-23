package com.ecoserv.subworld.menu;

import com.ecoserv.config.EcoConfig;
import com.ecoserv.init.ModRegistry;
import com.ecoserv.menu.ChestMenu;
import com.ecoserv.menu.Icons;
import com.ecoserv.region.Region;
import com.ecoserv.region.RegionMenu;
import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldAccess;
import com.ecoserv.subworld.SubworldDim;
import com.ecoserv.subworld.SubworldManager;
import com.ecoserv.subworld.SubworldRegistry;
import com.ecoserv.subworld.SubworldTeams;
import com.ecoserv.subworld.SubworldTeleport;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню миров (кнопка-планета в инвентаре, п. 3). Вкладки игрока: «Все миры», «Команда»;
 * админские: «Создать подмир», «Настроить порталы».
 */
public class WorldsMenu extends SubMenu {

    public static final int TAB_ALL = 0;
    public static final int TAB_TEAM = 1;

    /** Кнопка в списке. */
    private interface Click {
        void run(boolean shift, boolean right);
    }

    private static final class Entry {
        final ItemStack icon;
        final Click click;

        Entry(ItemStack icon, Click click) {
            this.icon = icon;
            this.click = click;
        }
    }

    private final int tab;
    private int page;
    private final List<Entry> shown = new ArrayList<Entry>();
    /** Покупка / новое платное сохранение ждёт второго нажатия. */
    private String pending = "";
    private long pendingUntil;

    public WorldsMenu(EntityPlayerMP player, int tab, int page) {
        super(player, tab == TAB_TEAM ? "Миры: команда" : "Миры", 6);
        this.tab = tab;
        this.page = page;
    }

    public static void open(EntityPlayerMP player) {
        if (!EcoConfig.subworldsEnabled && !Perms.isAdmin(player)) {
            ChatUtil.send(player, "&cПодмиры выключены на сервере.");
            return;
        }
        new WorldsMenu(player, TAB_ALL, 0).open();
    }

    @Override
    protected ChestMenu reopen(EntityPlayerMP who) {
        return new WorldsMenu(who, tab, page);
    }

    private boolean admin() {
        return Perms.isAdmin(player);
    }

    // --- отрисовка ------------------------------------------------------------------------

    @Override
    protected void buildButtons() {
        for (int i = 0; i < topSize; i++) {
            setButton(i, ItemStack.EMPTY);
        }
        fillRow(0, filler());
        fillRow(5, filler());
        ItemStack all = Icons.of(new ItemStack(Blocks.GRASS), (tab == TAB_ALL ? "&a▶ " : "&f") + "Все миры",
                "&7Подмиры сервера: вход, доступ, покупка");
        ItemStack team = Icons.of(Items.BANNER, 4, (tab == TAB_TEAM ? "&a▶ " : "&f") + "Команда",
                "&7Локальные миры твоей команды,", "&7сохранения и гости");
        setButton(0, tab == TAB_ALL ? Icons.glint(all) : all);
        setButton(1, tab == TAB_TEAM ? Icons.glint(team) : team);
        setButton(4, location());
        if (admin()) {
            setButton(7, Icons.of(Items.NETHER_STAR, "&dСоздать подмир", "&7Тип мира, доступ, локальные миры,",
                    "&7сохранения, свои квесты", "&8ЛКМ — открыть мастер"));
            setButton(8, Icons.of(Items.ENDER_EYE, "&dНастроить порталы", "&7Список порталов с поиском по названию:",
                    "&7куда ведёт каждый", "&8ЛКМ — открыть"));
            setButton(47, Icons.of(ModRegistry.PORTAL_WAND, "&dПалочка порталов",
                    "&7ПКМ — точка 1, ПКМ — точка 2,", "&7затем название в чат", "&8ЛКМ — получить"));
        }
        shown.clear();
        if (tab == TAB_TEAM) {
            buildTeam();
        } else {
            buildAll();
        }
        page = Math.min(page, pages(shown.size(), PER_PAGE) - 1);
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < shown.size(); i++) {
            setButton(9 + i, shown.get(page * PER_PAGE + i).icon);
        }
        if (page > 0) {
            setButton(45, Icons.arrowLeft("&eНазад"));
        }
        if (page < pages(shown.size(), PER_PAGE) - 1) {
            setButton(53, Icons.arrowRight("&eДальше"));
        }
        setButton(49, Icons.close());
    }

    private ItemStack location() {
        Subworld here = SubworldRegistry.subworldOf(player.dimension);
        if (here == null) {
            return Icons.of(Items.COMPASS, "&fТы в мире: &e" + WorldNames.displayName(player.dimension),
                    "&7Выбери подмир ниже, чтобы войти");
        }
        SubworldDim d = SubworldRegistry.dim(player.dimension);
        String where = d == null ? "" : d.isTemplate() ? " &d(шаблон)" : d.isInstance() ? " &7— " + d.saveName : "";
        return Icons.of(Items.COMPASS, "&fТы в подмире: &e" + here.name + where,
                "&7Вернуться: &f" + WorldNames.displayName(SubworldTeleport.returnDim(player)),
                "&8ЛКМ — вернуться туда, откуда пришёл");
    }

    private int playersIn(Subworld sw) {
        int n = 0;
        for (SubworldDim d : SubworldRegistry.dimsOf(sw.id)) {
            WorldServer w = DimensionManager.getWorld(d.dim);
            if (w != null) {
                n += w.playerEntities.size();
            }
        }
        return n;
    }

    private void buildAll() {
        List<Subworld> list = SubworldRegistry.all();
        list.sort((a, b) -> ChatUtil.strip(ChatUtil.color(a.name)).compareToIgnoreCase(ChatUtil.strip(ChatUtil.color(b.name))));
        boolean admin = admin();
        for (final Subworld sw : list) {
            if (!sw.enabled && !admin) {
                continue;
            }
            List<String> lore = new ArrayList<String>();
            lore.addAll(Icons.wrap(sw.description, 34, "&7"));
            lore.add("&7Тип: &f" + sw.typeTitle());
            SubworldAccess.Result access = SubworldAccess.check(player, sw);
            lore.add("&7Доступ: " + SubworldAccess.status(player, sw) + " &8(" + ChatUtil.strip(sw.accessTitle()) + ")");
            if (sw.perOwner()) {
                lore.add("&7Локальные миры: &f" + (sw.perPlayer() ? "у каждого игрока свой" : "у каждой команды свой"));
                lore.add("&7Сохранений: до &f" + sw.maxSaves + (sw.savePrice > 0 ? " &7(следующие по &6"
                        + Fmt.money(sw.savePrice) + "&7)" : ""));
                if (sw.borderStart > 0) {
                    lore.add("&7Барьер: &f" + sw.borderStart + " &7блоков, растёт за задания");
                }
            }
            if (sw.ownQuests) {
                lore.add("&dСвои квесты и свой магазин");
            }
            if (sw.glitch) {
                lore.add("&5&lGLITCH");
            }
            int online = playersIn(sw);
            if (online > 0) {
                lore.add("&7Сейчас играют: &f" + online);
            }
            if (!sw.enabled) {
                lore.add("&cвыключен — видят только админы");
            }
            boolean waiting = pending.equals("buy:" + sw.id) && armed(pendingUntil);
            if (access.ok) {
                lore.add("&8ЛКМ — войти");
            } else if (access.canBuy) {
                lore.add(waiting ? "&e&lНажми ещё раз — купить за " + Fmt.money(sw.price) : "&8ЛКМ — купить доступ");
            } else if (access.reason != null) {
                lore.addAll(Icons.wrap(ChatUtil.strip(ChatUtil.color(access.reason)), 34, "&c"));
            }
            if (admin) {
                lore.add("&8Shift+ЛКМ — настройки подмира");
                lore.add("&8ПКМ — " + (sw.perOwner() ? "в шаблон (правка)" : "войти как админ"));
            }
            ItemStack icon = Icons.of(icon(sw.icon), (sw.enabled ? "&e" : "&7") + sw.name, lore.toArray(new String[0]));
            if (waiting || SubworldRegistry.subworldOf(player.dimension) == sw) {
                Icons.glint(icon);
            }
            shown.add(new Entry(icon, new Click() {
                @Override
                public void run(boolean shift, boolean right) {
                    clickSubworld(sw, shift, right);
                }
            }));
        }
        if (shown.isEmpty()) {
            shown.add(new Entry(Icons.of(Items.MAP, "&7Подмиров пока нет",
                    admin ? "&7Создай первый: кнопка «Создать подмир»" : "&7Их создают админы сервера"), null));
        }
    }

    private void buildTeam() {
        List<Subworld> local = new ArrayList<Subworld>();
        for (Subworld sw : SubworldRegistry.all()) {
            if (sw.perOwner() && (sw.enabled || admin())) {
                local.add(sw);
            }
        }
        String teamKey = SubworldTeams.teamKey(player.getUniqueID());
        List<String> info = new ArrayList<String>();
        if (teamKey != null) {
            info.add("&7Локальные миры общие для всей команды:");
            for (java.util.UUID m : SubworldTeams.members(teamKey)) {
                info.add(" &f" + PlayerUtil.nameOf(m));
            }
        } else if (SubworldTeams.available()) {
            info.add("&7Ты не в команде — локальные миры только твои.");
            info.add("&7Команду создают в меню FTB (команды).");
        } else {
            info.add("&7Локальные миры — у каждого игрока свои.");
        }
        shown.add(new Entry(Icons.of(Items.BANNER, 1, "&6" + (teamKey == null ? "Ты: " + player.getName()
                : "Команда: " + SubworldTeams.ownerName(teamKey)), info.toArray(new String[0])), null));
        for (final Subworld sw : local) {
            final String owner = SubworldTeams.ownerKey(player, sw);
            List<SubworldDim> saves = SubworldRegistry.instances(sw.id, owner);
            SubworldDim active = SubworldRegistry.activeInstance(sw.id, owner);
            for (final SubworldDim d : saves) {
                boolean isActive = active != null && active.dim == d.dim;
                List<String> lore = new ArrayList<String>();
                lore.add("&7Подмир: &f" + sw.name + (sw.perPlayer() ? " &5(личный)" : ""));
                if (isActive) {
                    lore.add("&a✔ активное сохранение (вход через «Все миры» ведёт сюда)");
                }
                if (d.border > 0) {
                    lore.add("&7Барьер: &f" + d.border + (sw.borderMax > 0 ? " / " + sw.borderMax : "") + " &7блоков");
                }
                lore.add("&7Гостей: &f" + d.guests.size());
                lore.add("&7Создан: &f" + Fmt.timestamp(d.created));
                lore.add("&8ЛКМ — войти, Shift+ЛКМ — управление и гости");
                ItemStack icon = Icons.of(icon(sw.icon), "&e" + d.saveName, lore.toArray(new String[0]));
                if (player.dimension == d.dim || isActive) {
                    Icons.glint(icon);
                }
                shown.add(new Entry(icon, new Click() {
                    @Override
                    public void run(boolean shift, boolean right) {
                        if (shift) {
                            new SaveMenu(player, d.dim).open();
                            return;
                        }
                        SubworldRegistry.setActive(sw.id, owner, d.dim);
                        player.closeScreen();
                        if (SubworldAccess.check(player, sw).ok || !sw.enabled && Perms.isAdmin(player)) {
                            SubworldTeleport.teleportTo(player, d, false, null);
                        } else {
                            ChatUtil.send(player, "&cНет доступа к подмиру «" + sw.name + "&c».");
                        }
                    }
                }));
            }
            int left = SubworldManager.savesLeft(sw, owner);
            if (left > 0) {
                final long price = SubworldManager.nextSavePrice(sw, owner);
                final boolean waiting = pending.equals("save:" + sw.id) && armed(pendingUntil);
                ItemStack plus = Icons.of(Items.MAP, "&a+ " + (saves.isEmpty() ? "Создать мир" : "Новое сохранение") + " &7(" + sw.name + "&7)",
                        "&7Свободно сохранений: &f" + left,
                        price > 0 ? "&7Цена: &6" + Fmt.money(price) : "&7Бесплатно",
                        waiting ? "&e&lНажми ещё раз для оплаты" : "&8ЛКМ — создать и войти");
                shown.add(new Entry(waiting ? Icons.glint(plus) : plus, new Click() {
                    @Override
                    public void run(boolean shift, boolean right) {
                        if (!SubworldAccess.check(player, sw).ok) {
                            ChatUtil.send(player, "&cСначала открой доступ к подмиру «" + sw.name + "&c» во вкладке «Все миры».");
                            return;
                        }
                        if (price > 0 && !(pending.equals("save:" + sw.id) && confirmed(pendingUntil))) {
                            pending = "save:" + sw.id;
                            pendingUntil = arm();
                            return;
                        }
                        pending = "";
                        final String ownerKey = SubworldTeams.ownerKey(player, sw);
                        prompt("&eНазвание нового мира (или &f-&e — по умолчанию):", new Input() {
                            @Override
                            public void accept(EntityPlayerMP who, String text) {
                                String name = text.trim().equals("-") ? "" : text.trim();
                                if (name.length() > 32) {
                                    name = name.substring(0, 32);
                                }
                                SubworldDim created = SubworldManager.createInstance(sw, ownerKey,
                                        SubworldTeams.ownerName(ownerKey), name, who);
                                if (created != null) {
                                    ChatUtil.send(who, "&aСоздан мир &f" + created.saveName);
                                    SubworldTeleport.teleportTo(who, created, true, null);
                                }
                            }
                        });
                    }
                }));
            }
        }
        // куда пригласили
        String uuid = player.getUniqueID().toString();
        for (final SubworldDim d : SubworldRegistry.dims()) {
            if (!d.isInstance() || d.guest(uuid) == null) {
                continue;
            }
            Subworld sw = SubworldRegistry.get(d.subworld);
            if (sw == null) {
                continue;
            }
            SubworldDim.Guest g = d.guest(uuid);
            shown.add(new Entry(Icons.of(Items.ENDER_PEARL, "&bВ гостях: &f" + SubworldTeams.ownerName(d.owner),
                    "&7Подмир: &f" + sw.name + " &7— " + d.saveName,
                    g.build ? "&aможно строить" : "&7только смотреть",
                    "&8ЛКМ — войти в гости"), new Click() {
                @Override
                public void run(boolean shift, boolean right) {
                    player.closeScreen();
                    SubworldTeleport.visit(player, d);
                }
            }));
        }
        if (local.isEmpty()) {
            shown.add(new Entry(Icons.of(Items.MAP, "&7Подмиров с локальными мирами нет"), null));
        }
    }

    // --- клики ---------------------------------------------------------------------------------

    @Override
    protected void onButton(int slot, ClickType type, int dragType) {
        boolean shift = type == ClickType.QUICK_MOVE;
        boolean right = dragType == 1 && type == ClickType.PICKUP;
        switch (slot) {
            case 0:
                new WorldsMenu(player, TAB_ALL, 0).open();
                return;
            case 1:
                new WorldsMenu(player, TAB_TEAM, 0).open();
                return;
            case 4:
                if (SubworldRegistry.isOurs(player.dimension)) {
                    player.closeScreen();
                    SubworldTeleport.leave(player);
                }
                return;
            case 7:
                if (admin()) {
                    SubworldCreateMenu.open(player);
                }
                return;
            case 8:
                if (admin()) {
                    RegionMenu.openList(player, Region.PORTAL);
                }
                return;
            case 47:
                if (admin()) {
                    com.ecoserv.util.PlayerUtil.giveOrDrop(player, new ItemStack(ModRegistry.PORTAL_WAND));
                }
                return;
            case 45:
                if (page > 0) {
                    page--;
                }
                return;
            case 53:
                page++;
                return;
            case 49:
                player.closeScreen();
                return;
            default:
        }
        int index = page * PER_PAGE + slot - 9;
        if (slot >= 9 && slot < 45 && index >= 0 && index < shown.size()) {
            Click click = shown.get(index).click;
            if (click != null) {
                click.run(shift, right);
            }
        }
    }

    private void clickSubworld(Subworld sw, boolean shift, boolean right) {
        if (admin() && shift) {
            new SubworldEditMenu(player, sw.id, SubworldEditMenu.Page.MAIN).open();
            return;
        }
        if (admin() && right) {
            player.closeScreen();
            SubworldDim d = SubworldRegistry.find(sw.id, sw.perOwner() ? SubworldDim.TEMPLATE : SubworldDim.SHARED);
            if (d != null) {
                SubworldTeleport.teleportTo(player, d, false, null);
            }
            return;
        }
        SubworldAccess.Result access = SubworldAccess.check(player, sw);
        if (access.ok) {
            player.closeScreen();
            SubworldTeleport.enter(player, sw, false);
            return;
        }
        if (access.canBuy) {
            if (pending.equals("buy:" + sw.id) && confirmed(pendingUntil)) {
                pending = "";
                if (SubworldAccess.buy(player, sw)) {
                    ChatUtil.send(player, "&aДоступ в «" + sw.name + "&a» куплен!");
                } else if (!SubworldRegistry.isCurrent(sw)) {
                    ChatUtil.send(player, "&cЭтого подмира уже нет.");
                } else {
                    ChatUtil.send(player, "&cНе хватает денег: нужно &6" + Fmt.money(sw.price));
                }
                return;
            }
            pending = "buy:" + sw.id;
            pendingUntil = arm();
            return;
        }
        if (access.reason != null) {
            ChatUtil.send(player, access.reason);
        }
    }
}

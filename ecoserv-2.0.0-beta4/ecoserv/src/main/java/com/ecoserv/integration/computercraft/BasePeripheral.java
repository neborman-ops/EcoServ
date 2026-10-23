package com.ecoserv.integration.computercraft;

import com.ecoserv.auction.AuctionManager;
import com.ecoserv.auction.Listing;
import com.ecoserv.cc.CcKeys;
import com.ecoserv.cc.CcRequests;
import com.ecoserv.economy.EconomyManager;
import com.ecoserv.economy.Orders;
import com.ecoserv.economy.StatsStore;
import com.ecoserv.extra.ExtraConfig;
import com.ecoserv.extra.MoneyNote;
import com.ecoserv.minigame.Lobby;
import com.ecoserv.minigame.RaceManager;
import com.ecoserv.role.Role;
import com.ecoserv.role.RoleManager;
import com.ecoserv.server.LagReport;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.world.WorldNames;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Общее для периферий мода. Методы с действиями владельца (выдать товар, цена, выплата) работают только после
 * auth(секрет): секрет владелец задаёт командой /ecocc key, смотря на блок. Деньги игрока компьютер списывает
 * только через запрос с подтверждением в чате (requestPayment / requestBuy). Все вызовы — в главном потоке.
 */
public abstract class BasePeripheral implements IPeripheral {

    static final String[] COMMON = {
            "help", "getPos", "getOwner", "auth", "isAuthorized",
            "getBalance", "getOnline", "isNearby", "getTop", "getPrices", "getAuction", "getOrders", "getLobbies",
            "getPlaytime", "getRoles", "hasRole", "getTps", "getWorlds", "sendMessage",
            "requestPayment", "getRequest", "cancelRequest", "pay"
    };

    protected final TileEntity tile;
    private final String[] methods;

    protected BasePeripheral(TileEntity tile, String... own) {
        this.tile = tile;
        List<String> all = new ArrayList<String>(Arrays.asList(COMMON));
        all.addAll(Arrays.asList(own));
        this.methods = all.toArray(new String[0]);
    }

    @Nullable
    protected abstract UUID owner();

    protected abstract String ownerName();

    /** Действие своего типа (null — метода нет). */
    @Nullable
    protected abstract Object[] own(String method, Object[] args, IComputerAccess computer) throws LuaException;

    @Nonnull
    @Override
    public String[] getMethodNames() {
        return methods.clone();
    }

    @Nullable
    @Override
    public Object[] callMethod(@Nonnull final IComputerAccess computer, @Nonnull ILuaContext context, final int method,
                               @Nonnull final Object[] args) throws LuaException, InterruptedException {
        if (!ExtraConfig.ccEnabled) {
            throw new LuaException("EcoServ: ComputerCraft выключен в настройках мода");
        }
        if (method < 0 || method >= methods.length) {
            throw new LuaException("Нет такого метода");
        }
        final String name = methods[method];
        return context.executeMainThreadTask(() -> {
            if (tile.isInvalid() || tile.getWorld() == null) {
                throw new LuaException("Блок сломан");
            }
            try {
                Object[] r = common(name, args, computer);
                return r != null ? r : own(name, args, computer);
            } catch (IllegalStateException e) {
                throw new LuaException(e.getMessage());
            }
        });
    }

    protected String blockKey() {
        return CcKeys.blockKey(tile.getWorld().provider.getDimension(), tile.getPos());
    }

    protected void requireOwner(IComputerAccess computer) throws LuaException {
        if (!CcKeys.authorized(blockKey(), computer.getID())) {
            throw new LuaException(CcKeys.hasKey(blockKey()) ? "Нужен auth(ключ) владельца этого блока"
                    : "Владелец не задал ключ: /ecocc key <ключ>, смотря на блок");
        }
    }

    @Nullable
    private Object[] common(String name, Object[] args, final IComputerAccess computer) throws LuaException {
        switch (name) {
            case "help":
                return new Object[]{list(Arrays.asList(methods))};
            case "getPos": {
                Map<Object, Object> m = new HashMap<Object, Object>();
                m.put("x", tile.getPos().getX());
                m.put("y", tile.getPos().getY());
                m.put("z", tile.getPos().getZ());
                m.put("dim", tile.getWorld().provider.getDimension());
                return new Object[]{m};
            }
            case "getOwner":
                return new Object[]{ownerName()};
            case "auth":
                return new Object[]{CcKeys.auth(blockKey(), computer.getID(), string(args, 0))};
            case "isAuthorized":
                return new Object[]{CcKeys.authorized(blockKey(), computer.getID())};
            case "getBalance":
                return new Object[]{EconomyManager.get(uuid(args, 0))};
            case "getOnline": {
                List<Object> out = new ArrayList<Object>();
                for (EntityPlayerMP p : PlayerUtil.allOnline()) {
                    out.add(p.getName());
                }
                return new Object[]{list(out)};
            }
            case "isNearby": {
                EntityPlayerMP p = PlayerUtil.online(string(args, 0));
                return new Object[]{p != null && near(p)};
            }
            case "getTop": {
                int n = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 10;
                List<Object> out = new ArrayList<Object>();
                for (Map.Entry<UUID, Long> e : EconomyManager.top(Math.max(1, Math.min(100, n)))) {
                    Map<Object, Object> row = new HashMap<Object, Object>();
                    row.put("name", PlayerUtil.nameOf(e.getKey()));
                    row.put("balance", e.getValue());
                    out.add(row);
                }
                return new Object[]{list(out)};
            }
            case "getPrices": {
                ShopList shop = ShopList.forDim(tile.getWorld().provider.getDimension());
                List<Object> out = new ArrayList<Object>();
                for (ShopEntry e : shop.entries()) {
                    Map<Object, Object> row = new HashMap<Object, Object>();
                    row.put("item", e.key);
                    row.put("name", ChatUtil.strip(e.displayName()));
                    row.put("batch", e.batchSize());
                    row.put("price", e.getEffectiveSellPrice());
                    row.put("enabled", e.enabled && e.canSell());
                    out.add(row);
                }
                return new Object[]{list(out)};
            }
            case "getAuction": {
                List<Object> out = new ArrayList<Object>();
                for (Listing l : AuctionManager.all()) {
                    out.add(listing(l));
                }
                return new Object[]{list(out)};
            }
            case "getOrders": {
                List<Object> out = new ArrayList<Object>();
                for (Orders.Order o : Orders.open()) {
                    Map<Object, Object> row = new HashMap<Object, Object>();
                    row.put("id", o.id);
                    row.put("item", o.key);
                    row.put("name", o.itemName);
                    row.put("left", o.left());
                    row.put("price", o.price);
                    row.put("owner", o.ownerName);
                    out.add(row);
                }
                return new Object[]{list(out)};
            }
            case "getLobbies": {
                List<Object> out = new ArrayList<Object>();
                for (Lobby l : RaceManager.lobbies()) {
                    Map<Object, Object> row = new HashMap<Object, Object>();
                    row.put("id", l.id);
                    row.put("name", ChatUtil.strip(ChatUtil.color(l.name)));
                    row.put("mode", l.mode.name().toLowerCase());
                    row.put("difficulty", l.effectiveDifficulty());
                    row.put("players", l.members.size());
                    row.put("maxPlayers", l.maxPlayers);
                    row.put("password", l.hasPassword());
                    row.put("state", l.state.name().toLowerCase());
                    out.add(row);
                }
                return new Object[]{list(out)};
            }
            case "getPlaytime":
                return new Object[]{StatsStore.get(uuid(args, 0)).playtimeMillis / 3_600_000.0D};
            case "getRoles": {
                List<Object> out = new ArrayList<Object>();
                for (Role r : RoleManager.rolesOf(uuid(args, 0))) {
                    out.add(r.id);
                }
                return new Object[]{list(out)};
            }
            case "hasRole":
                return new Object[]{RoleManager.has(uuid(args, 0), string(args, 1))};
            case "getTps":
                return new Object[]{LagReport.tps()};
            case "getWorlds": {
                List<Object> out = new ArrayList<Object>();
                for (WorldServer w : DimensionManager.getWorlds()) {
                    Map<Object, Object> row = new HashMap<Object, Object>();
                    int dim = w.provider.getDimension();
                    row.put("dim", dim);
                    row.put("name", ChatUtil.strip(ChatUtil.color(WorldNames.displayName(dim))));
                    row.put("players", w.playerEntities.size());
                    out.add(row);
                }
                return new Object[]{list(out)};
            }
            case "sendMessage": {
                if (!ExtraConfig.ccAllowMessages) {
                    throw new LuaException("sendMessage выключен (computercraft.allow_messages=false)");
                }
                EntityPlayerMP p = PlayerUtil.online(string(args, 0));
                if (p == null || !near(p)) {
                    return new Object[]{false};
                }
                String text = string(args, 1);
                ChatUtil.send(p, "&b[Компьютер] &f" + (text.length() > 256 ? text.substring(0, 256) : text));
                return new Object[]{true};
            }
            case "requestPayment": {
                final EntityPlayerMP p = nearbyPlayer(args, 0);
                final long amount = number(args, 1);
                final String reason = args.length > 2 && args[2] instanceof String ? (String) args[2] : "";
                final UUID to = owner();
                if (amount <= 0) {
                    throw new LuaException("Сумма должна быть больше нуля");
                }
                if (to == null) {
                    throw new LuaException("У блока нет владельца — некому платить");
                }
                CcRequests.Request r = CcRequests.create(p, "ecoserv_payment",
                        "Оплатить &6" + Fmt.money(amount) + " &fигроку &e" + ownerName()
                                + (reason.isEmpty() ? "" : " &7(" + cut(reason) + ")") + "&f?",
                        player -> {
                            MoneyNote.set("Компьютер: " + (reason.isEmpty() ? "оплата" : cut(reason)));
                            boolean ok;
                            try {
                                ok = EconomyManager.take(player.getUniqueID(), amount);
                            } finally {
                                MoneyNote.clear();
                            }
                            if (!ok) {
                                throw new IllegalStateException("Не хватает денег: нужно " + Fmt.money(amount));
                            }
                            MoneyNote.set("Компьютер: оплата от " + player.getName());
                            try {
                                EconomyManager.give(to, amount);
                            } finally {
                                MoneyNote.clear();
                            }
                            ChatUtil.send(player, "&aОплачено: &6" + Fmt.money(amount));
                            return new Object[]{"paid", player.getName(), amount};
                        }, notifier(computer));
                return new Object[]{r.id};
            }
            case "getRequest": {
                CcRequests.Request r = CcRequests.get((int) number(args, 0));
                return new Object[]{r == null ? "unknown" : r.status};
            }
            case "cancelRequest":
                CcRequests.cancel((int) number(args, 0));
                return new Object[]{true};
            case "pay": {
                requireOwner(computer);
                UUID from = owner();
                UUID to = uuid(args, 0);
                long amount = number(args, 1);
                if (from == null || amount <= 0 || amount > ExtraConfig.ccPayMax) {
                    throw new LuaException("Нельзя: сумма от 1 до " + ExtraConfig.ccPayMax);
                }
                long got = EconomyManager.transfer(from, to, amount);
                if (got < 0) {
                    return new Object[]{false, "у владельца не хватает денег"};
                }
                EntityPlayerMP p = PlayerUtil.online(to);
                if (p != null) {
                    ChatUtil.send(p, "&aКомпьютер " + ownerName() + " выплатил тебе &6" + Fmt.money(got));
                }
                return new Object[]{true, got};
            }
            default:
                return null;
        }
    }

    // --- помощники --------------------------------------------------------------------------------------

    /** Событие компьютеру; к имени события добавляется имя периферии (кто прислал). */
    protected static CcRequests.Notifier notifier(final IComputerAccess computer) {
        return (event, args) -> {
            Object[] full = Arrays.copyOf(args, args.length + 1);
            full[args.length] = computer.getAttachmentName();
            computer.queueEvent(event, full);
        };
    }

    protected boolean near(EntityPlayerMP p) {
        return p.dimension == tile.getWorld().provider.getDimension()
                && p.getDistanceSq(tile.getPos()) <= (double) ExtraConfig.ccDistance * ExtraConfig.ccDistance;
    }

    protected EntityPlayerMP nearbyPlayer(Object[] args, int i) throws LuaException {
        EntityPlayerMP p = PlayerUtil.online(string(args, i));
        if (p == null) {
            throw new LuaException("Игрок не в сети");
        }
        if (!near(p)) {
            throw new LuaException("Игрок дальше " + ExtraConfig.ccDistance + " блоков от блока");
        }
        return p;
    }

    protected static String cut(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    protected static Map<Object, Object> list(List<?> values) {
        Map<Object, Object> out = new HashMap<Object, Object>();
        for (int i = 0; i < values.size(); i++) {
            out.put(i + 1, values.get(i));
        }
        return out;
    }

    protected static Map<Object, Object> stack(ItemStack s) {
        Map<Object, Object> m = new HashMap<Object, Object>();
        m.put("name", s.getItem().getRegistryName() == null ? "?" : s.getItem().getRegistryName().toString());
        m.put("damage", s.getItemDamage());
        m.put("count", s.getCount());
        m.put("displayName", ChatUtil.strip(s.getDisplayName()));
        m.put("nbt", ItemCodec.encode(s));
        return m;
    }

    protected static Map<Object, Object> listing(Listing l) {
        Map<Object, Object> row = new HashMap<Object, Object>();
        row.put("id", l.id);
        row.put("name", ChatUtil.strip(l.displayName()));
        row.put("count", l.count);
        row.put("price", l.pricePerItem);
        row.put("seller", l.sellerName);
        return row;
    }

    /** Инвентарь соседа со стороны (up, down, north, south, west, east). */
    protected IItemHandler neighbour(String side) throws LuaException {
        EnumFacing f = EnumFacing.byName(side.toLowerCase());
        if (f == null) {
            throw new LuaException("Сторона: up, down, north, south, west, east");
        }
        TileEntity te = tile.getWorld().getTileEntity(tile.getPos().offset(f));
        if (te == null || !te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) {
            throw new LuaException("С этой стороны нет инвентаря");
        }
        return (IItemHandler) te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
    }

    protected static String string(Object[] args, int i) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String)) {
            throw new LuaException("Аргумент " + (i + 1) + ": нужна строка");
        }
        return (String) args[i];
    }

    protected static long number(Object[] args, int i) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Аргумент " + (i + 1) + ": нужно число");
        }
        return (long) Math.floor(((Number) args[i]).doubleValue());
    }

    protected static UUID uuid(Object[] args, int i) throws LuaException {
        String name = string(args, i);
        UUID id = PlayerUtil.uuidOf(name);
        if (id == null) {
            throw new LuaException("Игрок не найден: " + name);
        }
        return id;
    }

    @Override
    public void attach(@Nonnull IComputerAccess computer) {
    }

    @Override
    public void detach(@Nonnull IComputerAccess computer) {
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other != null && other.getClass() == getClass() && ((BasePeripheral) other).tile == tile;
    }
}

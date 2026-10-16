package com.ecoserv.command;

import com.ecoserv.network.PacketHandler;
import com.ecoserv.shop.ShopEntry;
import com.ecoserv.shop.ShopList;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.Fmt;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** /shop — открыть магазин и быстро править прайс с предметом в руке. */
public class CommandShop extends CommandBase {

    @Override
    public String getName() {
        return "shop";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/shop | /shop price <приём, шт> <цена за приём> [категория] | /shop remove | /shop list [фильтр]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            com.ecoserv.shop.ShopScreen.open(player, com.ecoserv.shop.ShopScreen.Tab.AUCTION);
            return;
        }

        String sub = args[0].toLowerCase();

        if ("list".equals(sub)) {
            String filter = args.length > 1 ? args[1].toLowerCase() : "";
            ShopList scoped = sender instanceof EntityPlayerMP ? ShopList.forPlayer((EntityPlayerMP) sender) : ShopList.get();
            List<ShopEntry> entries = scoped.entries();
            int shown = 0;
            ChatUtil.send(sender, "&6=== Прайс-лист" + (scoped.isGlobal() ? "" : " подмира " + scoped.scopeName())
                    + " (" + entries.size() + ") ===");
            for (int i = 0; i < entries.size() && shown < 20; i++) {
                ShopEntry e = entries.get(i);
                if (!filter.isEmpty() && !e.displayName().toLowerCase().contains(filter)
                        && !e.key.toLowerCase().contains(filter)) {
                    continue;
                }
                shown++;
                ChatUtil.sendRaw(sender, " &f" + e.displayName() + " &7| приём &f" + e.batchSize() + " шт"
                        + " &7-> &e" + Fmt.money(e.getEffectiveSellPrice())
                        + (e.enabled && e.canSell() ? "" : " &8(не принимается)"));
            }
            if (shown == 0) {
                ChatUtil.sendRaw(sender, " &7ничего не найдено");
            }
            return;
        }

        if (!Perms.isAdmin(sender) && !Perms.has(sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null,
                Perms.SHOP_EDIT)) {
            ChatUtil.send(sender, "&cНужны права администратора.");
            return;
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ItemStack held = player.getHeldItemMainhand();
        // в подмире со своими квестами правится его прайс
        ShopList shop = ShopList.forPlayer(player);

        if ("price".equals(sub)) {
            if (held.isEmpty()) {
                ChatUtil.send(sender, "&cВозьми предмет в руку.");
                return;
            }
            // /shop price <цена>            -> приём = сколько держишь в руке
            // /shop price <шт> <цена> [кат] -> приём = <шт>
            if (args.length < 2) {
                ChatUtil.send(sender, "&cИспользование: &f/shop price <приём, шт> <цена за приём> [категория]");
                ChatUtil.send(sender, "&7Пример: держишь висмут, &f/shop price 64 58000 &7— за 64 шт платим 58 000.");
                return;
            }
            int batch;
            double price;
            try {
                if (args.length == 2) {
                    batch = held.getCount();
                    price = parseMoney(args[1]);
                } else {
                    batch = Integer.parseInt(args[1].trim());
                    price = parseMoney(args[2]);
                }
            } catch (NumberFormatException e) {
                ChatUtil.send(sender, "&cНекорректные числа. Пример: &f/shop price 64 58000");
                return;
            }
            int maxBatch = com.ecoserv.tile.TileMarket.SIZE * Math.max(1, held.getMaxStackSize());
            if (batch < 1 || batch > maxBatch) {
                ChatUtil.send(sender, "&cПриём должен быть от 1 до " + maxBatch + " шт (столько влезает в терминал).");
                return;
            }
            if (price < 0.0D || Double.isNaN(price) || Double.isInfinite(price)) {
                ChatUtil.send(sender, "&cЦена не может быть отрицательной.");
                return;
            }
            ShopEntry entry = shop.putBatch(held, batch, price);
            if (args.length > 3) {
                entry.category = args[3];
            }
            shop.save();
            PacketHandler.sendShopListToScope(shop);
            ChatUtil.send(sender, "&aПрайс обновлён" + (shop.isGlobal() ? "" : " (подмир " + shop.scopeName() + ")")
                    + ": &f" + held.getDisplayName() + " &7(" + entry.key + ")"
                    + " &a— приём &f" + batch + " шт &a-> &e" + Fmt.money(price));
            return;
        }

        if ("remove".equals(sub)) {
            if (held.isEmpty()) {
                ChatUtil.send(sender, "&cВозьми предмет в руку.");
                return;
            }
            String key = ItemKey.of(held, false, true).serialize();
            if (shop.remove(key)) {
                shop.save();
                PacketHandler.sendShopListToScope(shop);
                ChatUtil.send(sender, "&aУдалено из прайса: &f" + held.getDisplayName());
            } else {
                ChatUtil.send(sender, "&cЭтого предмета нет в прайсе (" + key + ").");
            }
            return;
        }

        if ("reload".equals(sub)) {
            ShopList.get().load();
            ShopList.reloadScopes();
            PacketHandler.sendShopListToAll();
            ChatUtil.send(sender, "&aПрайс-лист перезагружен: &f" + ShopList.get().size() + " позиций");
            return;
        }

        ChatUtil.send(sender, "&cИспользование: &f" + getUsage(sender));
    }

    /** 58000, 58 000, 58k, 58к, 1.5m — как в GUI. */
    private static double parseMoney(String text) {
        String s = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace(',', '.');
        double multiplier = 1.0D;
        if (s.endsWith("k") || s.endsWith("к")) {
            multiplier = 1_000.0D;
        } else if (s.endsWith("m") || s.endsWith("м")) {
            multiplier = 1_000_000.0D;
        }
        if (multiplier > 1.0D) {
            s = s.substring(0, s.length() - 1);
        }
        return Double.parseDouble(s) * multiplier;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "price", "remove", "list", "reload");
        }
        return new ArrayList<String>();
    }
}

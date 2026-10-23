package com.ecoserv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * UI texts. First the translation from assets/ecoserv/lang/*.lang is used;
 * if it did not load, the embedded copy below is used, so raw keys like
 * ecoserv.gui.title never show up in the GUI.
 * Generated from the .lang files - edit those, not this class.
 */
@SideOnly(Side.CLIENT)
public final class Lang {

    private static final Map<String, String> RU = new HashMap<String, String>();
    private static final Map<String, String> EN = new HashMap<String, String>();

    private Lang() {
    }

    static {
        put("itemGroup.ecoserv", "EcoServ", "EcoServ");
        put("tile.ecoserv.market.name", "\u0422\u043e\u0440\u0433\u043e\u0432\u044b\u0439 \u0442\u0435\u0440\u043c\u0438\u043d\u0430\u043b", "Market Terminal");
        put("ecoserv.gui.title", "\u041c\u0430\u0433\u0430\u0437\u0438\u043d \u0441\u0435\u0440\u0432\u0435\u0440\u0430", "Server Shop");
        put("ecoserv.gui.balance", "\u0411\u0430\u043b\u0430\u043d\u0441", "Balance");
        put("ecoserv.gui.ok", "\u041e\u041a", "OK");
        put("ecoserv.gui.refresh", "\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c", "Refresh");
        put("ecoserv.gui.search", "\u041f\u043e\u0438\u0441\u043a...", "Search...");
        put("ecoserv.gui.no_items", "\u041d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e", "Nothing found");
        put("ecoserv.gui.sell_short", "\u041f\u0440\u0438\u0451\u043c", "Batch");
        put("ecoserv.gui.buy_short", "\u0426\u0435\u043d\u0430", "Price");
        put("ecoserv.gui.sell_price", "\u041f\u0440\u0438\u0451\u043c, \u0448\u0442", "Batch, pcs");
        put("ecoserv.gui.buy_price", "\u0426\u0435\u043d\u0430 \u0437\u0430 \u043f\u0440\u0438\u0451\u043c", "Price per batch");
        put("ecoserv.gui.category", "\u041a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u044f", "Category");
        put("ecoserv.gui.batch", "\u041f\u0440\u0438\u0451\u043c, \u0448\u0442", "Batch, pcs");
        put("ecoserv.gui.batch_price", "\u0426\u0435\u043d\u0430 \u0437\u0430 \u043f\u0440\u0438\u0451\u043c", "Batch price");
        put("ecoserv.gui.price_hint_give", "\u0421\u0434\u0430\u044e\u0442", "Gives");
        put("ecoserv.gui.price_hint_get", "\u043f\u043e\u043b\u0443\u0447\u0430\u044e\u0442", "gets");
        put("ecoserv.gui.price_hint_unit", "\u0437\u0430 1 \u0448\u0442", "per 1 pc");
        put("ecoserv.gui.price_hint_rule", "\u043c\u0435\u043d\u044c\u0448\u0435 \u043f\u0440\u0438\u0451\u043c\u0430 \u043d\u0435 \u043f\u0440\u043e\u0434\u0430\u0451\u0442\u0441\u044f", "less than a batch is not sold");
        put("ecoserv.gui.price_hint_explain", "\u041f\u0440\u0438\u0451\u043c = \u0448\u0442\u0443\u043a \u0437\u0430 \u0440\u0430\u0437, \u0446\u0435\u043d\u0430 = \u0437\u0430 \u043e\u0434\u0438\u043d \u043f\u0440\u0438\u0451\u043c", "Batch = items per sale, price = per batch");
        put("ecoserv.gui.price_hint_select", "\u0412\u044b\u0431\u0435\u0440\u0438 \u0441\u0442\u0440\u043e\u043a\u0443 \u0438\u043b\u0438 \u043d\u0430\u0436\u043c\u0438 \u00ab\u0418\u0437 \u0440\u0443\u043a\u0438\u00bb", "Pick a row or press \"From hand\"");
        put("ecoserv.gui.price_hint_enter", "\u0412\u0432\u0435\u0434\u0438 \u0446\u0435\u043d\u0443 \u0437\u0430 \u043f\u0440\u0438\u0451\u043c \u0438 \u043d\u0430\u0436\u043c\u0438 Enter", "Enter the batch price and press Enter");
        put("ecoserv.gui.price_new", "\u041d\u043e\u0432\u0430\u044f", "New");
        put("ecoserv.gui.price_off", "\u043d\u0435 \u043f\u0440\u0438\u043d\u0438\u043c\u0430\u0435\u0442\u0441\u044f (\u0446\u0435\u043d\u0430 0)", "not accepted (price 0)");
        put("ecoserv.gui.until_batch", "\u0414\u043e \u043f\u0440\u0438\u0451\u043c\u0430", "Until batch");
        put("ecoserv.gui.waiting_short", "\u0436\u0434\u0443\u0442", "waiting");
        put("ecoserv.gui.market_wait_hint", "\u041c\u0435\u043d\u044c\u0448\u0435 \u043f\u0440\u0438\u0451\u043c\u0430 \u043d\u0435 \u043f\u0440\u043e\u0434\u0430\u0451\u0442\u0441\u044f \u2014 \u0436\u0434\u0451\u0442 \u0432 \u0431\u043b\u043e\u043a\u0435", "Less than a batch is not sold - it waits in the block");
        put("ecoserv.unit.pcs", "\u0448\u0442", "pcs");
        put("ecoserv.tooltip.buyer", "\u0421\u043a\u0443\u043f\u043a\u0430", "Buyer");
        put("ecoserv.gui.enabled", "\u0412\u043a\u043b\u044e\u0447\u0435\u043d\u043e", "Enabled");
        put("ecoserv.gui.disabled", "\u0412\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e", "Disabled");
        put("ecoserv.gui.close", "\u0417\u0430\u043a\u0440\u044b\u0442\u044c", "Close");
        put("ecoserv.gui.tab.shop", "\u041c\u0430\u0433\u0430\u0437\u0438\u043d", "Shop");
        put("ecoserv.gui.tab.profile", "\u041f\u0440\u043e\u0444\u0438\u043b\u044c", "Profile");
        put("ecoserv.gui.tab.players", "\u0418\u0433\u0440\u043e\u043a\u0438", "Players");
        put("ecoserv.gui.tab.prices", "\u0426\u0435\u043d\u044b", "Prices");
        put("ecoserv.gui.tab.server", "\u0421\u0435\u0440\u0432\u0435\u0440", "Server");
        put("ecoserv.gui.buy1", "\u041a\u0443\u043f\u0438\u0442\u044c 1", "Buy 1");
        put("ecoserv.gui.buy16", "\u041a\u0443\u043f\u0438\u0442\u044c 16", "Buy 16");
        put("ecoserv.gui.buy_stack", "\u041a\u0443\u043f\u0438\u0442\u044c \u0441\u0442\u0430\u043a", "Buy stack");
        put("ecoserv.gui.save", "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c", "Save");
        put("ecoserv.gui.delete", "\u0423\u0434\u0430\u043b\u0438\u0442\u044c", "Delete");
        put("ecoserv.gui.from_hand", "\u0418\u0437 \u0440\u0443\u043a\u0438", "From hand");
        put("ecoserv.gui.sort", "\u0421\u043e\u0440\u0442.", "Sort");
        put("ecoserv.gui.daily", "\u0415\u0436\u0435\u0434\u043d\u0435\u0432\u043d\u0430\u044f \u043d\u0430\u0433\u0440\u0430\u0434\u0430", "Daily reward");
        put("ecoserv.gui.daily_ready", "\u0415\u0436\u0435\u0434\u043d\u0435\u0432\u043d\u0430\u044f \u043d\u0430\u0433\u0440\u0430\u0434\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430!", "Daily reward available!");
        put("ecoserv.gui.daily_done", "\u041d\u0430\u0433\u0440\u0430\u0434\u0430 \u0441\u0435\u0433\u043e\u0434\u043d\u044f \u0443\u0436\u0435 \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0430", "Daily reward already claimed");
        put("ecoserv.gui.pending", "\u041e\u0436\u0438\u0434\u0430\u0435\u0442 \u0432\u044b\u0434\u0430\u0447\u0438", "Pending payout");
        put("ecoserv.gui.playtime", "\u041d\u0430\u0438\u0433\u0440\u0430\u043d\u043e", "Playtime");
        put("ecoserv.gui.streak", "\u0421\u0435\u0440\u0438\u044f \u0432\u0445\u043e\u0434\u043e\u0432", "Login streak");
        put("ecoserv.gui.total_earned", "\u0412\u0441\u0435\u0433\u043e \u0437\u0430\u0440\u0430\u0431\u043e\u0442\u0430\u043d\u043e", "Total earned");
        put("ecoserv.gui.total_spent", "\u0412\u0441\u0435\u0433\u043e \u043f\u043e\u0442\u0440\u0430\u0447\u0435\u043d\u043e", "Total spent");
        put("ecoserv.gui.hint_trade", "\u041e\u0431\u043c\u0435\u043d \u0441 \u0438\u0433\u0440\u043e\u043a\u043e\u043c: \u0432\u043a\u043b\u0430\u0434\u043a\u0430 \u00ab\u0418\u0433\u0440\u043e\u043a\u0438\u00bb \u0438\u043b\u0438 /trade <\u043d\u0438\u043a>", "Trade: the Players tab or /trade <name>");
        put("ecoserv.gui.hint_market", "\u0422\u0435\u0440\u043c\u0438\u043d\u0430\u043b \u043f\u043b\u0430\u0442\u0438\u0442 \u0434\u0430\u0436\u0435 \u043a\u043e\u0433\u0434\u0430 \u0442\u044b \u043e\u0444\u043b\u0430\u0439\u043d", "The terminal pays you even while offline");
        put("ecoserv.gui.server_hint", "\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u044f \u043f\u0440\u0438\u043c\u0435\u043d\u044f\u044e\u0442\u0441\u044f \u0441\u0440\u0430\u0437\u0443 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435", "Actions apply on the server immediately");
        put("ecoserv.gui.players.trade", "\u041e\u0431\u043c\u0435\u043d", "Trade");
        put("ecoserv.gui.players.pay", "\u041f\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438", "Send money");
        put("ecoserv.gui.players.amount", "\u0421\u0443\u043c\u043c\u0430", "Amount");
        put("ecoserv.gui.players.none", "\u041d\u0438\u043a\u043e\u0433\u043e \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435\u0442 \u043e\u043d\u043b\u0430\u0439\u043d", "Nobody else is online");
        put("ecoserv.gui.players.select", "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438 \u0438\u0433\u0440\u043e\u043a\u0430 \u0432 \u0441\u043f\u0438\u0441\u043a\u0435", "Select a player first");
        put("ecoserv.gui.players.hint", "\u0412\u044b\u0431\u0435\u0440\u0438 \u0438\u0433\u0440\u043e\u043a\u0430: \u00ab\u041e\u0431\u043c\u0435\u043d\u00bb - \u043e\u043a\u043d\u043e \u043e\u0431\u043c\u0435\u043d\u0430, \u0441\u0443\u043c\u043c\u0430 + \u00ab\u041f\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438\u00bb - \u0434\u0435\u043d\u044c\u0433\u0438", "Pick a player: Trade opens the window, amount + Send moves money");
        put("ecoserv.gui.players.online", "\u041e\u043d\u043b\u0430\u0439\u043d", "Online");
        put("ecoserv.gui.backup_now", "\u0411\u044d\u043a\u0430\u043f \u0441\u0435\u0439\u0447\u0430\u0441", "Backup now");
        put("ecoserv.gui.backup_prune", "\u0427\u0438\u0441\u0442\u043a\u0430 \u0441\u0442\u0430\u0440\u044b\u0445", "Prune old");
        put("ecoserv.gui.restart5", "\u0420\u0435\u0441\u0442\u0430\u0440\u0442 5 \u043c\u0438\u043d", "Restart in 5m");
        put("ecoserv.gui.restart15", "\u0420\u0435\u0441\u0442\u0430\u0440\u0442 15 \u043c\u0438\u043d", "Restart in 15m");
        put("ecoserv.gui.restart_cancel", "\u041e\u0442\u043c\u0435\u043d\u0438\u0442\u044c \u0440\u0435\u0441\u0442\u0430\u0440\u0442", "Cancel restart");
        put("ecoserv.gui.reload", "\u041f\u0435\u0440\u0435\u0447\u0438\u0442\u0430\u0442\u044c \u043a\u043e\u043d\u0444\u0438\u0433", "Reload config");
        put("ecoserv.gui.lag_report", "\u041e\u0442\u0447\u0451\u0442 \u043f\u043e \u043b\u0430\u0433\u0430\u043c", "Lag report");
        put("ecoserv.gui.save_data", "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0434\u0430\u043d\u043d\u044b\u0435", "Save data");
        put("ecoserv.gui.blacklist_add", "\u0412 \u0447\u0451\u0440\u043d\u044b\u0439 \u0441\u043f\u0438\u0441\u043e\u043a", "Blacklist held");
        put("ecoserv.gui.announce", "\u041e\u0431\u044a\u044f\u0432\u0438\u0442\u044c", "Announce");
        put("ecoserv.gui.set_tax", "\u0417\u0430\u0434\u0430\u0442\u044c \u043d\u0430\u043b\u043e\u0433", "Set tax");
        put("ecoserv.gui.tax", "\u041d\u0430\u043b\u043e\u0433 \u0441 \u043f\u0440\u043e\u0434\u0430\u0436, %", "Sales tax, %");
        put("ecoserv.gui.sell_speed", "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c \u0430\u0432\u0442\u043e\u043f\u0440\u043e\u0434\u0430\u0436\u0438, \u0442\u0438\u043a\u043e\u0432", "Auto-sell interval, ticks");
        put("ecoserv.gui.sell_now", "\u041f\u0440\u043e\u0434\u0430\u0442\u044c \u0441\u0435\u0439\u0447\u0430\u0441", "Sell now");
        put("ecoserv.gui.auto_on", "\u0410\u0432\u0442\u043e: \u0432\u043a\u043b", "Auto: on");
        put("ecoserv.gui.auto_off", "\u0410\u0432\u0442\u043e: \u0432\u044b\u043a\u043b", "Auto: off");
        put("ecoserv.gui.owner_me", "\u041f\u043b\u0430\u0442\u0438\u0442\u044c \u043c\u043d\u0435", "Pay me");
        put("ecoserv.gui.owner", "\u0412\u043b\u0430\u0434\u0435\u043b\u0435\u0446", "Owner");
        put("ecoserv.gui.no_owner", "\u043d\u0435 \u0437\u0430\u0434\u0430\u043d", "not set");
        put("ecoserv.gui.earned", "\u0417\u0430\u0440\u0430\u0431\u043e\u0442\u0430\u043d\u043e", "Earned");
        put("ecoserv.gui.contents_value", "\u0412\u044b\u0440\u0443\u0447\u043a\u0430", "Payout");
        put("ecoserv.gui.pick", "\u0421\u043f\u0438\u0441\u043e\u043a", "List");
        put("ecoserv.gui.pick_title", "\u0418\u0433\u0440\u043e\u043a\u0438 \u043e\u043d\u043b\u0430\u0439\u043d", "Players online");
        put("ecoserv.gui.trade.title", "\u041e\u0431\u043c\u0435\u043d", "Trade");
        put("ecoserv.gui.trade.you", "\u0422\u0432\u043e\u0451", "Yours");
        put("ecoserv.gui.trade.partner", "\u041f\u0430\u0440\u0442\u043d\u0451\u0440", "Partner");
        put("ecoserv.gui.trade.money", "\u0414\u0435\u043d\u044c\u0433\u0438 \u0432 \u0441\u0434\u0435\u043b\u043a\u0435", "Money in trade");
        put("ecoserv.gui.trade.confirm", "\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044c", "Confirm");
        put("ecoserv.gui.trade.unconfirm", "\u041e\u0442\u043e\u0437\u0432\u0430\u0442\u044c", "Withdraw");
        put("ecoserv.gui.trade.cancel", "\u041e\u0442\u043c\u0435\u043d\u0438\u0442\u044c", "Cancel");
        put("ecoserv.gui.trade.both_ready", "\u041e\u0431\u0430 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u043b\u0438, \u043c\u0435\u043d\u044f\u0435\u043c\u0441\u044f!", "Both confirmed, trading!");
        put("ecoserv.gui.trade.waiting_you", "\u041f\u0430\u0440\u0442\u043d\u0451\u0440 \u0436\u0434\u0451\u0442 \u0442\u0432\u043e\u0435\u0433\u043e \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u044f", "Partner waits for your confirmation");
        put("ecoserv.gui.trade.waiting_other", "\u0416\u0434\u0451\u043c \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u044f \u043f\u0430\u0440\u0442\u043d\u0451\u0440\u0430", "Waiting for the partner");
        put("ecoserv.gui.trade.hint", "\u041b\u044e\u0431\u043e\u0435 \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0435 \u0441\u0431\u0440\u0430\u0441\u044b\u0432\u0430\u0435\u0442 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u044f", "Any change resets confirmations");
        put("key.categories.ecoserv", "EcoServ", "EcoServ");
        put("key.ecoserv.shop", "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0430\u0433\u0430\u0437\u0438\u043d", "Open shop");
        put("ecoserv.gui.drag_hint", "Shift+\u0442\u0430\u0449\u0438, \u0447\u0442\u043e\u0431\u044b \u043f\u043e\u0434\u0432\u0438\u043d\u0443\u0442\u044c", "Shift+drag to move");
        put("tile.ecoserv.vendor.name", "\u0421\u043a\u043b\u0430\u0434 \u043f\u0440\u043e\u0434\u0430\u0432\u0446\u0430", "Vendor Warehouse");
        put("container.ecoserv.vendor", "\u0421\u043a\u043b\u0430\u0434 \u043f\u0440\u043e\u0434\u0430\u0432\u0446\u0430", "Vendor Warehouse");
        put("ecoserv.gui.tab.auction", "\u0410\u0443\u043a\u0446\u0438\u043e\u043d", "Auction");
        put("ecoserv.gui.auction.title", "\u0410\u0443\u043a\u0446\u0438\u043e\u043d \u0438\u0433\u0440\u043e\u043a\u043e\u0432", "Player auction");
        put("ecoserv.gui.auction.seller", "\u041f\u0440\u043e\u0434\u0430\u0432\u0435\u0446", "Seller");
        put("ecoserv.gui.auction.price", "\u0426\u0435\u043d\u0430/\u0448\u0442", "Price/ea");
        put("ecoserv.gui.auction.empty", "\u041b\u043e\u0442\u043e\u0432 \u043f\u043e\u043a\u0430 \u043d\u0435\u0442. \u041f\u043e\u0441\u0442\u0430\u0432\u044c \u0441\u043a\u043b\u0430\u0434 \u043f\u0440\u043e\u0434\u0430\u0432\u0446\u0430 \u0438 \u0432\u044b\u0441\u0442\u0430\u0432\u044c \u0442\u043e\u0432\u0430\u0440!", "No lots yet. Place a vendor warehouse and list your goods!");
        put("ecoserv.gui.auction.select", "\u0412\u044b\u0431\u0435\u0440\u0438 \u043b\u043e\u0442 \u0432 \u0441\u043f\u0438\u0441\u043a\u0435", "Select a lot from the list");
        put("ecoserv.gui.auction.total", "\u0437\u0430", "for");
        put("ecoserv.gui.auction.mine", "\u041c\u043e\u0438 \u043b\u043e\u0442\u044b", "My lots");
        put("ecoserv.gui.auction.all", "\u0412\u0441\u0435 \u043b\u043e\u0442\u044b", "All lots");
        put("ecoserv.gui.auction.cancel", "\u0421\u043d\u044f\u0442\u044c \u043b\u043e\u0442", "Cancel lot");
        put("ecoserv.gui.vendor.list", "\u0412\u044b\u0441\u0442\u0430\u0432\u0438\u0442\u044c \u043d\u0430 \u0430\u0443\u043a\u0446\u0438\u043e\u043d", "List on auction");
        put("ecoserv.gui.vendor.unlist", "\u0421\u043d\u044f\u0442\u044c \u0432\u0441\u0451", "Unlist all");
        put("ecoserv.gui.vendor.listed", "\u041b\u043e\u0442\u043e\u0432", "Lots");
        put("ecoserv.gui.vendor.price", "\u0421\u043b\u0435\u0432\u0430 - \u0446\u0435\u043d\u0430 \u0437\u0430 \u043e\u0434\u043d\u0443 \u0448\u0442\u0443\u043a\u0443", "Left field - price per single item");
        put("ecoserv.gui.vendor.back_hint", "\u0410\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u043a\u0430 \u043f\u043e\u0434\u0430\u0451\u0442 \u0442\u043e\u0432\u0430\u0440 \u0442\u043e\u043b\u044c\u043a\u043e \u0432 \u0437\u0430\u0434\u043d\u044e\u044e \u0433\u0440\u0430\u043d\u044c", "Automation may only insert into the back face");
        put("tile.ecoserv.vending.name", "\u0411\u043b\u043e\u043a \u043f\u0440\u043e\u0434\u0430\u0436\u0438", "Vending Block");
        put("container.ecoserv.vending", "\u0411\u043b\u043e\u043a \u043f\u0440\u043e\u0434\u0430\u0436\u0438", "Vending Block");
        put("key.ecoserv.trade", "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043e\u0431\u043c\u0435\u043d", "Open trade");
        put("ecoserv.gui.vending.sample", "\u0412\u0438\u0442\u0440\u0438\u043d\u0430: \u0447\u0442\u043e \u043f\u0440\u043e\u0434\u0430\u0451\u043c", "Showcase: what is sold");
        put("ecoserv.gui.vending.fields", "\u0446\u0435\u043d\u0430 | \u0448\u0442\u0443\u043a", "price | count");
        put("ecoserv.gui.vending.stock", "\u041e\u0441\u0442\u0430\u0442\u043e\u043a", "In stock");
        put("ecoserv.gui.vending.cash", "\u0412 \u043a\u0430\u0441\u0441\u0435", "Cash box");
        put("ecoserv.gui.vending.sold", "\u041f\u0440\u043e\u0434\u0430\u043d\u043e", "Sold");
        put("ecoserv.gui.vending.withdraw", "\u0417\u0430\u0431\u0440\u0430\u0442\u044c \u043a\u0430\u0441\u0441\u0443", "Take cash");
        put("ecoserv.gui.vending.admin", "\u0410\u0434\u043c\u0438\u043d\u0441\u043a\u0438\u0439 \u0430\u0432\u0442\u043e\u043c\u0430\u0442: \u0442\u043e\u0432\u0430\u0440 \u0431\u0435\u0441\u043a\u043e\u043d\u0435\u0447\u043d\u044b\u0439", "Admin machine: infinite stock");
        put("item.ecoserv.zone_wand_danger.name", "BLOCK DESTROY DANGER", "BLOCK DESTROY DANGER");
        put("item.ecoserv.zone_wand.name", "BLOCK DESTROY", "BLOCK DESTROY");
        put("item.ecoserv.activator_wand.name", "\u041f\u0430\u043b\u043a\u0430 \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438", "Activation Wand");
        put("ftbquests.task.ecoserv.activation", "\u0410\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u044f", "Activation");
        put("ecoserv.quest.activation.name", "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u043a\u043d\u043e\u043f\u043a\u0438 \u0438\u043b\u0438 \u0440\u044b\u0447\u0430\u0433\u0430", "Button or lever name");
        put("ecoserv.quest.activation.hint", "\u041d\u0430\u0436\u043c\u0438 \u043a\u043d\u043e\u043f\u043a\u0443 \u0438\u043b\u0438 \u0440\u044b\u0447\u0430\u0433 \u00ab%s\u00bb", "Press the button or lever \"%s\"");
        put("item.ecoserv.trigger_wand.name", "\u041f\u0430\u043b\u043e\u0447\u043a\u0430 \u0442\u0440\u0438\u0433\u0433\u0435\u0440\u0430", "Trigger Wand");
        put("item.ecoserv.portal_wand.name", "\u041f\u0430\u043b\u043e\u0447\u043a\u0430 \u043f\u043e\u0440\u0442\u0430\u043b\u043e\u0432", "Portal Wand");
        put("item.ecoserv.fill_wand.name", "\u041f\u0430\u043b\u043e\u0447\u043a\u0430 \u0437\u0430\u043f\u043e\u043b\u043d\u0435\u043d\u0438\u044f", "Fill Wand");
        put("tile.ecoserv.role_barrier.name", "\u0411\u0430\u0440\u044c\u0435\u0440 \u0440\u043e\u043b\u0435\u0439", "Role Barrier");
        put("tile.ecoserv.checkpoint.name", "\u0427\u0435\u043a\u043f\u043e\u0438\u043d\u0442", "Checkpoint");
        put("tile.ecoserv.loot_chest.name", "\u0421\u0443\u043d\u0434\u0443\u043a \u0434\u043e\u0431\u044b\u0447\u0438", "Loot Chest");
        put("tile.ecoserv.player_sensor.name", "\u0414\u0430\u0442\u0447\u0438\u043a \u0438\u0433\u0440\u043e\u043a\u0430", "Player Sensor");
        put("ftbquests.task.ecoserv.hidden", "\u0421\u043a\u0440\u044b\u0442\u043e\u0435 \u0434\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u0435", "Hidden Achievement");
        put("ftbquests.task.ecoserv.age", "\u0412\u043e\u0437\u0440\u0430\u0441\u0442", "Age");
        put("ftbquests.reward.ecoserv.role", "\u0420\u043e\u043b\u044c", "Role");
        put("ecoserv.quest.hidden.trigger", "\u041a\u0430\u043a \u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u0442\u0441\u044f", "Trigger");
        put("ecoserv.quest.hidden.trigger.activation", "\u0410\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u044f (\u043a\u043d\u043e\u043f\u043a\u0430, \u0437\u043e\u043d\u0430, \u0441\u0446\u0435\u043d\u0430\u0440\u0438\u0439, \u043a\u043e\u043c\u0430\u043d\u0434\u0430)", "Activation (button, zone, script, command)");
        put("ecoserv.quest.hidden.trigger.dimension", "\u0412\u0445\u043e\u0434 \u0432 \u043c\u0438\u0440", "Entering a world");
        put("ecoserv.quest.hidden.trigger.advancement", "\u0414\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u0435 Minecraft", "Minecraft advancement");
        put("ecoserv.quest.hidden.trigger.item", "\u041f\u0440\u0435\u0434\u043c\u0435\u0442 \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435", "Item in inventory");
        put("ecoserv.quest.hidden.value", "\u0417\u043d\u0430\u0447\u0435\u043d\u0438\u0435: \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438, \u043c\u0438\u0440, id \u0434\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u044f \u0438\u043b\u0438 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u0430", "Value: activation name, world, advancement or item id");
        put("ecoserv.quest.hidden.revealed", "\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u043f\u043e\u0441\u043b\u0435 \u043e\u0442\u043a\u0440\u044b\u0442\u0438\u044f", "Title after unlocking");
        put("ecoserv.quest.hidden.hint", "\u041f\u043e\u0434\u0441\u043a\u0430\u0437\u043a\u0430, \u043f\u043e\u043a\u0430 \u0441\u043a\u0440\u044b\u0442\u043e", "Hint while hidden");
        put("ecoserv.quest.hidden.done", "\u0421\u0435\u043a\u0440\u0435\u0442 \u043e\u0442\u043a\u0440\u044b\u0442", "Secret unlocked");
        put("ecoserv.quest.hidden.secret", "\u0421\u043a\u0440\u044b\u0442\u043e\u0435 \u0434\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u0435", "Hidden achievement");
        put("ecoserv.quest.age.filter", "\u0424\u0438\u043b\u044c\u0442\u0440 \u0432\u043e\u0437\u0440\u0430\u0441\u0442\u0430 (18, >=16, <=12, 14-17)", "Age filter (18, >=16, <=12, 14-17)");
        put("ecoserv.quest.age.hint", "\u0423\u043a\u0430\u0436\u0438 \u0434\u0435\u043d\u044c \u0440\u043e\u0436\u0434\u0435\u043d\u0438\u044f: /birthday", "Set your birthday: /birthday");
        put("ecoserv.quest.role.role", "\u0420\u043e\u043b\u044c (id)", "Role id");
        put("ecoserv.quest.role.days", "\u0414\u043d\u0435\u0439 (0 \u2014 \u043d\u0430\u0432\u0441\u0435\u0433\u0434\u0430)", "Days (0 = forever)");
        put("ecoserv.gui.tab.worlds", "\u041c\u0438\u0440\u044b \u0438 \u043f\u043e\u0434\u043c\u0438\u0440\u044b", "Worlds and sub-worlds");
        put("key.ecoserv.worlds", "\u041c\u0435\u043d\u044e \u043c\u0438\u0440\u043e\u0432", "Worlds menu");
        put("tile.ecoserv.glitch_chest.name", "\u0413\u041b\u0418\u0422\u0427-\u0441\u0443\u043d\u0434\u0443\u043a", "GLITCH Chest");
        put("item.ecoserv.glitch_select.name", "\u041f\u0430\u043b\u043a\u0430 GLITCH SELECT", "GLITCH SELECT Stick");
        put("ftbquests.reward.ecoserv.subworld_access", "\u0414\u043e\u0441\u0442\u0443\u043f \u043a \u043f\u043e\u0434\u043c\u0438\u0440\u0443", "Sub-world access");
        put("ftbquests.reward.ecoserv.subworld_expand", "\u0420\u0430\u0441\u0448\u0438\u0440\u0435\u043d\u0438\u0435 \u043c\u0438\u0440\u0430", "World expansion");
        put("ecoserv.quest.subworld.name", "\u041f\u043e\u0434\u043c\u0438\u0440", "Sub-world");
        put("ecoserv.quest.subworld.amount", "\u041d\u0430 \u0441\u043a\u043e\u043b\u044c\u043a\u043e \u0431\u043b\u043e\u043a\u043e\u0432 (0 \u2014 \u0448\u0430\u0433 \u0438\u0437 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a \u043f\u043e\u0434\u043c\u0438\u0440\u0430)", "Blocks to add (0 = sub-world step)");
        put("ecoserv.upload.title", "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0441\u0432\u043e\u0439 \u043c\u0438\u0440 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440", "Upload your world to the server");
        put("ecoserv.upload.hint", "\u041e\u0434\u0438\u043d\u043e\u0447\u043d\u044b\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f \u0438\u0437 1.12.2 \u0438 \u0441\u0442\u0430\u0440\u0448\u0435, \u0434\u043e", "Single-player saves from 1.12.2 or older, up to");
        put("ecoserv.upload.start", "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c", "Upload");
        put("ecoserv.upload.cancel", "\u041e\u0442\u043c\u0435\u043d\u0438\u0442\u044c", "Cancel");
        put("ecoserv.upload.done", "\u0413\u043e\u0442\u043e\u0432\u043e:", "Done:");
        put("ecoserv.upload.empty", "\u043d\u0435\u0442 \u0444\u0430\u0439\u043b\u043e\u0432 \u0440\u0435\u0433\u0438\u043e\u043d\u043e\u0432", "no region files");
        put("ecoserv.upload.connecting", "\u0421\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435 \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u043e\u043c...", "Contacting the server...");
        put("ecoserv.upload.no_connection", "\u041d\u0435\u0442 \u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u044f \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u043e\u043c", "No connection to the server");
        put("ecoserv.upload.no_answer", "\u0421\u0435\u0440\u0432\u0435\u0440 \u043d\u0435 \u043e\u0442\u0432\u0435\u0447\u0430\u0435\u0442", "The server does not answer");
        put("ecoserv.upload.sending", "\u041e\u0442\u043f\u0440\u0430\u0432\u043a\u0430...", "Sending...");
        put("ecoserv.upload.finishing", "\u0421\u0435\u0440\u0432\u0435\u0440 \u0441\u043e\u0445\u0440\u0430\u043d\u044f\u0435\u0442 \u043c\u0438\u0440...", "The server is saving the world...");
        put("ecoserv.upload.cancelled", "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430 \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430", "Upload cancelled");
        put("ecoserv.upload.too_new", "\u043c\u0438\u0440 \u043d\u043e\u0432\u0435\u0435 1.12.2", "world is newer than 1.12.2");
        put("ecoserv.upload.broken", "level.dat \u043f\u043e\u0432\u0440\u0435\u0436\u0434\u0451\u043d", "level.dat is broken");
        put("ecoserv.upload.too_big", "\u0431\u043e\u043b\u044c\u0448\u0435", "bigger than");
        put("ecoserv.upload.no_worlds", "\u0412 \u043f\u0430\u043f\u043a\u0435 saves \u043d\u0435\u0442 \u043c\u0438\u0440\u043e\u0432", "No worlds in the saves folder");
    }

    private static void put(String key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    /** Text by key, with fallback. */
    public static String get(String key) {
        try {
            if (I18n.hasKey(key)) {
                String translated = I18n.format(key);
                if (translated != null && !translated.equals(key) && !translated.isEmpty()) {
                    return translated;
                }
            }
        } catch (Throwable ignored) {
            // translator not ready yet - use embedded text
        }
        String value = (isRussian() ? RU : EN).get(key);
        if (value == null) {
            value = EN.get(key);
        }
        return value == null ? key : value;
    }

    public static boolean isRussian() {
        try {
            String language = Minecraft.getMinecraft().gameSettings.language;
            return language != null && language.toLowerCase().startsWith("ru");
        } catch (Throwable ignored) {
            return false;
        }
    }
}

package com.ecoserv.inventory;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.restrict.RestrictionService;
import com.ecoserv.restrict.Stash;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.NbtFiles;
import com.ecoserv.util.Perms;
import com.ecoserv.world.WorldNames;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.GameType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Раздельные инвентари по группам миров (п. 23): например, «мир 1» и «мир 2» делят один инвентарь,
 * «мир 5» и «мир 8» — другой, а остальные миры — общий инвентарь группы default.
 * <p>
 * Какая группа сейчас «живая» (её вещи у игрока), записано в собственных данных игрока
 * (PlayerPersisted) — они сохраняются вместе с инвентарём, поэтому при падении сервера
 * вещи не дублируются. Снимки остальных групп — в world/ecoserv/inventories/&lt;uuid&gt;.dat.
 */
public final class InventoryGroups {

    public static final String DEFAULT = "default";
    private static final String TAG = "ecoserv_invgroup";

    private static String[] parsedGroups;
    private static String[] parsedModes;
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<String, List<String>>();
    private static final Map<String, GameType> MODES = new LinkedHashMap<String, GameType>();

    private InventoryGroups() {
    }

    // --- группы из конфига ------------------------------------------------------

    private static void parse() {
        if (parsedGroups == EcoConfig.invGroups && parsedModes == EcoConfig.invGamemodes) {
            return;
        }
        parsedGroups = EcoConfig.invGroups;
        parsedModes = EcoConfig.invGamemodes;
        GROUPS.clear();
        MODES.clear();
        String[] lines = parsedGroups == null ? new String[0] : parsedGroups;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                if (!line.isEmpty()) {
                    EcoServ.LOG.warn("inventories.groups: строка без «имя:» — {}", line);
                }
                continue;
            }
            String name = WorldNames.normalize(line.substring(0, colon));
            List<String> worlds = GROUPS.get(name);
            if (worlds == null) {
                worlds = new ArrayList<String>();
                GROUPS.put(name, worlds);
            }
            for (String w : line.substring(colon + 1).split(",")) {
                if (!w.trim().isEmpty()) {
                    worlds.add(w.trim());
                }
            }
        }
        String[] modes = parsedModes == null ? new String[0] : parsedModes;
        for (String raw : modes) {
            String line = raw == null ? "" : raw.trim();
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            GameType type = parseMode(line.substring(eq + 1));
            if (type == null) {
                EcoServ.LOG.warn("inventories.gamemodes: неизвестный режим в строке '{}'", line);
                continue;
            }
            MODES.put(WorldNames.normalize(line.substring(0, eq)), type);
        }
    }

    @Nullable
    private static GameType parseMode(String text) {
        String s = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (s.equals("0") || s.startsWith("surv") || s.equals("выживание")) {
            return GameType.SURVIVAL;
        }
        if (s.equals("1") || s.startsWith("crea") || s.equals("креатив")) {
            return GameType.CREATIVE;
        }
        if (s.equals("2") || s.startsWith("adv") || s.equals("приключение")) {
            return GameType.ADVENTURE;
        }
        if (s.equals("3") || s.startsWith("spec") || s.equals("наблюдатель")) {
            return GameType.SPECTATOR;
        }
        return null;
    }

    /** Группа мира: первая группа, где мир перечислен, иначе default. */
    public static String groupOf(int dim) {
        parse();
        for (Map.Entry<String, List<String>> e : GROUPS.entrySet()) {
            if (WorldNames.matchesAny(dim, e.getValue())) {
                return e.getKey();
            }
        }
        return DEFAULT;
    }

    public static Map<String, List<String>> groups() {
        parse();
        return Collections.unmodifiableMap(GROUPS);
    }

    /** Группа, чьи вещи сейчас у игрока. */
    public static String liveGroup(EntityPlayer player) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        String g = persisted.getString(TAG);
        return g.isEmpty() ? DEFAULT : g;
    }

    private static void setLive(EntityPlayer player, String group) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setString(TAG, group);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static File file(UUID id) {
        return new File(DataStore.file("inventories"), id + ".dat");
    }

    // --- смена группы ---------------------------------------------------------

    /** Проверить, совпадает ли живая группа с группой текущего мира; если нет — поменять инвентарь. */
    public static void check(EntityPlayerMP player) {
        if (!EcoConfig.invEnabled || player == null || player.connection == null) {
            return;
        }
        String live = liveGroup(player);
        String target = groupOf(player.dimension);
        if (live.equals(target)) {
            return;
        }
        swap(player, live, target);
    }

    private static void swap(EntityPlayerMP player, String from, String to) {
        UUID id = player.getUniqueID();
        player.closeScreen();
        List<ItemStack> loose = takeLoose(player);

        File file = file(id);
        NBTTagCompound root = NbtFiles.read(file);
        if (root == null) {
            root = new NBTTagCompound();
        }
        NBTTagCompound all = root.getCompoundTag("groups");
        all.setTag(from, capture(player, loose));
        root.setTag("groups", all);
        root.setString("live", to);
        // сначала надёжно записываем снимок, и только потом забираем вещи у игрока
        if (!NbtFiles.write(file, root)) {
            // не удалось записать — вещи остаются у игрока, группа не меняется
            for (ItemStack stack : loose) {
                giveBack(player, stack);
            }
            ChatUtil.send(player, "&cНе удалось сохранить инвентарь группы — смена инвентаря отменена. Сообщи админу.");
            EcoServ.LOG.error("Раздельные инвентари: не записан {}, группа игрока {} не сменена ({} -> {})",
                    file, player.getName(), from, to);
            return;
        }
        clear(player);
        NBTTagCompound target = all.getCompoundTag(to);
        apply(player, target, to);
        setLive(player, to);
        sync(player);
        if (EcoConfig.invNotify) {
            ChatUtil.actionBar(player, "&7Инвентарь группы миров &f" + to);
        }
    }

    /** Предмет на курсоре и в сетке крафта 2×2 — тоже часть инвентаря группы. */
    private static List<ItemStack> takeLoose(EntityPlayerMP player) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        ItemStack cursor = player.inventory.getItemStack();
        if (!cursor.isEmpty()) {
            player.inventory.setItemStack(ItemStack.EMPTY);
            out.add(cursor);
        }
        if (player.inventoryContainer instanceof ContainerPlayer) {
            InventoryCrafting grid = ((ContainerPlayer) player.inventoryContainer).craftMatrix;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                ItemStack stack = grid.removeStackFromSlot(i);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    private static NBTTagCompound capture(EntityPlayerMP player, List<ItemStack> loose) {
        NBTTagCompound snap = new NBTTagCompound();
        snap.setTag("inv", player.inventory.writeToNBT(new NBTTagList()));
        NBTTagList extra = new NBTTagList();
        for (ItemStack stack : loose) {
            extra.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        // буфер запретов тоже у каждой группы свой — иначе вещи «перетекали» бы между группами
        for (ItemStack stack : Stash.peek(player.getUniqueID())) {
            if (!stack.isEmpty()) {
                extra.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        snap.setTag("extra", extra);
        if (EcoConfig.invSeparateEnderChest) {
            snap.setTag("ender", player.getInventoryEnderChest().saveInventoryToNBT());
        }
        if (EcoConfig.invSeparateXp) {
            snap.setInteger("xpLevel", player.experienceLevel);
            snap.setFloat("xpBar", player.experience);
            snap.setInteger("xpTotal", player.experienceTotal);
        }
        if (EcoConfig.invSeparateHealth) {
            snap.setFloat("health", player.getHealth());
            NBTTagCompound food = new NBTTagCompound();
            player.getFoodStats().writeNBT(food);
            snap.setTag("food", food);
        }
        if (EcoConfig.invSeparateEffects) {
            NBTTagList effects = new NBTTagList();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                effects.appendTag(effect.writeCustomPotionEffectToNBT(new NBTTagCompound()));
            }
            snap.setTag("effects", effects);
        }
        snap.setInteger("gamemode", player.interactionManager.getGameType().getID());
        snap.setLong("saved", System.currentTimeMillis());
        return snap;
    }

    private static void clear(EntityPlayerMP player) {
        player.inventory.clear();
        Stash.clear(player.getUniqueID());
        if (EcoConfig.invSeparateEnderChest) {
            player.getInventoryEnderChest().clear();
        }
        if (EcoConfig.invSeparateXp) {
            player.experienceLevel = 0;
            player.experience = 0.0F;
            player.experienceTotal = 0;
        }
        if (EcoConfig.invSeparateEffects) {
            player.clearActivePotions();
        }
    }

    private static void apply(EntityPlayerMP player, NBTTagCompound snap, String group) {
        if (snap.hasKey("inv")) {
            player.inventory.readFromNBT(snap.getTagList("inv", 10));
        }
        NBTTagList extra = snap.getTagList("extra", 10);
        for (int i = 0; i < extra.tagCount(); i++) {
            ItemStack stack = new ItemStack(extra.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                giveBack(player, stack);
            }
        }
        if (EcoConfig.invSeparateEnderChest && snap.hasKey("ender")) {
            player.getInventoryEnderChest().loadInventoryFromNBT(snap.getTagList("ender", 10));
        }
        if (EcoConfig.invSeparateXp && snap.hasKey("xpLevel")) {
            player.experienceLevel = snap.getInteger("xpLevel");
            player.experience = snap.getFloat("xpBar");
            player.experienceTotal = snap.getInteger("xpTotal");
        }
        if (EcoConfig.invSeparateHealth && snap.hasKey("health")) {
            player.setHealth(Math.max(1.0F, snap.getFloat("health")));
            player.getFoodStats().readNBT(snap.getCompoundTag("food"));
        }
        if (EcoConfig.invSeparateEffects && snap.hasKey("effects")) {
            NBTTagList effects = snap.getTagList("effects", 10);
            for (int i = 0; i < effects.tagCount(); i++) {
                PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(effects.getCompoundTagAt(i));
                if (effect != null) {
                    player.addPotionEffect(effect);
                }
            }
        }
        applyGamemode(player, snap, group);
    }

    private static void applyGamemode(EntityPlayerMP player, NBTTagCompound snap, String group) {
        if (EcoConfig.invAdminsKeepGamemode && Perms.isAdmin(player)) {
            return;
        }
        GameType forced = MODES.get(group);
        GameType target = forced;
        if (target == null && snap.hasKey("gamemode")) {
            target = GameType.getByID(snap.getInteger("gamemode"));
        }
        if (target != null && target != GameType.NOT_SET && player.interactionManager.getGameType() != target) {
            player.setGameType(target);
        }
    }

    /** В инвентарь, а что не влезло — в буфер (/stash), чтобы ничего не пропало. */
    private static void giveBack(EntityPlayerMP player, ItemStack stack) {
        RestrictionService.insert(player.inventory, stack);
        if (!stack.isEmpty()) {
            Stash.add(player.getUniqueID(), stack);
        }
    }

    private static void sync(EntityPlayerMP player) {
        player.inventoryContainer.detectAndSendChanges();
        player.sendContainerToPlayer(player.inventoryContainer);
        player.connection.sendPacket(new SPacketSetExperience(player.experience, player.experienceTotal,
                player.experienceLevel));
        player.connection.sendPacket(new SPacketUpdateHealth(player.getHealth(), player.getFoodStats().getFoodLevel(),
                player.getFoodStats().getSaturationLevel()));
    }

    /** Для админа: какие группы сохранены у игрока. */
    public static List<String> storedGroups(UUID id) {
        List<String> out = new ArrayList<String>();
        NBTTagCompound root = NbtFiles.read(file(id));
        if (root != null) {
            out.addAll(root.getCompoundTag("groups").getKeySet());
        }
        Collections.sort(out);
        return out;
    }
}

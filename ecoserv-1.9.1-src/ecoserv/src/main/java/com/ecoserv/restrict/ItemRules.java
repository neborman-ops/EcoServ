package com.ecoserv.restrict;

import com.ecoserv.EcoServ;
import com.ecoserv.config.EcoConfig;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Все частичные запреты предметов. Файл: config/ecoserv/restrictions.json */
public final class ItemRules {

    private static final Type LIST_TYPE = new TypeToken<ArrayList<ItemRule>>() {
    }.getType();

    private static final List<ItemRule> RULES = new ArrayList<ItemRule>();
    /** id предмета -> правила для него (быстрый поиск на каждое событие). */
    private static final Map<String, List<ItemRule>> BY_ID = new HashMap<String, List<ItemRule>>();
    /** modid -> правила «@modid» (выключенные моды). */
    private static final Map<String, List<ItemRule>> BY_MOD = new HashMap<String, List<ItemRule>>();
    private static boolean anyCarry;
    private static boolean anyCraft;
    private static boolean anyModUse;

    private ItemRules() {
    }

    private static File file() {
        return new File(EcoConfig.getConfigDir(), "restrictions.json");
    }

    public static void load() {
        RULES.clear();
        File file = file();
        if (!file.exists()) {
            JsonUtil.write(file, new ArrayList<ItemRule>());
        }
        ArrayList<ItemRule> loaded = JsonUtil.readType(file, LIST_TYPE, new ArrayList<ItemRule>());
        for (int i = 0; i < loaded.size(); i++) {
            ItemRule rule = loaded.get(i);
            if (rule != null && rule.item != null && !rule.item.trim().isEmpty()) {
                // "tnt" -> "minecraft:tnt@-1": один вид ключа для поиска и редактора
                rule.item = normalize(rule.item);
                RULES.add(rule);
            }
        }
        reindex();
        if (!RULES.isEmpty()) {
            EcoServ.LOG.info("Частичные запреты предметов: {} правил", RULES.size());
        }
    }

    public static void save() {
        JsonUtil.write(file(), RULES);
    }

    /** Пересобрать индекс (после загрузки, правки или смены списка миров). */
    public static void reindex() {
        BY_ID.clear();
        BY_MOD.clear();
        anyCarry = false;
        anyCraft = false;
        anyModUse = false;
        for (int i = 0; i < RULES.size(); i++) {
            ItemRule rule = RULES.get(i);
            rule.compile();
            Map<String, List<ItemRule>> index = rule.isModRule() ? BY_MOD : BY_ID;
            String indexKey = rule.isModRule() ? rule.modId() : rule.key().id;
            List<ItemRule> list = index.get(indexKey);
            if (list == null) {
                list = new ArrayList<ItemRule>();
                index.put(indexKey, list);
            }
            list.add(rule);
            anyCarry |= rule.denies(RestrictAction.CARRY);
            anyCraft |= rule.denies(RestrictAction.CRAFT);
            anyModUse |= rule.isModRule() && rule.denies(RestrictAction.USE);
        }
    }

    public static List<ItemRule> all() {
        return Collections.unmodifiableList(RULES);
    }

    public static String normalize(String key) {
        String k = key == null ? "" : key.trim();
        if (k.startsWith("@")) {
            return "@" + k.substring(1).trim().toLowerCase(java.util.Locale.ROOT);
        }
        return ItemKey.parse(k).serialize();
    }

    /** Ключ правила для всего мода. */
    public static String modKey(String modId) {
        return normalize("@" + modId);
    }

    /** Правило ровно для этого ключа (для редактора). */
    @Nullable
    public static ItemRule exact(String key) {
        String norm = normalize(key);
        for (int i = 0; i < RULES.size(); i++) {
            if (RULES.get(i).item.equals(norm)) {
                return RULES.get(i);
            }
        }
        return null;
    }

    /**
     * Уже существующее правило, под которое подходит предмет из руки
     * (например, записанное вручную как «любой meta»). Сначала — точное совпадение ключа.
     */
    @Nullable
    public static ItemRule findFor(ItemStack stack, String preferredKey) {
        ItemRule exact = exact(preferredKey);
        if (exact != null) {
            return exact;
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (int i = 0; i < RULES.size(); i++) {
            // правило на весь мод здесь не подходит: из руки редактируется конкретный предмет
            if (!RULES.get(i).isModRule() && RULES.get(i).matches(stack)) {
                return RULES.get(i);
            }
        }
        return null;
    }

    public static ItemRule getOrCreate(String key) {
        ItemRule rule = exact(key);
        if (rule == null) {
            rule = new ItemRule(normalize(key));
            rule.compile();
            RULES.add(rule);
            reindex();
        }
        return rule;
    }

    public static boolean remove(String key) {
        ItemRule rule = exact(key);
        if (rule == null) {
            return false;
        }
        RULES.remove(rule);
        reindex();
        save();
        return true;
    }

    /** После правки правила: пересобрать индекс и записать файл. */
    public static void changed() {
        reindex();
        save();
    }

    public static boolean hasCarryRules() {
        return EcoConfig.restrictionsEnabled && anyCarry;
    }

    public static boolean hasCraftRules() {
        return EcoConfig.restrictionsEnabled && anyCraft;
    }

    /** Игрок не подчиняется запретам (админ или креатив — по настройкам). */
    public static boolean bypass(@Nullable EntityPlayer player) {
        if (player == null) {
            return false;
        }
        if (EcoConfig.restrictionsCreativeBypass && player.isCreative()) {
            return true;
        }
        return EcoConfig.restrictionsAdminsBypass && Perms.isAdmin(player);
    }

    /** Запрещено ли действие с предметом в мире dim (без учёта прав игрока). */
    public static boolean denies(int dim, @Nullable ItemStack stack, RestrictAction action) {
        if (!EcoConfig.restrictionsEnabled || stack == null || stack.isEmpty()
                || (BY_ID.isEmpty() && BY_MOD.isEmpty())) {
            return false;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) {
            return false;
        }
        return check(BY_ID.get(id.toString()), dim, stack, action)
                || check(BY_MOD.get(id.getNamespace()), dim, stack, action);
    }

    private static boolean check(@Nullable List<ItemRule> list, int dim, ItemStack stack, RestrictAction action) {
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            ItemRule rule = list.get(i);
            if (rule.denies(action) && rule.appliesIn(dim) && rule.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Выключен ли мод, которому принадлежит блок (правило «@modid» с запретом использования):
     * тогда его машины и сундуки в этом мире не открываются.
     */
    public static boolean deniesBlockUse(@Nullable EntityPlayer player, @Nullable net.minecraft.block.Block block) {
        if (!EcoConfig.restrictionsEnabled || !anyModUse || player == null || block == null
                || player.world == null || player.world.isRemote) {
            return false;
        }
        ResourceLocation id = block.getRegistryName();
        if (id == null) {
            return false;
        }
        List<ItemRule> list = BY_MOD.get(id.getNamespace());
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            ItemRule rule = list.get(i);
            if (rule.denies(RestrictAction.USE) && rule.appliesIn(player.dimension)) {
                return !bypass(player);
            }
        }
        return false;
    }

    /** Запрещено ли действие этому игроку в его текущем мире. */
    public static boolean denies(@Nullable EntityPlayer player, @Nullable ItemStack stack, RestrictAction action) {
        if (player == null || player.world == null || player.world.isRemote) {
            return false;
        }
        // сначала быстрый поиск по индексу, права проверяем только если запрет нашёлся
        return denies(player.dimension, stack, action) && !bypass(player);
    }
}

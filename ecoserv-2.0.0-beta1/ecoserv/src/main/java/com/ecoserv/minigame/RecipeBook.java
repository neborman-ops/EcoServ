package com.ecoserv.minigame;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ItemCodec;
import com.ecoserv.util.ItemKey;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Рецепты для «Квестовой гонки»: из чего делается вещь (крафт или печь), сколько сырья нужно добыть,
 * сколько плавок и крафтов, и сколько примерно на это уйдёт времени. Ещё — подбор случайной цепочки крафта
 * нужной сложности, если FTB Quests нет или в книге не нашлось подходящих квестов.
 */
public final class RecipeBook {

    /** Сырьё, которого на обычной арене не добыть (ад, край, океанские храмы, боссы). */
    private static final Set<String> UNREACHABLE = new HashSet<String>(Arrays.asList(
            "minecraft:quartz", "minecraft:blaze_rod", "minecraft:nether_wart", "minecraft:soul_sand",
            "minecraft:glowstone_dust", "minecraft:netherrack", "minecraft:end_stone", "minecraft:chorus_fruit",
            "minecraft:chorus_flower", "minecraft:shulker_shell", "minecraft:dragon_breath", "minecraft:prismarine_shard",
            "minecraft:prismarine_crystals", "minecraft:ghast_tear", "minecraft:nether_star", "minecraft:elytra",
            "minecraft:totem_of_undying", "minecraft:skull", "minecraft:sponge", "minecraft:dragon_egg",
            "minecraft:barrier", "minecraft:command_block", "minecraft:structure_block", "minecraft:spawn_egg",
            "minecraft:bedrock", "minecraft:knowledge_book", "minecraft:written_book", "minecraft:filled_map",
            "minecraft:enchanted_book", "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
            "minecraft:tipped_arrow", "minecraft:firework_charge", "minecraft:fireworks", "minecraft:banner",
            "minecraft:shield", "minecraft:magma"));

    /** Выход крафта (id или id:meta) → рецепты. */
    private static Map<String, List<IRecipe>> crafting;
    /** Выход печи (id:meta) → что плавить. */
    private static Map<String, ItemStack> smelting;

    private RecipeBook() {
    }

    private static String k(ItemStack s) {
        ResourceLocation rl = s.getItem().getRegistryName();
        return (rl == null ? "?" : rl.toString()) + ":" + (s.getHasSubtypes() ? s.getMetadata() : 0);
    }

    private static String id(ItemStack s) {
        ResourceLocation rl = s.getItem().getRegistryName();
        return rl == null ? "?" : rl.toString();
    }

    private static synchronized void index() {
        if (crafting != null) {
            return;
        }
        Map<String, List<IRecipe>> c = new HashMap<String, List<IRecipe>>();
        Map<String, ItemStack> f = new HashMap<String, ItemStack>();
        try {
            for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
                ItemStack out = r.getRecipeOutput();
                if (out.isEmpty() || r.getIngredients().isEmpty()) {
                    continue;
                }
                List<IRecipe> list = c.get(k(out));
                if (list == null) {
                    list = new ArrayList<IRecipe>();
                    c.put(k(out), list);
                }
                list.add(r);
            }
            for (Map.Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
                ItemStack in = e.getKey();
                ItemStack out = e.getValue();
                if (in.isEmpty() || out.isEmpty() || f.containsKey(k(out))) {
                    continue;
                }
                ItemStack input = in.copy();
                if (input.getMetadata() == 32767) {
                    input.setItemDamage(0);
                }
                f.put(k(out), input);
            }
        } catch (Throwable t) {
            EcoServ.LOG.warn("Квестовая гонка: не удалось прочитать рецепты: {}", t.toString());
        }
        crafting = c;
        smelting = f;
    }

    /** Сбросить кэш (после /reload рецептов других модов). */
    public static synchronized void reset() {
        crafting = null;
        smelting = null;
    }

    // --- выбор рецепта ---------------------------------------------------------------------------------

    @Nullable
    private static IRecipe bestRecipe(ItemStack target, Set<String> path) {
        index();
        List<IRecipe> list = crafting.get(k(target));
        if (list == null) {
            return null;
        }
        IRecipe best = null;
        int bestScore = Integer.MAX_VALUE;
        for (IRecipe r : list) {
            int score = 0;
            boolean bad = false;
            Set<String> kinds = new HashSet<String>();
            for (Ingredient ing : r.getIngredients()) {
                ItemStack pick = pick(ing);
                if (pick.isEmpty()) {
                    continue;
                }
                if (path.contains(k(pick)) || k(pick).equals(k(target))) {
                    bad = true; // зацикливание (блок слитков ↔ слитки)
                    break;
                }
                kinds.add(k(pick));
                score++;
            }
            if (bad || kinds.isEmpty()) {
                continue;
            }
            score += kinds.size() * 3;
            ResourceLocation rid = r.getRegistryName();
            if (rid == null || !"minecraft".equals(rid.getNamespace())) {
                score += 2; // ванильный рецепт предсказуемее
            }
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    /** Какую вещь взять из ингредиента: ванильную и с метой 0, если можно. */
    private static ItemStack pick(Ingredient ing) {
        ItemStack[] options = ing.getMatchingStacks();
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack s : options) {
            if (s.isEmpty()) {
                continue;
            }
            if (best.isEmpty() || (id(s).startsWith("minecraft:") && !id(best).startsWith("minecraft:"))) {
                best = s;
            }
        }
        if (best.isEmpty()) {
            return best;
        }
        ItemStack one = best.copy();
        one.setCount(1);
        if (one.getMetadata() == 32767) {
            one.setItemDamage(0);
        }
        return one;
    }

    // --- оценка ------------------------------------------------------------------------------------------

    /** Посчитать сырьё, плавки и крафты для всех вещей задания. */
    public static RaceTask.Estimate estimate(List<RaceTask.Step> steps) {
        index();
        RaceTask.Estimate e = new RaceTask.Estimate();
        Map<String, double[]> raw = new LinkedHashMap<String, double[]>();
        Map<String, String> names = new HashMap<String, String>();
        double[] totals = new double[2]; // плавки, крафты
        boolean[] partial = new boolean[1];
        for (RaceTask.Step s : steps) {
            for (RaceTask.Need need : s.needs) {
                ItemStack stack = need.icon();
                if (stack.isEmpty()) {
                    partial[0] = true;
                    continue;
                }
                expand(stack, need.count, new HashSet<String>(), 0, raw, names, totals, partial);
            }
        }
        double seconds = 0;
        for (Map.Entry<String, double[]> r : raw.entrySet()) {
            int n = (int) Math.ceil(r.getValue()[0]);
            e.raw.put(names.get(r.getKey()), n);
            seconds += n * r.getValue()[1];
        }
        e.smelts = (int) Math.ceil(totals[0]);
        e.crafts = (int) Math.ceil(totals[1]);
        // плавка 10 с, считаем две печи; крафт — 1.5 с на операцию
        seconds += e.smelts * 5.0D + e.crafts * 1.5D;
        e.seconds = (int) Math.ceil(seconds) + 30;
        e.partial = partial[0];
        return e;
    }

    private static void expand(ItemStack item, double count, Set<String> path, int depth,
                               Map<String, double[]> raw, Map<String, String> names, double[] totals, boolean[] partial) {
        String key = k(item);
        if (depth < 10 && !path.contains(key)) {
            IRecipe r = bestRecipe(item, path);
            if (r != null) {
                int made = Math.max(1, r.getRecipeOutput().getCount());
                double times = Math.ceil(count / made);
                totals[1] += times;
                path.add(key);
                for (Ingredient ing : r.getIngredients()) {
                    ItemStack pick = pick(ing);
                    if (!pick.isEmpty() && !isTool(pick)) {
                        expand(pick, times, path, depth + 1, raw, names, totals, partial);
                    }
                }
                path.remove(key);
                return;
            }
            ItemStack input = smelting.get(key);
            if (input != null && !path.contains(k(input))) {
                totals[0] += count;
                path.add(key);
                expand(input, count, path, depth + 1, raw, names, totals, partial);
                path.remove(key);
                return;
            }
        }
        double[] cur = raw.get(key);
        if (cur == null) {
            cur = new double[]{0, gatherSeconds(item)};
            raw.put(key, cur);
            names.put(key, item.getDisplayName());
        }
        cur[0] += count;
    }

    /** Вёдра и т.п. возвращаются после крафта — не считаем как расход. */
    private static boolean isTool(ItemStack s) {
        String id = id(s);
        return id.equals("minecraft:water_bucket") || id.equals("minecraft:lava_bucket") || id.equals("minecraft:milk_bucket");
    }

    /** Сколько секунд в среднем добыть одну штуку сырья. */
    static double gatherSeconds(ItemStack s) {
        String id = id(s);
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (p.contains("log") || p.equals("wood")) {
            return 3;
        }
        if (p.equals("cobblestone") || p.equals("stone") || p.equals("dirt") || p.equals("sand") || p.equals("gravel")
                || p.equals("snowball") || p.equals("clay_ball") || p.equals("clay") || p.equals("ice")) {
            return 1.5;
        }
        if (p.equals("diamond") || p.equals("diamond_ore")) {
            return 45;
        }
        if (p.equals("emerald") || p.equals("emerald_ore")) {
            return 60;
        }
        if (p.equals("coal") || p.equals("coal_ore")) {
            return 5;
        }
        if (p.equals("redstone") || p.equals("dye") || p.contains("lapis")) {
            return 10;
        }
        if (p.endsWith("_ore") || p.equals("obsidian")) {
            return 9;
        }
        if (p.equals("ender_pearl")) {
            return 60;
        }
        if (p.equals("string") || p.equals("bone") || p.equals("gunpowder") || p.equals("rotten_flesh")
                || p.equals("spider_eye") || p.equals("leather") || p.equals("feather") || p.equals("slime_ball")
                || p.equals("egg") || p.equals("beef") || p.equals("porkchop") || p.equals("chicken")
                || p.equals("mutton") || p.equals("rabbit") || p.equals("rabbit_hide") || p.equals("wool")
                || p.equals("fish") || p.equals("ink_sac")) {
            return 18;
        }
        if (p.equals("wheat") || p.equals("wheat_seeds") || p.equals("reeds") || p.equals("apple")
                || p.equals("carrot") || p.equals("potato") || p.equals("pumpkin") || p.equals("melon")
                || p.equals("cactus") || p.equals("vine") || p.equals("sapling") || p.contains("flower")
                || p.contains("mushroom") || p.equals("beetroot") || p.equals("waterlily")) {
            return 8;
        }
        return 6;
    }

    // --- цепочки крафта ---------------------------------------------------------------------------------------

    /** Глубина вещи: 0 — сырьё, иначе 1 + самая глубокая составляющая. */
    private static int depth(ItemStack item, Set<String> path, Map<String, Integer> memo, boolean[] bad) {
        String key = k(item);
        if (UNREACHABLE.contains(id(item))) {
            bad[0] = true;
            return 0;
        }
        Integer m = memo.get(key);
        if (m != null) {
            return m;
        }
        if (path.size() > 10 || path.contains(key)) {
            return 0;
        }
        path.add(key);
        int d = 0;
        IRecipe r = bestRecipe(item, path);
        if (r != null) {
            for (Ingredient ing : r.getIngredients()) {
                ItemStack pick = pick(ing);
                if (!pick.isEmpty()) {
                    d = Math.max(d, 1 + depth(pick, path, memo, bad));
                }
            }
        } else {
            ItemStack input = smelting.get(key);
            if (input != null) {
                d = 1 + depth(input, path, memo, bad);
            }
        }
        path.remove(key);
        memo.put(key, d);
        return d;
    }

    /** Какие глубины подходят сложности 1–5. */
    static int[] depthRange(int difficulty) {
        switch (Math.max(1, Math.min(5, difficulty))) {
            case 1:
                return new int[]{1, 2};
            case 2:
                return new int[]{2, 3};
            case 3:
                return new int[]{3, 4};
            case 4:
                return new int[]{4, 5};
            default:
                return new int[]{5, 12};
        }
    }

    /**
     * Случайная цепочка крафта: цель нужной глубины и шаги по самой длинной ветке
     * (например: железная руда → слиток → ведро → торт). null — ничего не нашлось.
     */
    @Nullable
    public static RaceTask randomChain(int difficulty, Random rnd, boolean vanillaOnly) {
        index();
        int[] range = depthRange(difficulty);
        Map<String, Integer> memo = new HashMap<String, Integer>();
        List<ItemStack> candidates = new ArrayList<ItemStack>();
        Set<String> seen = new HashSet<String>();
        for (List<IRecipe> list : crafting.values()) {
            ItemStack out = list.get(0).getRecipeOutput();
            String id = id(out);
            if (vanillaOnly && !id.startsWith("minecraft:")) {
                continue;
            }
            if (!seen.add(k(out)) || UNREACHABLE.contains(id)) {
                continue;
            }
            boolean[] bad = new boolean[1];
            int d = depth(out, new HashSet<String>(), memo, bad);
            if (!bad[0] && d >= range[0] && d <= range[1]) {
                ItemStack one = out.copy();
                one.setCount(1);
                candidates.add(one);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        ItemStack target = candidates.get(rnd.nextInt(candidates.size()));
        // путь: от цели вниз по самой глубокой составляющей
        List<ItemStack> chain = new ArrayList<ItemStack>();
        List<Integer> counts = new ArrayList<Integer>();
        ItemStack cur = target;
        int need = 1;
        Set<String> path = new HashSet<String>();
        while (cur != null && chain.size() < 8) {
            chain.add(0, cur);
            counts.add(0, need);
            path.add(k(cur));
            IRecipe r = bestRecipe(cur, path);
            ItemStack next = null;
            int nextCount = 0;
            if (r != null) {
                int bestD = 0;
                Map<String, Integer> per = new HashMap<String, Integer>();
                for (Ingredient ing : r.getIngredients()) {
                    ItemStack pick = pick(ing);
                    if (!pick.isEmpty()) {
                        per.put(k(pick), per.containsKey(k(pick)) ? per.get(k(pick)) + 1 : 1);
                    }
                }
                for (Ingredient ing : r.getIngredients()) {
                    ItemStack pick = pick(ing);
                    if (pick.isEmpty()) {
                        continue;
                    }
                    Integer d = memo.get(k(pick));
                    if (d != null && d > 0 && d >= bestD && !path.contains(k(pick))) {
                        bestD = d;
                        next = pick;
                        int made = Math.max(1, r.getRecipeOutput().getCount());
                        nextCount = (int) Math.ceil((double) need / made) * per.get(k(pick));
                    }
                }
            } else {
                ItemStack input = smelting.get(k(cur));
                Integer d = input == null ? null : memo.get(k(input));
                if (input != null && d != null && d > 0) {
                    next = input;
                    nextCount = need;
                }
            }
            cur = next;
            need = Math.max(1, nextCount);
        }
        RaceTask task = new RaceTask();
        task.source = "recipe";
        task.difficulty = difficulty;
        task.title = target.getDisplayName();
        for (int i = 0; i < chain.size(); i++) {
            ItemStack s = chain.get(i);
            RaceTask.Step step = new RaceTask.Step();
            step.title = (i == chain.size() - 1 ? "Цель: " : "") + s.getDisplayName();
            RaceTask.Need n = new RaceTask.Need();
            n.key = ItemKey.of(s, false, s.getHasSubtypes()).serialize();
            n.sample = ItemCodec.encode(s);
            n.name = s.getDisplayName();
            n.count = Math.min(64, counts.get(i));
            step.needs.add(n);
            task.steps.add(step);
        }
        task.estimate = estimate(task.steps);
        return task;
    }

    /** Предмет по ключу (для названий). */
    static ItemStack stackOf(String key) {
        ItemKey k = ItemKey.parse(key);
        Item item = Item.getByNameOrId(k.id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, Math.max(0, k.meta));
    }

    static NonNullList<ItemStack> none() {
        return NonNullList.create();
    }
}

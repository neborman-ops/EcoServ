package com.ecoserv.restrict;

import com.ecoserv.EcoServ;
import com.ecoserv.util.ItemKey;
import com.ecoserv.world.WorldNames;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Частичный запрет одного предмета. Хранится в config/ecoserv/restrictions.json:
 * <pre>
 * {
 *   "item": "minecraft:tnt@-1",
 *   "deny": ["craft", "place"],     // что запрещено: craft, use, place, carry
 *   "worlds": ["nether"],           // только в этих мирах (пусто = везде)
 *   "except": [],                   // во всех мирах, кроме этих
 *   "note": "в аду TNT можно носить, но нельзя крафтить и ставить"
 * }
 * </pre>
 * «carry» — предмет нельзя держать в инвентаре в этом мире: он уходит в буфер
 * и возвращается сам, когда игрок попадёт туда, где предмет разрешён.
 * <p>
 * Ключ вида «@mekanism» — правило на все предметы и блоки мода (выключение мода в мире).
 */
public class ItemRule {

    public String item = "minecraft:air@-1";
    public List<String> deny = new ArrayList<String>();
    public List<String> worlds = new ArrayList<String>();
    public List<String> except = new ArrayList<String>();
    public String note = "";

    private transient ItemKey key;
    private transient EnumSet<RestrictAction> denied;
    private transient Set<Integer> worldDims;
    private transient Set<Integer> exceptDims;

    public ItemRule() {
    }

    public ItemRule(String item) {
        this.item = item;
    }

    /** Разобрать строки в быстрые структуры (после загрузки и после правки). */
    public void compile() {
        if (deny == null) {
            deny = new ArrayList<String>();
        }
        if (worlds == null) {
            worlds = new ArrayList<String>();
        }
        if (except == null) {
            except = new ArrayList<String>();
        }
        if (note == null) {
            note = "";
        }
        key = isModRule() ? ItemKey.parse("minecraft:book@0") : ItemKey.parse(item);
        denied = EnumSet.noneOf(RestrictAction.class);
        for (int i = 0; i < deny.size(); i++) {
            RestrictAction action = RestrictAction.byId(deny.get(i));
            if (action == null) {
                EcoServ.LOG.warn("restrictions.json: неизвестное действие '{}' у {}", deny.get(i), item);
            } else {
                denied.add(action);
            }
        }
        worldDims = resolve(worlds);
        exceptDims = resolve(except);
    }

    private Set<Integer> resolve(List<String> names) {
        Set<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < names.size(); i++) {
            Integer dim = WorldNames.resolve(names.get(i));
            if (dim == null && WorldNames.isGroup(names.get(i))) {
                continue; // подмир: проверяется по группе
            }
            if (dim == null) {
                EcoServ.LOG.warn("restrictions.json: мир '{}' у {} не найден", names.get(i), item);
            } else {
                set.add(dim);
            }
        }
        return set;
    }

    /** Правило на весь мод («@modid»). */
    public boolean isModRule() {
        return item != null && item.startsWith("@");
    }

    public String modId() {
        return isModRule() ? item.substring(1) : "";
    }

    /** Подходит ли предмет под правило. */
    public boolean matches(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isModRule()) {
            net.minecraft.util.ResourceLocation id = stack.getItem().getRegistryName();
            return id != null && id.getNamespace().equals(modId());
        }
        return key().matches(stack);
    }

    /** Название для людей: предмет или «Мод Mekanism». */
    public String title() {
        if (isModRule()) {
            return "Мод " + modName(modId());
        }
        net.minecraft.item.ItemStack stack = key().toStack(1);
        return stack.isEmpty() ? item : stack.getDisplayName();
    }

    public static String modName(String modId) {
        net.minecraftforge.fml.common.ModContainer mod =
                net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(modId);
        return mod == null ? modId : mod.getName() + " (" + modId + ")";
    }

    public ItemKey key() {
        if (key == null) {
            compile();
        }
        return key;
    }

    public boolean denies(RestrictAction action) {
        if (denied == null) {
            compile();
        }
        return denied.contains(action);
    }

    public boolean hasAnyDeny() {
        if (denied == null) {
            compile();
        }
        return !denied.isEmpty();
    }

    /** Действует ли правило в этом мире. */
    public boolean appliesIn(int dim) {
        if (worldDims == null) {
            compile();
        }
        if (!worlds.isEmpty() && !worldDims.contains(dim) && !WorldNames.inGroups(dim, worlds)) {
            return false;
        }
        return !exceptDims.contains(dim) && !WorldNames.inGroups(dim, except);
    }

    public void setDenied(RestrictAction action, boolean value) {
        deny.remove(action.id);
        if (value) {
            deny.add(action.id);
        }
        compile();
    }

    /** 0 — везде, 1 — только в выбранных мирах, 2 — везде кроме выбранных. */
    public int worldMode() {
        if (!worlds.isEmpty()) {
            return 1;
        }
        return except.isEmpty() ? 0 : 2;
    }

    public List<String> selectedWorlds() {
        return worldMode() == 2 ? except : worlds;
    }

    public String describeWorlds() {
        switch (worldMode()) {
            case 1:
                return "только в: " + String.join(", ", worlds);
            case 2:
                return "везде, кроме: " + String.join(", ", except);
            default:
                return "во всех мирах";
        }
    }

    public String describeDeny() {
        if (!hasAnyDeny()) {
            return "ничего не запрещено";
        }
        StringBuilder sb = new StringBuilder();
        for (RestrictAction a : RestrictAction.values()) {
            if (denies(a)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(a.title.toLowerCase());
            }
        }
        return sb.toString();
    }
}

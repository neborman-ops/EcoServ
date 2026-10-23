package com.ecoserv.npc;

/** Кем может быть NPC и спутник: id сущности, название, иконка. */
public final class MobTypes {

    public static final String[][] ALL = {
            {"minecraft:villager", "Житель", "minecraft:emerald"},
            {"minecraft:armor_stand", "Стойка с бронёй", "minecraft:armor_stand"},
            {"minecraft:zombie", "Зомби", "minecraft:rotten_flesh"},
            {"minecraft:husk", "Кадавр", "minecraft:sand"},
            {"minecraft:skeleton", "Скелет", "minecraft:bone"},
            {"minecraft:wither_skeleton", "Скелет-иссушитель", "minecraft:coal"},
            {"minecraft:zombie_pigman", "Зомби-свиночеловек", "minecraft:gold_nugget"},
            {"minecraft:witch", "Ведьма", "minecraft:glass_bottle"},
            {"minecraft:vindication_illager", "Поборник", "minecraft:iron_axe"},
            {"minecraft:evocation_illager", "Заклинатель", "minecraft:totem_of_undying"},
            {"minecraft:iron_golem", "Железный голем", "minecraft:iron_block"},
            {"minecraft:snowman", "Снеговик", "minecraft:pumpkin"},
            {"minecraft:enderman", "Эндермен", "minecraft:ender_pearl"},
            {"minecraft:blaze", "Ифрит", "minecraft:blaze_rod"},
            {"minecraft:creeper", "Крипер", "minecraft:gunpowder"},
            {"minecraft:spider", "Паук", "minecraft:string"},
            {"minecraft:pig", "Свинья", "minecraft:porkchop"},
            {"minecraft:cow", "Корова", "minecraft:leather"},
            {"minecraft:sheep", "Овца", "minecraft:wool"},
            {"minecraft:chicken", "Курица", "minecraft:feather"},
            {"minecraft:wolf", "Волк", "minecraft:bone"},
            {"minecraft:ocelot", "Оцелот", "minecraft:fish"},
            {"minecraft:parrot", "Попугай", "minecraft:wheat_seeds"},
            {"minecraft:polar_bear", "Белый медведь", "minecraft:snowball"},
            {"minecraft:llama", "Лама", "minecraft:hay_block"},
            {"minecraft:horse", "Лошадь", "minecraft:saddle"},
            {"minecraft:rabbit", "Кролик", "minecraft:rabbit_foot"},
            {"minecraft:bat", "Летучая мышь", "minecraft:coal"},
            {"minecraft:slime", "Слизень", "minecraft:slime_ball"},
            {"minecraft:vex", "Вредина", "minecraft:iron_sword"},
    };

    public static final String[][] PROFESSIONS = {
            {"0", "Фермер", "minecraft:wheat"},
            {"1", "Библиотекарь", "minecraft:book"},
            {"2", "Священник", "minecraft:ender_eye"},
            {"3", "Кузнец", "minecraft:iron_ingot"},
            {"4", "Мясник", "minecraft:cooked_beef"},
            {"5", "Нитвит (зелёный)", "minecraft:slime_ball"},
    };

    private MobTypes() {
    }

    public static String title(String type) {
        for (String[] t : ALL) {
            if (t[0].equals(type)) {
                return t[1];
            }
        }
        return type;
    }

    public static String icon(String type) {
        for (String[] t : ALL) {
            if (t[0].equals(type)) {
                return t[2];
            }
        }
        return "minecraft:spawn_egg";
    }
}

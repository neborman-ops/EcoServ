package com.ecoserv.content;

import com.ecoserv.dev.StepsEditor;
import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.util.Selection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Конструктор своего контента (2.0 beta3). Всё, что создаёшь, сначала копится в буфере; «Применить» —
 * сразу у всех (обновятся ресурсы), иначе — само при перезапуске сервера.
 */
public class ContentScreen extends UiScreen {

    private enum Tab {
        BLOCKS("Блоки", "minecraft:grass"),
        ITEMS("Предметы", "minecraft:diamond"),
        FLUIDS("Жидкости", "minecraft:water_bucket"),
        TEXTURES("Текстуры", "minecraft:painting"),
        MODELS("3D-модели", "minecraft:armor_stand"),
        BUFFER("Буфер", "minecraft:chest");

        final String title;
        final String icon;

        Tab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final String[][] SHAPES = {
            {"cube", "Куб", "Одна текстура со всех сторон", "minecraft:stone"},
            {"column", "Столб", "Верх/низ и бока разные (как бревно)", "minecraft:log"},
            {"cross", "Растение", "Две плоскости крест-накрест (цветок, трава)", "minecraft:red_flower"},
            {"glass", "Стекло", "Прозрачные пиксели, соседние стёкла сливаются", "minecraft:glass"},
            {"model", "3D-модель", "Своя модель из постройки", "minecraft:armor_stand"},
    };

    private static final String[][] SOUNDS = {{"stone", "Камень"}, {"wood", "Дерево"}, {"metal", "Металл"}, {"glass", "Стекло"},
            {"grass", "Трава"}, {"sand", "Песок"}, {"cloth", "Шерсть"}, {"snow", "Снег"}};

    private Tab tab = Tab.BLOCKS;
    private int block = -1;
    private int item = -1;
    private int fluid = -1;
    private String newKey = "";
    private int paintSize = 16;
    private final StepsEditor.State steps = new StepsEditor.State();

    public ContentScreen(EntityPlayerMP player) {
        super(player);
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private static ContentPack b() {
        return CustomContent.buffer();
    }

    private static void save() {
        CustomContent.saveBuffer();
    }

    @Override
    protected void build(Ui ui) {
        if (StepsEditor.modal(ui, player, steps)) {
            return;
        }
        boolean pending = CustomContent.pendingChanges() > 0;
        ui.title("Свой контент").subtitle(pending ? "&eЕсть изменения в буфере — «Буфер» → «Применить»" : "&7Всё применено")
                .icon(new ItemStack(Blocks.CRAFTING_TABLE));
        for (Tab t : Tab.values()) {
            final Tab tt = t;
            ui.tab(t.title + (t == Tab.BUFFER && pending ? " &e●" : ""), com.ecoserv.ui.UiUtil.icon(t.icon), tab == t, () -> {
                tab = tt;
                block = -1;
                item = -1;
                fluid = -1;
            });
        }
        switch (tab) {
            case ITEMS:
                items(ui);
                break;
            case FLUIDS:
                fluids(ui);
                break;
            case TEXTURES:
                textures(ui);
                break;
            case MODELS:
                models(ui);
                break;
            case BUFFER:
                buffer(ui);
                break;
            default:
                blocks(ui);
        }
    }

    private void textureSelect(Ui ui, String label, String value, Ui.Text set) {
        Ui.Select s = ui.select(label, "Нарисуй во вкладке «Текстуры»", value, set);
        s.option("", "— нет —", "");
        for (String k : b().textures.keySet()) {
            s.option(k, k, "");
        }
        if (!value.isEmpty() && !b().textures.containsKey(value)) {
            s.option(value, value, "нет такой");
        }
    }

    private void modelSelect(Ui ui, String value, Ui.Text set) {
        Ui.Select s = ui.select("3D-модель", "Сними во вкладке «3D-модели»", value, set);
        s.option("", "— нет —", "");
        for (String k : b().models.keySet()) {
            s.option(k, k, "");
        }
    }

    // --- блоки ---------------------------------------------------------------------------------------

    private void blocks(Ui ui) {
        final ContentPack.BlockDef d = block < 0 ? null : b().block(block);
        if (d != null) {
            blockEditor(ui, d);
            return;
        }
        ui.text("&7Свои блоки: текстура, форма (куб, столб, растение, стекло, 3D-модель), прочность, свет, звук."
                + " Слотов: " + ContentPack.BLOCKS + ".");
        for (final ContentPack.BlockDef x : b().blocks) {
            boolean live = CustomContent.active().block(x.slot) != null;
            ui.row(live ? new ItemStack(CustomContent.BLOCKS[x.slot]) : new ItemStack(Items.PAPER),
                    com.ecoserv.util.ChatUtil.color(x.name) + " &8#" + x.slot)
                    .line("&7" + shapeTitle(x.shape) + (live ? "" : " &e· в буфере"))
                    .onClick(() -> block = x.slot)
                    .button("✎", Ui.BLUE, () -> block = x.slot);
        }
        Ui.Select add = ui.select("Новый блок", "Выбери форму", "", v -> {
            int slot = b().freeBlock();
            if (slot < 0) {
                throw new UiError("Все слоты блоков заняты.");
            }
            ContentPack.BlockDef n = new ContentPack.BlockDef();
            n.slot = slot;
            n.shape = v;
            if ("glass".equals(v)) {
                n.cutout = true;
                n.sound = "glass";
            }
            if ("cross".equals(v)) {
                n.collision = false;
                n.sound = "grass";
                n.hardness = 0;
            }
            b().blocks.add(n);
            save();
            block = slot;
        });
        for (String[] s : SHAPES) {
            add.option(s[0], s[1], s[2], com.ecoserv.ui.UiUtil.icon(s[3]));
        }
    }

    static String shapeTitle(String shape) {
        for (String[] s : SHAPES) {
            if (s[0].equals(shape)) {
                return s[1];
            }
        }
        return shape;
    }

    private void blockEditor(Ui ui, final ContentPack.BlockDef d) {
        ui.back(() -> block = -1);
        ui.header("Блок #" + d.slot);
        ui.field("Название", "& — цвета", d.name, v -> {
            d.name = v.trim().isEmpty() ? "Свой блок" : v.trim();
            save();
        }).max(64);
        Ui.Select sh = ui.select("Форма", "", d.shape, v -> {
            d.shape = v;
            save();
        });
        for (String[] s : SHAPES) {
            sh.option(s[0], s[1], s[2], com.ecoserv.ui.UiUtil.icon(s[3]));
        }
        if ("model".equals(d.shape)) {
            modelSelect(ui, d.model, v -> {
                d.model = v;
                save();
            });
        } else if ("column".equals(d.shape)) {
            textureSelect(ui, "Верх и низ", d.top, v -> {
                d.top = v;
                save();
            });
            textureSelect(ui, "Бока", d.side, v -> {
                d.side = v;
                save();
            });
        } else {
            textureSelect(ui, "Текстура", d.texture, v -> {
                d.texture = v;
                save();
            });
        }
        ui.buttons().add("Нарисовать текстуру", Ui.BLUE, () -> {
            String key = d.texture.isEmpty() ? "block_" + d.slot : d.texture;
            if (d.texture.isEmpty()) {
                d.texture = key;
                save();
            }
            ContentNet.paint(player, key, paintSize);
        });
        ui.slider("Прочность", "Как долго ломать (камень — 1.5, обсидиан — 50)", d.hardness, 0, 50, 0.1, "", v -> {
            d.hardness = v;
            save();
        });
        ui.slider("Взрывоустойчивость", "", d.resistance, 0, 2000, 1, "", v -> {
            d.resistance = v;
            save();
        });
        ui.slider("Свет", "0 — не светит, 15 — как светокамень", d.light, 0, 15, 1, "", v -> {
            d.light = (int) v;
            save();
        });
        Ui.Select snd = ui.select("Звук", "", d.sound, v -> {
            d.sound = v;
            save();
        });
        for (String[] s : SOUNDS) {
            snd.option(s[0], s[1]);
        }
        ui.toggle("Твёрдый", "Иначе сквозь него можно пройти", d.collision, on -> {
            d.collision = on;
            save();
        });
        ui.toggle("Прозрачные пиксели", "Как листва и стекло", d.cutout, on -> {
            d.cutout = on;
            save();
        });
        ui.toggle("Полупрозрачный", "Как цветное стекло и лёд", d.translucent, on -> {
            d.translucent = on;
            save();
        });
        ui.toggle("Выпадает сам", "", d.dropsSelf, on -> {
            d.dropsSelf = on;
            save();
        });
        ui.toggle("Скрыт в творческом", "", d.hidden, on -> {
            d.hidden = on;
            save();
        });
        boolean live = CustomContent.active().block(d.slot) != null;
        ui.buttons().add("Взять блок", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, new ItemStack(CustomContent.BLOCKS[d.slot], 64)))
                .disabled(!live).tip(live ? "" : "Сначала примени буфер").add("Удалить", Ui.RED, () -> {
                    b().blocks.remove(d);
                    save();
                    block = -1;
                }).confirm("Удалить блок? Уже поставленные станут пустым слотом.");
    }

    // --- предметы --------------------------------------------------------------------------------------

    private void items(Ui ui) {
        final ContentPack.ItemDef d = item < 0 ? null : b().item(item);
        if (d != null) {
            itemEditor(ui, d);
            return;
        }
        ui.text("&7Свои предметы со своей картинкой или 3D-моделью и действием по ПКМ. Слотов: " + ContentPack.ITEMS + ".");
        for (final ContentPack.ItemDef x : b().items) {
            boolean live = CustomContent.active().item(x.slot) != null;
            ui.row(live ? new ItemStack(CustomContent.ITEMS[x.slot]) : new ItemStack(Items.PAPER),
                    com.ecoserv.util.ChatUtil.color(x.name) + " &8#" + x.slot)
                    .line(live ? "" : "&e· в буфере").onClick(() -> item = x.slot).button("✎", Ui.BLUE, () -> item = x.slot);
        }
        ui.buttons().add("+ Новый предмет", Ui.GREEN, () -> {
            int slot = b().freeItem();
            if (slot < 0) {
                throw new UiError("Все слоты предметов заняты.");
            }
            ContentPack.ItemDef n = new ContentPack.ItemDef();
            n.slot = slot;
            b().items.add(n);
            save();
            item = slot;
        });
    }

    private void itemEditor(Ui ui, final ContentPack.ItemDef d) {
        ui.back(() -> item = -1);
        ui.header("Предмет #" + d.slot);
        ui.field("Название", "& — цвета", d.name, v -> {
            d.name = v.trim().isEmpty() ? "Свой предмет" : v.trim();
            save();
        }).max(64);
        textureSelect(ui, "Картинка", d.texture, v -> {
            d.texture = v;
            save();
        });
        ui.buttons().add("Нарисовать картинку", Ui.BLUE, () -> {
            String key = d.texture.isEmpty() ? "item_" + d.slot : d.texture;
            if (d.texture.isEmpty()) {
                d.texture = key;
                save();
            }
            ContentNet.paint(player, key, paintSize);
        });
        modelSelect(ui, d.model, v -> {
            d.model = v;
            save();
        });
        ui.field("Описание", "| — новая строка", String.join("|", d.lore), v -> {
            d.lore.clear();
            for (String l : v.split("\\|")) {
                if (!l.trim().isEmpty()) {
                    d.lore.add(l.trim());
                }
            }
            save();
        }).clearable().max(300);
        ui.slider("В стопке", "", d.maxStack, 1, 64, 1, "", v -> {
            d.maxStack = (int) v;
            save();
        });
        ui.toggle("Свечение", "", d.glow, on -> {
            d.glow = on;
            save();
        });
        ui.slider("Пауза между ПКМ", "секунд", d.cooldown, 0, 300, 1, " с", v -> {
            d.cooldown = (int) v;
            save();
        });
        ui.toggle("Тратится", "", d.consume, on -> {
            d.consume = on;
            save();
        });
        StepsEditor.build(ui, steps, "Действие по ПКМ", d.steps, ContentScreen::save);
        boolean live = CustomContent.active().item(d.slot) != null;
        ui.buttons().add("Взять", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, new ItemStack(CustomContent.ITEMS[d.slot])))
                .disabled(!live).add("Удалить", Ui.RED, () -> {
                    b().items.remove(d);
                    save();
                    item = -1;
                }).confirm("Удалить предмет?");
    }

    // --- жидкости --------------------------------------------------------------------------------------

    private void fluids(Ui ui) {
        final ContentPack.FluidDef d = fluid < 0 ? null : b().fluid(fluid);
        if (d != null) {
            fluidEditor(ui, d);
            return;
        }
        ui.text("&7Свои жидкости: цвет, свечение, вязкость. Ведро — у каждой. Слотов: 12 как вода и 4 как лава.");
        for (final ContentPack.FluidDef x : b().fluids) {
            ui.row(new ItemStack(x.slot < ContentPack.WATER_FLUIDS ? Items.WATER_BUCKET : Items.LAVA_BUCKET),
                    com.ecoserv.util.ChatUtil.color(x.name) + " &8#" + x.slot)
                    .line(String.format("&7#%06X", x.color)).onClick(() -> fluid = x.slot).button("✎", Ui.BLUE, () -> fluid = x.slot);
        }
        ui.buttons().add("+ Как вода", Ui.BLUE, () -> fluid = newFluid(false)).add("+ Как лава", Ui.YELLOW, () -> fluid = newFluid(true));
    }

    private int newFluid(boolean lava) {
        int slot = b().freeFluid(lava);
        if (slot < 0) {
            throw new UiError("Слоты этого вида заняты.");
        }
        ContentPack.FluidDef n = new ContentPack.FluidDef();
        n.slot = slot;
        if (lava) {
            n.color = 0xFF6A00;
            n.luminosity = 15;
            n.density = 3000;
            n.viscosity = 6000;
            n.temperature = 1300;
        }
        b().fluids.add(n);
        save();
        return slot;
    }

    private void fluidEditor(Ui ui, final ContentPack.FluidDef d) {
        ui.back(() -> fluid = -1);
        ui.header("Жидкость #" + d.slot + (d.slot < ContentPack.WATER_FLUIDS ? " &8(как вода)" : " &8(как лава)"));
        ui.field("Название", "", d.name, v -> {
            d.name = v.trim().isEmpty() ? "Жидкость" : v.trim();
            save();
        }).max(64);
        ui.color("Цвет", "", d.color, v -> {
            d.color = (int) v;
            save();
        });
        ui.slider("Свечение", "", d.luminosity, 0, 15, 1, "", v -> {
            d.luminosity = (int) v;
            save();
        });
        ui.slider("Вязкость", "Больше — течёт медленнее (вода 1000, лава 6000)", d.viscosity, 100, 10000, 100, "", v -> {
            d.viscosity = (int) v;
            save();
        });
        ui.slider("Плотность", "Меньше нуля — течёт вверх, как газ", d.density, -1000, 5000, 50, "", v -> {
            d.density = (int) v;
            save();
        });
        boolean live = CustomContent.active().fluid(d.slot) != null;
        ui.buttons().add("Взять ведро", Ui.GREEN, () -> PlayerUtil.giveOrDrop(player, bucket(d.slot))).disabled(!live)
                .add("Удалить", Ui.RED, () -> {
                    b().fluids.remove(d);
                    save();
                    fluid = -1;
                }).confirm("Удалить жидкость?");
    }

    static ItemStack bucket(int slot) {
        return net.minecraftforge.fluids.FluidUtil.getFilledBucket(new net.minecraftforge.fluids.FluidStack(CustomContent.FLUIDS[slot], 1000));
    }

    // --- текстуры и модели ---------------------------------------------------------------------------------

    private void textures(Ui ui) {
        ui.text("&7Текстуры рисуются прямо в игре: кисть, заливка, пипетка, палитра, можно взять картинку предмета из руки"
                + " и перекрасить.");
        ui.select("Размер новой", "", Integer.toString(paintSize), v -> paintSize = Integer.parseInt(v))
                .option("16", "16×16 — как в Minecraft").option("32", "32×32 — детальнее");
        for (final Map.Entry<String, byte[]> e : b().textures.entrySet()) {
            ui.row(new ItemStack(Items.PAINTING), "&f" + e.getKey()).line("&8" + e.getValue().length + " байт")
                    .button("Рисовать", Ui.BLUE, () -> ContentNet.paint(player, e.getKey(), paintSize))
                    .button("✕", Ui.RED, () -> {
                        b().textures.remove(e.getKey());
                        save();
                    }).confirm("Удалить текстуру «" + e.getKey() + "»?");
        }
        ui.field("Новая текстура", "Имя: латиница, цифры, _ — Enter", newKey, v -> {
            String key = ContentNet.clean(v);
            if (key.isEmpty()) {
                throw new UiError("Нужно имя.");
            }
            ContentNet.paint(player, key, paintSize);
        }).placeholder("ruby_ore").max(32);
    }

    private void models(Ui ui) {
        ui.text("&7Модель снимается с постройки: построй её из блоков (до 16×16×16), выдели топором стройки (/build)"
                + " и нажми «Снять». Каждый блок станет кубиком модели со своими текстурами.");
        Selection.Sel sel = Selection.complete(player.getUniqueID(), com.ecoserv.build.BuildManager.TOOL, player.dimension);
        ui.note(sel == null ? Ui.YELLOW : Ui.BLUE, sel == null ? "Нет выделения стройки." : "Выделено: " + sel.describe());
        ui.field("Снять модель", "Имя: латиница, цифры, _ — Enter", newKey, v -> {
            String key = ContentNet.clean(v);
            Selection.Sel s = Selection.complete(player.getUniqueID(), com.ecoserv.build.BuildManager.TOOL, player.dimension);
            if (s == null) {
                throw new UiError("Сначала выдели постройку топором стройки.");
            }
            net.minecraft.util.math.BlockPos a = s.min();
            net.minecraft.util.math.BlockPos c = s.max();
            if (c.getX() - a.getX() >= 16 || c.getY() - a.getY() >= 16 || c.getZ() - a.getZ() >= 16) {
                throw new UiError("Не больше 16 блоков по каждой стороне.");
            }
            if (key.isEmpty()) {
                throw new UiError("Нужно имя.");
            }
            ContentNet.capture(player, key, a, c);
            toast("Снимаю модель…");
        }).placeholder("statue").max(32);
        for (final String k : b().models.keySet()) {
            ui.row(new ItemStack(Items.ARMOR_STAND), "&f" + k).line("&8" + b().models.get(k).length() / 1024 + " КБ")
                    .button("Блок с ней", Ui.GREEN, () -> {
                        int slot = b().freeBlock();
                        if (slot < 0) {
                            throw new UiError("Все слоты блоков заняты.");
                        }
                        ContentPack.BlockDef n = new ContentPack.BlockDef();
                        n.slot = slot;
                        n.shape = "model";
                        n.model = k;
                        n.name = k;
                        b().blocks.add(n);
                        save();
                        tab = Tab.BLOCKS;
                        block = slot;
                    })
                    .button("✕", Ui.RED, () -> {
                        b().models.remove(k);
                        save();
                    }).confirm("Удалить модель «" + k + "»?");
        }
    }

    // --- буфер -------------------------------------------------------------------------------------------

    private void buffer(Ui ui) {
        boolean pending = CustomContent.pendingChanges() > 0;
        ui.note(pending ? Ui.YELLOW : Ui.GREEN, pending ? "В буфере есть изменения." : "Буфер совпадает с тем, что в игре.");
        ui.text("&7Всё созданное копится здесь. Применить можно сразу — у всех игроков подгрузятся текстуры и модели"
                + " (пара секунд подвисания), или оставить: при перезапуске сервера буфер применится сам.");
        ui.text("&7В буфере: блоков " + b().blocks.size() + ", предметов " + b().items.size() + ", жидкостей " + b().fluids.size()
                + ", текстур " + b().textures.size() + ", моделей " + b().models.size() + ".");
        ui.buttons().add("Применить сейчас", Ui.GREEN, () -> {
            CustomContent.applyNow();
            toast("Применено — игроки получат контент.");
        }).disabled(!pending).confirm("Применить буфер? У всех игроков обновятся ресурсы.")
                .add("Отменить изменения", Ui.RED, CustomContent::discard).disabled(!pending)
                .confirm("Вернуть буфер к тому, что сейчас в игре?");
        ui.note(Ui.BLUE, "Игроки без мода свои блоки увидят пустыми — для своего контента мод нужен у всех.");
    }
}

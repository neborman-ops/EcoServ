package com.ecoserv.region;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiError;
import com.ecoserv.ui.UiIcons;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Настройки особых порталов (1.9.0).
 * <ul>
 * <li>«Настройка для всех» — внизу списка порталов: значения по умолчанию для всех чёрных дыр / монолитов;</li>
 * <li>у конкретного портала (вкладка «Эффекты» в его меню, {@link #build}) — те же настройки, но только для него
 * (↺ — вернуть «как у всех»), плюс раздел «Вид»: цвет, диск, размер, что внутри.</li>
 * </ul>
 */
public class PortalFxScreen extends UiScreen {

    private String style;

    public PortalFxScreen(EntityPlayerMP player, String style) {
        super(player);
        this.style = Region.STYLE_MONOLITH.equals(style) ? Region.STYLE_MONOLITH : Region.STYLE_BLACKHOLE;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    @Override
    protected void build(Ui ui) {
        ui.back(() -> new com.ecoserv.subworld.menu.WorldsScreen(player, com.ecoserv.subworld.menu.WorldsScreen.Tab.PORTALS).open());
        ui.title("Настройка для всех").subtitle(styleTitle(style)).icon(UiIcons.glitch());
        ui.tab("Чёрные дыры", UiIcons.glitch(), Region.STYLE_BLACKHOLE.equals(style), () -> style = Region.STYLE_BLACKHOLE);
        ui.tab("Монолиты", UiIcons.portal(), Region.STYLE_MONOLITH.equals(style), () -> style = Region.STYLE_MONOLITH);
        ui.sideLabel("&8Действия");
        ui.sideButton("Сбросить всё", new ItemStack(Blocks.TNT), Ui.RED, () -> {
            HoleParams.resetGlobal(style);
            toast("Вернул значения по умолчанию.");
        }).confirm("Вернуть все настройки «" + styleTitle(style) + "» по умолчанию? Свои настройки отдельных порталов останутся.");
        ui.note(Ui.BLUE, "Это значения для всех порталов этого вида. У любого портала можно поменять их только для него: "
                + "меню портала → вкладка «Эффекты».");
        build(ui, this, style, null);
    }

    static String styleTitle(String style) {
        return Region.STYLE_MONOLITH.equals(style) ? "Монолиты" : "Чёрные дыры";
    }

    /**
     * Строки настроек. region == null — «для всех» данного вида; иначе — свои значения портала (с ↺).
     */
    public static void build(Ui ui, UiScreen screen, String style, @Nullable final Region region) {
        section(ui, screen, style, region, HoleParams.Section.EFFECTS,
                region == null ? "Эффекты" : "Эффекты — только для этого портала");
        section(ui, screen, style, region, HoleParams.Section.LOOK,
                region == null ? "Вид по умолчанию" : "Вид: цвет, диск, размер, что внутри");
        if (region != null) {
            ui.buttons().add("Всё как у всех", Ui.GREY, () -> {
                region.fx = null;
                RegionManager.changed();
            }).disabled(region.fx == null || region.fx.isEmpty()).confirm("Убрать все свои настройки этого портала?");
        }
    }

    private static void section(Ui ui, UiScreen screen, final String style, @Nullable final Region region,
                                HoleParams.Section section, String header) {
        List<HoleParams.Param> params = HoleParams.params(style, section);
        if (params.isEmpty()) {
            return;
        }
        ui.header(header);
        boolean diskOn = region != null ? HoleParams.flag(region, HoleParams.DISK) : HoleParams.global(style, HoleParams.DISK) != 0.0D;
        int inside = (int) (region != null ? HoleParams.get(region, HoleParams.INSIDE) : HoleParams.global(style, HoleParams.INSIDE));
        for (final HoleParams.Param p : params) {
            // настройки диска — только когда он включён; цвет внутри — когда выбран «свой цвет»
            if (p.key.startsWith("disk_") && !diskOn) {
                continue;
            }
            if (p == HoleParams.INSIDE_COLOR && inside != 2) {
                continue;
            }
            if (p == HoleParams.PULL_POWER && (region != null ? !HoleParams.flag(region, HoleParams.PULL)
                    : HoleParams.global(style, HoleParams.PULL) == 0.0D)) {
                continue;
            }
            final double value = region != null ? HoleParams.get(region, p) : HoleParams.global(style, p);
            final boolean own = region != null ? HoleParams.own(region, p) : HoleParams.globalChanged(style, p);
            String label = p.title + (own ? " &b•" : "");
            String desc = p.desc;
            if (region != null && !own) {
                desc = (desc.isEmpty() ? "" : desc + ". ") + "&8Как у всех: " + p.format(HoleParams.global(style, p));
            }
            String resetTip = region != null ? "Вернуть «как у всех» (" + p.format(HoleParams.global(style, p)) + ")"
                    : "Вернуть по умолчанию (" + p.format(p.def(style)) + ")";
            Ui.Click reset = () -> set(style, region, p, null);
            switch (p.type) {
                case BOOL: {
                    Ui.Prop prop = ui.toggle(label, desc, value != 0.0D, on -> set(style, region, p, on ? 1.0D : 0.0D));
                    if (own) {
                        prop.reset(resetTip, reset);
                    }
                    break;
                }
                case COLOR: {
                    Ui.Field f = ui.field(label, desc, Ui.hex((int) value), text -> {
                        Integer c = Ui.parseColor(text);
                        if (c == null) {
                            throw new UiError("Цвет в виде #RRGGBB, например #A6BFFF.");
                        }
                        set(style, region, p, (double) c);
                    }).picker(Ui.PICK_COLOR).max(7);
                    if (own) {
                        f.reset(resetTip, reset);
                    }
                    break;
                }
                case CHOICE: {
                    final List<String[]> choices = p.choices;
                    Ui.Select sel = ui.select(label, desc, Integer.toString((int) Math.round(value)), v -> {
                        try {
                            set(style, region, p, (double) Integer.parseInt(v));
                        } catch (NumberFormatException e) {
                            throw new UiError("Не понял вариант.");
                        }
                    });
                    for (int i = 0; i < choices.size(); i++) {
                        sel.option(Integer.toString(i), choices.get(i)[1]);
                    }
                    if (own) {
                        sel.reset(resetTip, reset);
                    }
                    break;
                }
                default: {
                    String unit = p.type == HoleParams.Type.PERCENT ? ", %" : "";
                    Ui.Field f = ui.number(label + unit, desc, Math.round(value), (long) p.min, (long) p.max,
                            v -> set(style, region, p, (double) v));
                    if (own) {
                        f.reset(resetTip, reset);
                    }
                }
            }
        }
    }

    private static void set(String style, @Nullable Region region, HoleParams.Param p, @Nullable Double v) {
        if (region == null) {
            HoleParams.setGlobal(style, p, v);
        } else {
            HoleParams.setOwn(region, p, v);
            RegionManager.changed();
        }
    }
}

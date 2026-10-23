package com.ecoserv.lighting;

import com.ecoserv.ui.Ui;
import com.ecoserv.ui.UiScreen;
import com.ecoserv.ui.UiUtil;
import com.ecoserv.util.Perms;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Окно освещения мира — как свойства Lighting в Roblox Studio: те же разделы и параметры (в скобках —
 * английское имя из Roblox). Изменения видны сразу у всех в этом мире.
 */
public class LightingScreen extends UiScreen {

    private enum Tab {
        LIGHTING("Lighting", "Освещение", "minecraft:glowstone"),
        ATMOSPHERE("Atmosphere", "Атмосфера", "minecraft:glass"),
        SKY("Sky", "Небо", "minecraft:feather"),
        BLOOM("Bloom", "Свечение", "minecraft:glowstone_dust"),
        BLUR("BlurEffect", "Размытие", "minecraft:ghast_tear"),
        CC("ColorCorrection", "Цветокоррекция", "minecraft:dye@1"),
        DOF("DepthOfField", "Глубина резкости", "minecraft:ender_eye"),
        RAYS("SunRays", "Солнечные лучи", "minecraft:double_plant"),
        CLOUDS("Clouds", "Облака", "minecraft:snowball"),
        LIGHTS("PointLight…", "Источники света", "minecraft:torch"),
        PRESETS("", "Пресеты", "minecraft:book");

        final String roblox;
        final String title;
        final String icon;

        Tab(String roblox, String title, String icon) {
            this.roblox = roblox;
            this.title = title;
            this.icon = icon;
        }
    }

    private Tab tab = Tab.LIGHTING;
    private int dim;
    @Nullable
    private String light;
    private String presetName = "";

    public LightingScreen(EntityPlayerMP player) {
        super(player);
        dim = player.dimension;
    }

    public LightingScreen light(String id) {
        tab = Tab.LIGHTS;
        light = id;
        return this;
    }

    @Override
    protected boolean canView() {
        return Perms.isAdmin(player);
    }

    private LightingProfile p() {
        return LightingManager.profile(dim);
    }

    private void ch() {
        LightingManager.changed(dim);
    }

    private static String n(String ru, String en) {
        return ru + " &8" + en;
    }

    @Override
    protected void build(Ui ui) {
        final LightingProfile p = p();
        ui.title("Освещение мира").subtitle("&7" + com.ecoserv.world.WorldNames.nameOf(dim)
                + (p.enabled ? " &a· включено" : " &8· выключено")).icon(new ItemStack(Blocks.GLOWSTONE));
        for (Tab t : Tab.values()) {
            final Tab tt = t;
            ui.tab(t.title, UiUtil.icon(t.icon), tab == t, () -> {
                tab = tt;
                light = null;
            });
        }
        ui.sideLabel("&8Быстро");
        ui.sideButton(p.enabled ? "Выключить всё" : "Включить", new ItemStack(p.enabled ? Blocks.REDSTONE_LAMP : Blocks.GLOWSTONE),
                p.enabled ? Ui.YELLOW : Ui.GREEN, () -> {
                    p.enabled = !p.enabled;
                    ch();
                });
        if (!p.enabled && tab != Tab.PRESETS && tab != Tab.LIGHTS) {
            ui.note(Ui.YELLOW, "Освещение мира выключено — включи кнопкой слева, чтобы настройки заработали.");
        }
        switch (tab) {
            case ATMOSPHERE:
                ui.toggle(n("Атмосфера", "Atmosphere"), "Дымка вдали, свечение у солнца", p.atmosphere, on -> {
                    p.atmosphere = on;
                    ch();
                });
                ui.slider(n("Плотность", "Density"), "Как густо воздух", p.atmDensity, 0, 1, 0.01, "", v -> {
                    p.atmDensity = v;
                    ch();
                });
                ui.slider(n("Смещение", "Offset"), "Где начинается дымка", p.atmOffset, 0, 1, 0.01, "", v -> {
                    p.atmOffset = v;
                    ch();
                });
                ui.color(n("Цвет", "Color"), "", p.atmColor, v -> {
                    p.atmColor = (int) v;
                    ch();
                });
                ui.color(n("Затухание", "Decay"), "Цвет вдали от солнца", p.atmDecay, v -> {
                    p.atmDecay = (int) v;
                    ch();
                });
                ui.slider(n("Блик", "Glare"), "Свечение вокруг солнца", p.atmGlare, 0, 10, 0.1, "", v -> {
                    p.atmGlare = v;
                    ch();
                });
                ui.slider(n("Дымка", "Haze"), "Мутность у горизонта", p.atmHaze, 0, 10, 0.1, "", v -> {
                    p.atmHaze = v;
                    ch();
                });
                break;
            case SKY:
                ui.toggle(n("Своё небо", "Sky"), "Солнце, луна и звёзды по настройкам ниже", p.customSky, on -> {
                    p.customSky = on;
                    ch();
                });
                ui.toggle(n("Солнце и луна", "CelestialBodiesShown"), "", p.celestialBodiesShown, on -> {
                    p.celestialBodiesShown = on;
                    ch();
                });
                ui.slider(n("Размер солнца", "SunAngularSize"), "Градусы", p.sunAngularSize, 0, 60, 1, "°", v -> {
                    p.sunAngularSize = v;
                    ch();
                });
                ui.slider(n("Размер луны", "MoonAngularSize"), "Градусы", p.moonAngularSize, 0, 60, 1, "°", v -> {
                    p.moonAngularSize = v;
                    ch();
                });
                ui.slider(n("Звёзд", "StarCount"), "", p.starCount, 0, 5000, 50, "", v -> {
                    p.starCount = (int) v;
                    ch();
                });
                ui.color(n("Цвет неба вверху", "Top"), "Днём; ночью темнеет сам", p.skyTop, v -> {
                    p.skyTop = (int) v;
                    ch();
                });
                ui.color(n("Цвет у горизонта", "Horizon"), "", p.skyHorizon, v -> {
                    p.skyHorizon = (int) v;
                    ch();
                });
                break;
            case BLOOM:
                ui.toggle(n("Свечение", "Bloom"), "Яркое расплывается сиянием", p.bloom, on -> {
                    p.bloom = on;
                    ch();
                });
                ui.slider(n("Сила", "Intensity"), "", p.bloomIntensity, 0, 1, 0.01, "", v -> {
                    p.bloomIntensity = v;
                    ch();
                });
                ui.slider(n("Размер", "Size"), "", p.bloomSize, 0, 56, 1, "", v -> {
                    p.bloomSize = v;
                    ch();
                });
                ui.slider(n("Порог", "Threshold"), "Меньше — светится больше всего", p.bloomThreshold, 0, 4, 0.05, "", v -> {
                    p.bloomThreshold = v;
                    ch();
                });
                break;
            case BLUR:
                ui.toggle(n("Размытие", "BlurEffect"), "Всё размыто (меню, сон, оглушение)", p.blur, on -> {
                    p.blur = on;
                    ch();
                });
                ui.slider(n("Размер", "Size"), "", p.blurSize, 0, 56, 1, "", v -> {
                    p.blurSize = v;
                    ch();
                });
                break;
            case CC:
                ui.toggle(n("Цветокоррекция", "ColorCorrection"), "", p.colorCorrection, on -> {
                    p.colorCorrection = on;
                    ch();
                });
                ui.slider(n("Яркость", "Brightness"), "", p.ccBrightness, -1, 1, 0.01, "", v -> {
                    p.ccBrightness = v;
                    ch();
                });
                ui.slider(n("Контраст", "Contrast"), "", p.ccContrast, -1, 1, 0.01, "", v -> {
                    p.ccContrast = v;
                    ch();
                });
                ui.slider(n("Насыщенность", "Saturation"), "-1 — чёрно-белое", p.ccSaturation, -1, 1, 0.01, "", v -> {
                    p.ccSaturation = v;
                    ch();
                });
                ui.color(n("Оттенок", "TintColor"), "Белый — без оттенка", p.ccTint, v -> {
                    p.ccTint = (int) v;
                    ch();
                });
                break;
            case DOF:
                ui.toggle(n("Глубина резкости", "DepthOfField"), "Даль и/или близь размыты", p.depthOfField, on -> {
                    p.depthOfField = on;
                    ch();
                });
                ui.slider(n("Даль", "FarIntensity"), "", p.dofFarIntensity, 0, 1, 0.01, "", v -> {
                    p.dofFarIntensity = v;
                    ch();
                });
                ui.slider(n("Фокус", "FocusDistance"), "Доля дальности (0 — у глаз)", p.dofFocusDistance, 0, 1, 0.01, "", v -> {
                    p.dofFocusDistance = v;
                    ch();
                });
                ui.slider(n("Резкая зона", "InFocusRadius"), "Блоков вокруг фокуса", p.dofInFocusRadius, 0, 64, 1, "", v -> {
                    p.dofInFocusRadius = v;
                    ch();
                });
                ui.slider(n("Близь", "NearIntensity"), "", p.dofNearIntensity, 0, 1, 0.01, "", v -> {
                    p.dofNearIntensity = v;
                    ch();
                });
                break;
            case RAYS:
                ui.toggle(n("Солнечные лучи", "SunRays"), "Лучи от солнца сквозь листву и края", p.sunRays, on -> {
                    p.sunRays = on;
                    ch();
                });
                ui.slider(n("Сила", "Intensity"), "", p.sunRaysIntensity, 0, 1, 0.01, "", v -> {
                    p.sunRaysIntensity = v;
                    ch();
                });
                ui.slider(n("Разброс", "Spread"), "", p.sunRaysSpread, 0, 1, 0.01, "", v -> {
                    p.sunRaysSpread = v;
                    ch();
                });
                break;
            case CLOUDS:
                ui.toggle(n("Облака", "Clouds"), "Свои объёмные облака", p.clouds, on -> {
                    p.clouds = on;
                    ch();
                });
                ui.slider(n("Покрытие", "Cover"), "Сколько неба закрыто", p.cloudCover, 0, 1, 0.01, "", v -> {
                    p.cloudCover = v;
                    ch();
                });
                ui.slider(n("Плотность", "Density"), "Насколько непрозрачные", p.cloudDensity, 0, 1, 0.01, "", v -> {
                    p.cloudDensity = v;
                    ch();
                });
                ui.color(n("Цвет", "Color"), "", p.cloudColor, v -> {
                    p.cloudColor = (int) v;
                    ch();
                });
                ui.toggle("Убрать обычные облака", "Облака Minecraft", p.hideVanillaClouds, on -> {
                    p.hideVanillaClouds = on;
                    ch();
                });
                break;
            case LIGHTS:
                lights(ui);
                break;
            case PRESETS:
                presets(ui, p);
                break;
            default:
                lighting(ui, p);
        }
    }

    private void lighting(Ui ui, final LightingProfile p) {
        ui.header("Внешний вид", "&8Appearance");
        Ui.Select tech = ui.select(n("Технология", "Technology"), "Сколько эффектов считает видеокарта", p.technology, v -> {
            p.technology = v;
            ch();
        });
        tech.option("Compatibility", "Compatibility", "Только цвета света и туман, без шейдеров")
                .option("Voxel", "Voxel", "+ свечение, цветокоррекция, лучи")
                .option("ShadowMap", "ShadowMap", "+ цветные источники, атмосфера")
                .option("Future", "Future", "Всё: + глубина резкости, блики");
        ui.color(n("Свет в помещениях", "Ambient"), "Цвет и подсветка там, куда не достаёт небо", p.ambient, v -> {
            p.ambient = (int) v;
            ch();
        });
        ui.slider(n("Яркость солнца", "Brightness"), "", p.brightness, 0, 10, 0.05, "", v -> {
            p.brightness = v;
            ch();
        });
        ui.color(n("Сдвиг цвета: низ", "ColorShift_Bottom"), "Оттенок теней", p.colorShiftBottom, v -> {
            p.colorShiftBottom = (int) v;
            ch();
        });
        ui.color(n("Сдвиг цвета: верх", "ColorShift_Top"), "Оттенок освещённого солнцем", p.colorShiftTop, v -> {
            p.colorShiftTop = (int) v;
            ch();
        });
        ui.slider(n("Рассеянный от окружения", "EnvironmentDiffuseScale"), "Насколько цвет неба окрашивает мир",
                p.environmentDiffuseScale, 0, 1, 0.01, "", v -> {
                    p.environmentDiffuseScale = v;
                    ch();
                });
        ui.slider(n("Блики от окружения", "EnvironmentSpecularScale"), "Блеск ярких мест", p.environmentSpecularScale, 0, 1, 0.01, "", v -> {
            p.environmentSpecularScale = v;
            ch();
        });
        ui.toggle(n("Тени", "GlobalShadows"), "Выключи — темнота в тени пропадёт, всё освещено ровно", p.globalShadows, on -> {
            p.globalShadows = on;
            ch();
        });
        ui.color(n("Свет на улице", "OutdoorAmbient"), "Цвет и подсветка под открытым небом", p.outdoorAmbient, v -> {
            p.outdoorAmbient = (int) v;
            ch();
        });
        ui.slider(n("Мягкость теней", "ShadowSoftness"), "", p.shadowSoftness, 0, 1, 0.01, "", v -> {
            p.shadowSoftness = v;
            ch();
        });
        ui.header("Время", "&8Data");
        ui.toggle("Остановить время", "Всегда показывать время ниже (у игроков; на сервере время идёт)", p.lockTime, on -> {
            p.lockTime = on;
            ch();
        });
        ui.slider(n("Время суток", "ClockTime"), "Часы: 6 — рассвет, 12 — полдень, 18 — закат", p.clockTime, 0, 24, 0.1, " ч", v -> {
            p.clockTime = v;
            ch();
        });
        ui.slider(n("Широта", "GeographicLatitude"), "Наклон пути солнца (для своего неба)", p.geographicLatitude, -90, 90, 0.5, "°", v -> {
            p.geographicLatitude = v;
            ch();
        });
        ui.header("Экспозиция", "&8Exposure");
        ui.slider(n("Поправка экспозиции", "ExposureCompensation"), "", p.exposureCompensation, -3, 3, 0.05, "", v -> {
            p.exposureCompensation = v;
            ch();
        });
        ui.header("Туман", "&8Fog");
        ui.toggle("Свой туман", "Иначе — туман Minecraft", p.customFog, on -> {
            p.customFog = on;
            ch();
        });
        ui.color(n("Цвет тумана", "FogColor"), "", p.fogColor, v -> {
            p.fogColor = (int) v;
            ch();
        });
        ui.slider(n("Начало", "FogStart"), "Блоков", p.fogStart, 0, 512, 1, "", v -> {
            p.fogStart = v;
            ch();
        });
        ui.slider(n("Конец", "FogEnd"), "Блоков (512+ — без тумана)", Math.min(1024, p.fogEnd), 1, 1024, 1, "", v -> {
            p.fogEnd = v >= 1024 ? 100000 : v;
            ch();
        });
    }

    private void lights(Ui ui) {
        LightObject o = light == null ? null : LightingManager.light(light);
        if (o != null) {
            lightEditor(ui, o);
            return;
        }
        ui.text("&7Цветные источники света, как PointLight / SpotLight / SurfaceLight в Roblox. Ставятся здесь"
                + " или в редакторе сцены (/editor).");
        ui.buttons().add("+ Точечный (Point)", Ui.GREEN, () -> light = LightingManager.addLight(player.dimension, player.posX,
                player.posY + 1.5, player.posZ, "point").id)
                .add("+ Прожектор (Spot)", Ui.GREEN, () -> light = LightingManager.addLight(player.dimension, player.posX,
                        player.posY + 2.5, player.posZ, "spot").id)
                .add("+ Поверхность", Ui.GREEN, () -> light = LightingManager.addLight(player.dimension, player.posX,
                        player.posY + 2.5, player.posZ, "surface").id);
        for (final LightObject l : LightingManager.lights()) {
            if (l.dim != dim) {
                continue;
            }
            ui.row(new ItemStack(Blocks.TORCH), String.format("&f%s &8%s", kindTitle(l.kind), l.id))
                    .line(String.format("&7%.0f %.0f %.0f · дальность %.0f · яркость %.1f", l.x, l.y, l.z, l.range, l.brightness))
                    .onClick(() -> light = l.id)
                    .button("✎", Ui.BLUE, () -> light = l.id);
        }
    }

    static String kindTitle(String k) {
        return "spot".equals(k) ? "Прожектор" : "surface".equals(k) ? "Поверхность" : "Точечный";
    }

    private void lightEditor(Ui ui, final LightObject o) {
        ui.back(() -> light = null);
        ui.header(kindTitle(o.kind) + " " + o.id);
        ui.select("Вид", "", o.kind, v -> {
            o.kind = v;
            LightingManager.lightChanged(o);
        }).option("point", "Точечный (PointLight)", "Во все стороны", new ItemStack(Blocks.TORCH))
                .option("spot", "Прожектор (SpotLight)", "Конус в одну сторону", new ItemStack(Blocks.REDSTONE_LAMP))
                .option("surface", "Поверхность (SurfaceLight)", "Широко с одной стороны", new ItemStack(Blocks.SEA_LANTERN));
        ui.toggle("Включён", "", o.enabled, on -> {
            o.enabled = on;
            LightingManager.lightChanged(o);
        });
        ui.color(n("Цвет", "Color"), "", o.color, v -> {
            o.color = (int) v;
            LightingManager.lightChanged(o);
        });
        ui.slider(n("Яркость", "Brightness"), "", o.brightness, 0, 10, 0.05, "", v -> {
            o.brightness = v;
            LightingManager.lightChanged(o);
        });
        ui.slider(n("Дальность", "Range"), "Блоков", o.range, 1, 60, 0.5, "", v -> {
            o.range = v;
            LightingManager.lightChanged(o);
        });
        if (!"point".equals(o.kind)) {
            ui.slider(n("Угол", "Angle"), "", o.angle, 1, 180, 1, "°", v -> {
                o.angle = v;
                LightingManager.lightChanged(o);
            });
            ui.slider("Поворот", "Куда светит", o.yaw, -180, 180, 1, "°", v -> {
                o.yaw = (float) v;
                LightingManager.lightChanged(o);
            });
            ui.slider("Наклон", "90 — вниз, -90 — вверх", o.pitch, -90, 90, 1, "°", v -> {
                o.pitch = (float) v;
                LightingManager.lightChanged(o);
            });
        }
        ui.toggle("Свет блоков", "Ещё и обычный свет Minecraft (светло даже без шейдеров)", o.blockLight, on -> {
            o.blockLight = on;
            LightingManager.lightChanged(o);
        });
        ui.buttons().add("Перенести ко мне", Ui.BLUE, () -> {
            o.dim = player.dimension;
            o.x = player.posX;
            o.y = player.posY + 1.5;
            o.z = player.posZ;
            LightingManager.lightChanged(o);
        }).add("Удалить", Ui.RED, () -> {
            LightingManager.removeLight(o);
            light = null;
        });
    }

    private void presets(Ui ui, final LightingProfile p) {
        ui.text("&7Готовые настройки освещения. «Применить» заменяет все параметры этого мира.");
        for (final Map.Entry<String, LightingProfile> e : LightingManager.presets().entrySet()) {
            ui.row(new ItemStack(Items.PAINTING), "&f" + e.getKey())
                    .button("Применить", Ui.GREEN, () -> {
                        LightingProfile c = e.getValue().copy();
                        c.enabled = true;
                        LightingManager.setProfile(dim, c);
                        toast("Применено: " + e.getKey());
                    })
                    .button("✕", Ui.RED, () -> LightingManager.deletePreset(e.getKey())).confirm("Удалить пресет «" + e.getKey() + "»?");
        }
        ui.header("Сохранить текущее");
        ui.field("Имя", "", presetName, v -> presetName = v.trim()).placeholder("Мой вечер").max(40);
        ui.buttons().add("Сохранить пресет", Ui.GREEN, () -> {
            LightingManager.savePreset(presetName.isEmpty() ? "Пресет " + (LightingManager.presets().size() + 1) : presetName, p);
            presetName = "";
            toast("Сохранено.");
        }).add("Сбросить мир", Ui.RED, () -> LightingManager.setProfile(dim, new LightingProfile()))
                .confirm("Вернуть освещение Minecraft по умолчанию?");
    }
}

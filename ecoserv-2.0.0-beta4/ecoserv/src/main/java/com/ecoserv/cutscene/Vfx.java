package com.ecoserv.cutscene;

import com.ecoserv.v2.V2Actions;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;

/** Визуальные эффекты (2.0): фигуры из частиц (видят все рядом), тряска и вспышка экрана, молния. */
public final class Vfx {

    public static final String[][] SHAPES = {
            {"ring", "Кольцо", "Круг частиц вокруг точки", "minecraft:gold_nugget"},
            {"sphere", "Сфера", "Шар из частиц", "minecraft:snowball"},
            {"spiral", "Спираль", "Вихрь вверх", "minecraft:string"},
            {"pillar", "Столб", "Луч вверх", "minecraft:end_rod"},
            {"burst", "Взрыв", "Частицы во все стороны", "minecraft:fire_charge"},
            {"wave", "Волна", "Расходящееся кольцо по земле", "minecraft:prismarine_shard"},
            {"beam", "Луч к игроку", "Линия от точки до игрока", "minecraft:blaze_rod"},
    };

    private Vfx() {
    }

    /** shape, частицы, центр, радиус. */
    public static void shape(EntityPlayerMP p, String shape, EnumParticleTypes type, double cx, double cy, double cz, double r) {
        WorldServer w = p.getServerWorld();
        int n = (int) Math.max(12, Math.min(200, r * 24));
        switch (shape) {
            case "sphere":
                for (int i = 0; i < n * 2; i++) {
                    double u = Math.random() * 2 - 1;
                    double th = Math.random() * Math.PI * 2;
                    double s = Math.sqrt(1 - u * u);
                    w.spawnParticle(type, cx + r * s * Math.cos(th), cy + r * u, cz + r * s * Math.sin(th), 1, 0, 0, 0, 0.0D);
                }
                break;
            case "spiral":
                for (int i = 0; i < n * 2; i++) {
                    double a = i * 0.35D;
                    double h = i * 0.05D;
                    w.spawnParticle(type, cx + Math.cos(a) * r, cy + h, cz + Math.sin(a) * r, 1, 0, 0, 0, 0.0D);
                }
                break;
            case "pillar":
                for (int i = 0; i < n; i++) {
                    w.spawnParticle(type, cx, cy + i * 0.25D, cz, 2, 0.05D, 0.05D, 0.05D, 0.0D);
                }
                break;
            case "burst":
                w.spawnParticle(type, cx, cy, cz, n * 2, 0.1D, 0.1D, 0.1D, Math.max(0.1D, r * 0.15D));
                break;
            case "wave":
                for (int k = 1; k <= 4; k++) {
                    double rr = r * k / 4.0D;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n;
                        w.spawnParticle(type, cx + Math.cos(a) * rr, cy + 0.1D, cz + Math.sin(a) * rr, 1, 0, 0.02D, 0, 0.0D);
                    }
                }
                break;
            case "beam": {
                double dx = p.posX - cx;
                double dy = p.posY + 1.0D - cy;
                double dz = p.posZ - cz;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int steps = (int) Math.min(300, len * 4);
                for (int i = 0; i <= steps; i++) {
                    double t = i / (double) Math.max(1, steps);
                    w.spawnParticle(type, cx + dx * t, cy + dy * t, cz + dz * t, 1, 0, 0, 0, 0.0D);
                }
                break;
            }
            default:
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    w.spawnParticle(type, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0, 0, 0, 0.0D);
                }
        }
    }

    /** Тряска экрана: сила 1–10, тики. */
    public static void shake(EntityPlayerMP p, float strength, int ticks) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("t", "shake");
        d.setFloat("s", strength);
        d.setInteger("ticks", ticks);
        V2Actions.send(p, "fx", d);
    }

    /** Вспышка цветом (0xRRGGBB). */
    public static void flash(EntityPlayerMP p, int color, int ticks) {
        NBTTagCompound d = new NBTTagCompound();
        d.setString("t", "flash");
        d.setInteger("c", color);
        d.setInteger("ticks", ticks);
        V2Actions.send(p, "fx", d);
    }

    /** Молния без урона и пожара. */
    public static void lightning(EntityPlayerMP p, double x, double y, double z) {
        WorldServer w = p.getServerWorld();
        w.addWeatherEffect(new EntityLightningBolt(w, x, y, z, true));
    }
}

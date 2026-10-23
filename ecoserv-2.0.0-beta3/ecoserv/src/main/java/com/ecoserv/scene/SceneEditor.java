package com.ecoserv.scene;

import com.ecoserv.EcoServ;
import com.ecoserv.dev.BlockMarker;
import com.ecoserv.dev.DevManager;
import com.ecoserv.dev.DevZone;
import com.ecoserv.dev.TileLogic;
import com.ecoserv.dev.TileMarker;
import com.ecoserv.electric.ElectricTypes;
import com.ecoserv.electric.TileElectric;
import com.ecoserv.lighting.LightObject;
import com.ecoserv.lighting.LightingManager;
import com.ecoserv.npc.Hologram;
import com.ecoserv.npc.Npc;
import com.ecoserv.npc.NpcManager;
import com.ecoserv.panel.Panel;
import com.ecoserv.panel.PanelManager;
import com.ecoserv.util.ChatUtil;
import com.ecoserv.util.JsonUtil;
import com.ecoserv.util.Perms;
import com.ecoserv.v2.ModRegistry2;
import com.ecoserv.v2.V2Actions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Редактор сцены (2.0 beta3, как в Unity): сервер отдаёт объекты рядом (NPC, голограммы, 3D-панели, свет,
 * маркеры, блоки-программы, электроника, зоны), принимает перемещение/поворот/размер, добавление, удаление,
 * копию и отмену. Окно и камера — на клиенте (GuiSceneEditor).
 */
public final class SceneEditor {

    private static final double RADIUS = 160.0D;

    /** Шаг отмены. */
    private static final class Step {
        final Runnable undo;
        final Runnable redo;

        Step(Runnable undo, Runnable redo) {
            this.undo = undo;
            this.redo = redo;
        }
    }

    private static final Map<UUID, Deque<Step>> UNDO = new HashMap<UUID, Deque<Step>>();
    private static final Map<UUID, Deque<Step>> REDO = new HashMap<UUID, Deque<Step>>();

    private SceneEditor() {
    }

    public static void init() {
        V2Actions.on("ed_open", (p, d) -> open(p));
        V2Actions.on("ed_scene", (p, d) -> {
            if (Perms.isAdmin(p)) {
                send(p, false);
            }
        });
        V2Actions.on("ed_move", (p, d) -> guarded(p, () -> move(p, d)));
        V2Actions.on("ed_add", (p, d) -> guarded(p, () -> add(p, d)));
        V2Actions.on("ed_delete", (p, d) -> guarded(p, () -> delete(p, d.getString("type"), d.getString("id"))));
        V2Actions.on("ed_dup", (p, d) -> guarded(p, () -> duplicate(p, d.getString("type"), d.getString("id"))));
        V2Actions.on("ed_props", (p, d) -> guarded(p, () -> props(p, d.getString("type"), d.getString("id"))));
        V2Actions.on("ed_undo", (p, d) -> guarded(p, () -> undo(p, true)));
        V2Actions.on("ed_redo", (p, d) -> guarded(p, () -> undo(p, false)));
        V2Actions.on("ed_tool", (p, d) -> guarded(p, () -> tool(p, d.getString("tool"))));
        V2Actions.on("panel_click", (p, d) -> PanelManager.click(p, d.getString("id"), d.getInteger("i")));
    }

    private static void guarded(EntityPlayerMP p, Runnable r) {
        if (!Perms.isAdmin(p)) {
            return;
        }
        try {
            r.run();
        } catch (com.ecoserv.ui.UiError e) {
            ChatUtil.actionBar(p, "&c" + e.getMessage());
        } catch (Throwable t) {
            EcoServ.LOG.warn("Редактор сцены: {}", t.toString());
            ChatUtil.actionBar(p, "&cОшибка: " + t.getMessage());
        }
        send(p, false);
    }

    public static void open(EntityPlayerMP p) {
        if (!Perms.isAdmin(p)) {
            ChatUtil.send(p, "&cРедактор сцены — только для админов.");
            return;
        }
        com.ecoserv.ui.UiManager.close(p);
        send(p, true);
    }

    private static void record(EntityPlayerMP p, Runnable undo, Runnable redo) {
        UNDO.computeIfAbsent(p.getUniqueID(), k -> new ArrayDeque<Step>()).push(new Step(undo, redo));
        Deque<Step> u = UNDO.get(p.getUniqueID());
        while (u.size() > 50) {
            u.removeLast();
        }
        REDO.computeIfAbsent(p.getUniqueID(), k -> new ArrayDeque<Step>()).clear();
    }

    private static void undo(EntityPlayerMP p, boolean undo) {
        Deque<Step> from = (undo ? UNDO : REDO).computeIfAbsent(p.getUniqueID(), k -> new ArrayDeque<Step>());
        Deque<Step> to = (undo ? REDO : UNDO).computeIfAbsent(p.getUniqueID(), k -> new ArrayDeque<Step>());
        if (from.isEmpty()) {
            ChatUtil.actionBar(p, undo ? "&7Отменять нечего" : "&7Повторять нечего");
            return;
        }
        Step s = from.pop();
        (undo ? s.undo : s.redo).run();
        to.push(s);
    }

    // --- список объектов ---------------------------------------------------------------------------

    private static NBTTagCompound obj(String type, String id, String label, double x, double y, double z, float yaw, float pitch,
                                      double sx, double sy, double sz, int color, String caps) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("type", type);
        t.setString("id", id);
        t.setString("label", label);
        t.setDouble("x", x);
        t.setDouble("y", y);
        t.setDouble("z", z);
        t.setFloat("yaw", yaw);
        t.setFloat("pitch", pitch);
        t.setDouble("sx", sx);
        t.setDouble("sy", sy);
        t.setDouble("sz", sz);
        t.setInteger("c", color);
        t.setString("caps", caps);
        return t;
    }

    private static boolean near(EntityPlayerMP p, double x, double z) {
        double dx = x - p.posX;
        double dz = z - p.posZ;
        return dx * dx + dz * dz < RADIUS * RADIUS;
    }

    public static void send(EntityPlayerMP p, boolean open) {
        NBTTagCompound d = new NBTTagCompound();
        d.setBoolean("open", open);
        NBTTagList l = new NBTTagList();
        int dim = p.dimension;
        for (Npc n : NpcManager.all()) {
            if (n.dim == dim && near(p, n.x, n.z)) {
                l.appendTag(obj("npc", n.id, ChatUtil.strip(ChatUtil.color(n.name)) + " (" + n.id + ")", n.x, n.y, n.z, n.yaw, 0,
                        0.7, 1.9, 0.7, 0x40FF90, "move,yaw"));
            }
        }
        for (Hologram h : NpcManager.holograms()) {
            if (h.dim == dim && near(p, h.x, h.z)) {
                double hh = Math.max(0.3, h.lines.size() * h.spacing);
                l.appendTag(obj("holo", h.id, "Голограмма " + h.id, h.x, h.y, h.z, 0, 0, 1.2, hh, 0.2, 0xFFE070, "move"));
            }
        }
        for (Panel pn : PanelManager.all()) {
            if (pn.dim == dim && near(p, pn.x, pn.z)) {
                l.appendTag(obj("panel", pn.id, "Панель " + pn.id, pn.x, pn.y, pn.z, pn.yaw, pn.pitch, pn.width, pn.height, 0.1,
                        0x4E8CFF, "move,yaw,pitch,scale"));
            }
        }
        for (LightObject o : LightingManager.lights()) {
            if (o.dim == dim && near(p, o.x, o.z)) {
                l.appendTag(obj("light", o.id, ("spot".equals(o.kind) ? "Прожектор " : "surface".equals(o.kind) ? "Поверхность " : "Свет ") + o.id,
                        o.x, o.y, o.z, o.yaw, o.pitch, o.range, o.range, o.range, o.color,
                        "point".equals(o.kind) ? "move,scale" : "move,yaw,pitch,scale"));
            }
        }
        for (DevZone z : DevManager.zones()) {
            if (z.dim == dim && near(p, (z.minX + z.maxX) / 2.0, (z.minZ + z.maxZ) / 2.0)) {
                l.appendTag(obj("zone", z.name, "Зона " + z.name, (z.minX + z.maxX + 1) / 2.0, z.minY, (z.minZ + z.maxZ + 1) / 2.0, 0, 0,
                        z.maxX - z.minX + 1, z.maxY - z.minY + 1, z.maxZ - z.minZ + 1, z.global() ? 0xFF3030 : 0x3070FF, "move,scale"));
            }
        }
        WorldServer w = p.getServerWorld();
        for (TileEntity te : new ArrayList<TileEntity>(w.loadedTileEntityList)) {
            BlockPos b = te.getPos();
            if (!near(p, b.getX(), b.getZ())) {
                continue;
            }
            String id = b.getX() + "," + b.getY() + "," + b.getZ();
            if (te instanceof TileMarker) {
                int t = te.getBlockMetadata() & 15;
                TileMarker m = (TileMarker) te;
                l.appendTag(obj("block", id, BlockMarker.TITLES[t] + (m.name.isEmpty() ? "" : " «" + m.name + "»"), b.getX() + 0.5, b.getY(),
                        b.getZ() + 0.5, m.yaw, 0, 1, 1, 1, BlockMarker.COLORS[t], "move,yaw"));
            } else if (te instanceof TileLogic) {
                TileLogic lg = (TileLogic) te;
                l.appendTag(obj("block", id, "Программа " + (lg.name.isEmpty() ? id : lg.name), b.getX() + 0.5, b.getY(), b.getZ() + 0.5,
                        0, 0, 1, 1, 1, lg.global() ? 0xFF3030 : 0x3070FF, "move"));
            } else if (te instanceof TileElectric) {
                TileElectric e = (TileElectric) te;
                l.appendTag(obj("block", id, "⚡ " + ElectricTypes.TITLES[e.type()], b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0, 0,
                        1, 1, 1, ElectricTypes.COLORS[e.type()], "move"));
            }
        }
        for (com.ecoserv.cutscene.Cutscene c : com.ecoserv.cutscene.CutsceneManager.all()) {
            if (c.dim != dim) {
                continue;
            }
            for (int i = 0; i < c.points.size(); i++) {
                com.ecoserv.cutscene.Cutscene.Point pt = c.points.get(i);
                if (near(p, pt.x, pt.z)) {
                    l.appendTag(obj("campoint", c.id + "#" + i, "Камера «" + c.id + "» " + (i + 1), pt.x, pt.y - 0.25, pt.z, pt.yaw, pt.pitch,
                            0.5, 0.5, 0.5, 0xFFFFFF, "move,yaw,pitch"));
                }
            }
        }
        d.setTag("o", l);
        Deque<Step> u = UNDO.get(p.getUniqueID());
        Deque<Step> r = REDO.get(p.getUniqueID());
        d.setInteger("undo", u == null ? 0 : u.size());
        d.setInteger("redo", r == null ? 0 : r.size());
        V2Actions.send(p, "ed_scene", d);
    }

    // --- перемещение -------------------------------------------------------------------------------

    private static String json(Object o) {
        return JsonUtil.GSON.toJson(o);
    }

    private static void move(EntityPlayerMP p, NBTTagCompound d) {
        String type = d.getString("type");
        String id = d.getString("id");
        final double x = d.getDouble("x");
        final double y = d.getDouble("y");
        final double z = d.getDouble("z");
        final float yaw = d.getFloat("yaw");
        final float pitch = d.getFloat("pitch");
        final double sx = d.getDouble("sx");
        final double sy = d.getDouble("sy");
        final double sz = d.getDouble("sz");
        switch (type) {
            case "npc": {
                final Npc n = NpcManager.get(id);
                if (n == null) {
                    return;
                }
                final double[] old = {n.x, n.y, n.z, n.yaw};
                Runnable redo = () -> {
                    n.x = x;
                    n.y = y;
                    n.z = z;
                    n.yaw = yaw;
                    NpcManager.respawn(n);
                };
                record(p, () -> {
                    n.x = old[0];
                    n.y = old[1];
                    n.z = old[2];
                    n.yaw = (float) old[3];
                    NpcManager.respawn(n);
                }, redo);
                redo.run();
                break;
            }
            case "holo": {
                final Hologram h = NpcManager.hologram(id);
                if (h == null) {
                    return;
                }
                final double[] old = {h.x, h.y, h.z};
                Runnable redo = () -> {
                    h.x = x;
                    h.y = y;
                    h.z = z;
                    NpcManager.respawnHologram(h);
                };
                record(p, () -> {
                    h.x = old[0];
                    h.y = old[1];
                    h.z = old[2];
                    NpcManager.respawnHologram(h);
                }, redo);
                redo.run();
                break;
            }
            case "panel": {
                final Panel pn = PanelManager.get(id);
                if (pn == null) {
                    return;
                }
                final double[] old = {pn.x, pn.y, pn.z, pn.yaw, pn.pitch, pn.width, pn.height};
                Runnable redo = () -> {
                    pn.x = x;
                    pn.y = y;
                    pn.z = z;
                    pn.yaw = yaw;
                    pn.pitch = pitch;
                    if (sx > 0.1) {
                        pn.width = sx;
                    }
                    if (sy > 0.1) {
                        pn.height = sy;
                    }
                    PanelManager.changed(pn);
                };
                record(p, () -> {
                    pn.x = old[0];
                    pn.y = old[1];
                    pn.z = old[2];
                    pn.yaw = (float) old[3];
                    pn.pitch = (float) old[4];
                    pn.width = old[5];
                    pn.height = old[6];
                    PanelManager.changed(pn);
                }, redo);
                redo.run();
                break;
            }
            case "light": {
                final LightObject o = LightingManager.light(id);
                if (o == null) {
                    return;
                }
                final double[] old = {o.x, o.y, o.z, o.yaw, o.pitch, o.range};
                Runnable redo = () -> {
                    o.x = x;
                    o.y = y;
                    o.z = z;
                    o.yaw = yaw;
                    o.pitch = pitch;
                    if (sx > 0.5) {
                        o.range = Math.min(60, sx);
                    }
                    LightingManager.lightChanged(o);
                };
                record(p, () -> {
                    o.x = old[0];
                    o.y = old[1];
                    o.z = old[2];
                    o.yaw = (float) old[3];
                    o.pitch = (float) old[4];
                    o.range = old[5];
                    LightingManager.lightChanged(o);
                }, redo);
                redo.run();
                break;
            }
            case "zone": {
                final DevZone zn = DevManager.zone(id);
                if (zn == null) {
                    return;
                }
                final int[] old = {zn.minX, zn.minY, zn.minZ, zn.maxX, zn.maxY, zn.maxZ};
                final int w = (int) Math.max(1, Math.round(sx));
                final int h = (int) Math.max(1, Math.round(sy));
                final int dd = (int) Math.max(1, Math.round(sz));
                Runnable redo = () -> {
                    zn.minX = (int) Math.round(x - w / 2.0);
                    zn.minY = (int) Math.round(y);
                    zn.minZ = (int) Math.round(z - dd / 2.0);
                    zn.maxX = zn.minX + w - 1;
                    zn.maxY = zn.minY + h - 1;
                    zn.maxZ = zn.minZ + dd - 1;
                    DevManager.saveZones();
                    DevManager.syncAll();
                };
                record(p, () -> {
                    zn.minX = old[0];
                    zn.minY = old[1];
                    zn.minZ = old[2];
                    zn.maxX = old[3];
                    zn.maxY = old[4];
                    zn.maxZ = old[5];
                    DevManager.saveZones();
                    DevManager.syncAll();
                }, redo);
                redo.run();
                break;
            }
            case "block": {
                final WorldServer w = p.getServerWorld();
                final BlockPos from = parse(id);
                final BlockPos to = new BlockPos(Math.floor(x), Math.round(y), Math.floor(z));
                final float newYaw = yaw;
                if (from == null) {
                    return;
                }
                if (from.equals(to)) {
                    TileEntity te = w.getTileEntity(from);
                    if (te instanceof TileMarker) {
                        final TileMarker m = (TileMarker) te;
                        final float oldYaw = m.yaw;
                        Runnable redo = () -> {
                            m.yaw = newYaw;
                            m.changed();
                        };
                        record(p, () -> {
                            m.yaw = oldYaw;
                            m.changed();
                        }, redo);
                        redo.run();
                    }
                    return;
                }
                if (!w.isAirBlock(to)) {
                    throw new com.ecoserv.ui.UiError("Место занято блоком.");
                }
                Runnable redo = () -> moveBlock(w, from, to);
                record(p, () -> moveBlock(w, to, from), redo);
                redo.run();
                break;
            }
            case "campoint": {
                String[] parts = id.split("#");
                final com.ecoserv.cutscene.Cutscene c = com.ecoserv.cutscene.CutsceneManager.get(parts[0]);
                if (c == null) {
                    return;
                }
                final int i = Integer.parseInt(parts[1]);
                if (i < 0 || i >= c.points.size()) {
                    return;
                }
                final com.ecoserv.cutscene.Cutscene.Point pt = c.points.get(i);
                final double[] old = {pt.x, pt.y, pt.z, pt.yaw, pt.pitch};
                Runnable redo = () -> {
                    pt.x = x;
                    pt.y = y + 0.25;
                    pt.z = z;
                    pt.yaw = yaw;
                    pt.pitch = pitch;
                    com.ecoserv.cutscene.CutsceneManager.save();
                };
                record(p, () -> {
                    pt.x = old[0];
                    pt.y = old[1];
                    pt.z = old[2];
                    pt.yaw = (float) old[3];
                    pt.pitch = (float) old[4];
                    com.ecoserv.cutscene.CutsceneManager.save();
                }, redo);
                redo.run();
                break;
            }
            default:
        }
    }

    static BlockPos parse(String id) {
        try {
            String[] s = id.split(",");
            return new BlockPos(Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]));
        } catch (Exception e) {
            return null;
        }
    }

    /** Перенести блок с данными (маркер, программа, электроника) и поправить провода. */
    static void moveBlock(WorldServer w, BlockPos from, BlockPos to) {
        IBlockState st = w.getBlockState(from);
        TileEntity te = w.getTileEntity(from);
        NBTTagCompound nbt = te == null ? null : te.writeToNBT(new NBTTagCompound());
        w.removeTileEntity(from);
        w.setBlockState(from, Blocks.AIR.getDefaultState(), 3);
        w.setBlockState(to, st, 3);
        if (nbt != null) {
            TileEntity nt = w.getTileEntity(to);
            if (nt != null) {
                nbt.setInteger("x", to.getX());
                nbt.setInteger("y", to.getY());
                nbt.setInteger("z", to.getZ());
                nt.readFromNBT(nbt);
                nt.markDirty();
                w.notifyBlockUpdate(to, st, st, 3);
            }
        }
        for (TileEntity t : new ArrayList<TileEntity>(w.loadedTileEntityList)) {
            if (t instanceof TileElectric) {
                TileElectric e = (TileElectric) t;
                int i = e.outputs.indexOf(from);
                if (i >= 0) {
                    e.outputs.set(i, to);
                    e.changed();
                }
            }
        }
    }

    // --- добавление ------------------------------------------------------------------------------------

    private static void add(final EntityPlayerMP p, NBTTagCompound d) {
        String kind = d.getString("kind");
        double x = d.getDouble("x");
        double y = d.getDouble("y");
        double z = d.getDouble("z");
        float yaw = d.getFloat("yaw");
        final int dim = p.dimension;
        final WorldServer w = p.getServerWorld();
        if (kind.startsWith("npc")) {
            String type = kind.contains(":") ? kind.substring(4) : "minecraft:villager";
            String id = "npc_" + (NpcManager.all().size() + 1);
            while (NpcManager.get(id) != null) {
                id = id + "_";
            }
            final Npc n = NpcManager.create(p, id, type);
            n.x = x;
            n.y = y;
            n.z = z;
            n.yaw = yaw;
            NpcManager.respawn(n);
            final String json = json(n);
            record(p, () -> NpcManager.delete(n), () -> {
                Npc again = JsonUtil.GSON.fromJson(json, Npc.class);
                restoreNpc(p, again);
            });
            return;
        }
        switch (kind) {
            case "holo": {
                String id = "holo_" + (NpcManager.holograms().size() + 1);
                while (NpcManager.hologram(id) != null) {
                    id = id + "_";
                }
                final Hologram h = NpcManager.createHologram(p, id);
                h.x = x;
                h.y = y + 1.0;
                h.z = z;
                NpcManager.respawnHologram(h);
                record(p, () -> NpcManager.deleteHologram(h), () -> recreateHolo(p, h));
                return;
            }
            case "panel": {
                final Panel pn = PanelManager.create(p, "");
                pn.x = x;
                pn.y = y + 1.5;
                pn.z = z;
                pn.yaw = yaw + 180.0F;
                PanelManager.changed(pn);
                final String json = json(pn);
                record(p, () -> PanelManager.delete(pn), () -> restorePanel(json));
                return;
            }
            case "light:point":
            case "light:spot":
            case "light:surface": {
                final LightObject o = LightingManager.addLight(dim, x, y + 1.5, z, kind.substring(6));
                record(p, () -> LightingManager.removeLight(o), () -> restoreLight(json(o)));
                return;
            }
            default:
        }
        if (kind.startsWith("marker:") || "logic".equals(kind) || kind.startsWith("electric:")) {
            final BlockPos at = new BlockPos(Math.floor(x), Math.round(y), Math.floor(z));
            if (!w.isAirBlock(at)) {
                throw new com.ecoserv.ui.UiError("Место занято блоком.");
            }
            final IBlockState st;
            if (kind.startsWith("marker:")) {
                st = ModRegistry2.MARKER.getStateFromMeta(Integer.parseInt(kind.substring(7)));
            } else if (kind.startsWith("electric:")) {
                st = ModRegistry2.ELECTRIC.getStateFromMeta(Integer.parseInt(kind.substring(9)));
            } else {
                st = ModRegistry2.LOGIC.getDefaultState();
            }
            Runnable redo = () -> w.setBlockState(at, st, 3);
            record(p, () -> {
                w.removeTileEntity(at);
                w.setBlockState(at, Blocks.AIR.getDefaultState(), 3);
            }, redo);
            redo.run();
            return;
        }
        if ("zone".equals(kind)) {
            String name = "zone_" + (DevManager.zones().size() + 1);
            while (DevManager.zone(name) != null) {
                name = name + "_";
            }
            BlockPos a = new BlockPos(Math.floor(x) - 2, Math.round(y), Math.floor(z) - 2);
            final DevZone zn = DevManager.createZone(name, dim, a, a.add(4, 3, 4));
            DevManager.syncAll();
            record(p, () -> {
                DevManager.removeZone(zn);
                DevManager.syncAll();
            }, () -> {
                DevManager.createZone(zn.name, dim, new BlockPos(zn.minX, zn.minY, zn.minZ), new BlockPos(zn.maxX, zn.maxY, zn.maxZ));
                DevManager.syncAll();
            });
            return;
        }
        if (kind.startsWith("structure:")) {
            com.ecoserv.build.Structure s = com.ecoserv.build.BuildManager.structure(kind.substring(10));
            if (s != null) {
                com.ecoserv.build.BuildManager.place(p, s, new BlockPos(Math.floor(x), Math.round(y), Math.floor(z)),
                        net.minecraft.util.Rotation.NONE, false, "Структура " + s.name);
            }
            return;
        }
        if (kind.startsWith("cblock:")) {
            int slot = Integer.parseInt(kind.substring(7));
            final BlockPos at = new BlockPos(Math.floor(x), Math.round(y), Math.floor(z));
            final IBlockState st = com.ecoserv.content.CustomContent.blockState(slot);
            if (st == null) {
                throw new com.ecoserv.ui.UiError("Этого блока ещё нет — перезапусти сервер после создания.");
            }
            Runnable redo = () -> w.setBlockState(at, st, 3);
            record(p, () -> w.setBlockState(at, Blocks.AIR.getDefaultState(), 3), redo);
            redo.run();
        }
    }

    private static void restoreNpc(EntityPlayerMP p, Npc again) {
        Npc n = NpcManager.create(p, again.id, again.type);
        String json = json(again);
        Npc copy = JsonUtil.GSON.fromJson(json, Npc.class);
        n.name = copy.name;
        n.x = copy.x;
        n.y = copy.y;
        n.z = copy.z;
        n.yaw = copy.yaw;
        n.dialog = copy.dialog;
        n.equipment = copy.equipment;
        n.clickSteps = copy.clickSteps;
        n.profession = copy.profession;
        n.normalize();
        NpcManager.respawn(n);
    }

    private static void recreateHolo(EntityPlayerMP p, Hologram h) {
        Hologram n = NpcManager.createHologram(p, h.id);
        n.x = h.x;
        n.y = h.y;
        n.z = h.z;
        n.lines = new ArrayList<String>(h.lines);
        NpcManager.respawnHologram(n);
    }

    private static void restorePanel(String json) {
        Panel pn = JsonUtil.GSON.fromJson(json, Panel.class);
        pn.normalize();
        PanelManager.restore(pn);
    }

    private static void restoreLight(String json) {
        LightObject o = JsonUtil.GSON.fromJson(json, LightObject.class);
        LightingManager.restoreLight(o);
    }

    // --- удаление и копия ---------------------------------------------------------------------------------

    private static void delete(final EntityPlayerMP p, String type, String id) {
        switch (type) {
            case "npc": {
                final Npc n = NpcManager.get(id);
                if (n != null) {
                    final String json = json(n);
                    NpcManager.delete(n);
                    record(p, () -> restoreNpc(p, JsonUtil.GSON.fromJson(json, Npc.class)), () -> {
                        Npc x = NpcManager.get(n.id);
                        if (x != null) {
                            NpcManager.delete(x);
                        }
                    });
                }
                break;
            }
            case "holo": {
                final Hologram h = NpcManager.hologram(id);
                if (h != null) {
                    NpcManager.deleteHologram(h);
                    record(p, () -> recreateHolo(p, h), () -> {
                        Hologram x = NpcManager.hologram(h.id);
                        if (x != null) {
                            NpcManager.deleteHologram(x);
                        }
                    });
                }
                break;
            }
            case "panel": {
                final Panel pn = PanelManager.get(id);
                if (pn != null) {
                    final String json = json(pn);
                    PanelManager.delete(pn);
                    record(p, () -> restorePanel(json), () -> {
                        Panel x = PanelManager.get(pn.id);
                        if (x != null) {
                            PanelManager.delete(x);
                        }
                    });
                }
                break;
            }
            case "light": {
                final LightObject o = LightingManager.light(id);
                if (o != null) {
                    final String json = json(o);
                    LightingManager.removeLight(o);
                    record(p, () -> restoreLight(json), () -> {
                        LightObject x = LightingManager.light(o.id);
                        if (x != null) {
                            LightingManager.removeLight(x);
                        }
                    });
                }
                break;
            }
            case "zone": {
                final DevZone z = DevManager.zone(id);
                if (z != null) {
                    final int dim = z.dim;
                    DevManager.removeZone(z);
                    DevManager.syncAll();
                    record(p, () -> {
                        DevZone n = DevManager.createZone(z.name, dim, new BlockPos(z.minX, z.minY, z.minZ), new BlockPos(z.maxX, z.maxY, z.maxZ));
                        if (n != null) {
                            n.onEnter = z.onEnter;
                            n.onLeave = z.onLeave;
                            n.onStay = z.onStay;
                            n.scope = z.scope;
                            DevManager.saveZones();
                        }
                        DevManager.syncAll();
                    }, () -> {
                        DevZone x = DevManager.zone(z.name);
                        if (x != null) {
                            DevManager.removeZone(x);
                        }
                        DevManager.syncAll();
                    });
                }
                break;
            }
            case "block": {
                final WorldServer w = p.getServerWorld();
                final BlockPos at = parse(id);
                if (at == null) {
                    return;
                }
                final IBlockState st = w.getBlockState(at);
                TileEntity te = w.getTileEntity(at);
                final NBTTagCompound nbt = te == null ? null : te.writeToNBT(new NBTTagCompound());
                Runnable redo = () -> {
                    w.removeTileEntity(at);
                    w.setBlockState(at, Blocks.AIR.getDefaultState(), 3);
                };
                record(p, () -> {
                    w.setBlockState(at, st, 3);
                    TileEntity nt = w.getTileEntity(at);
                    if (nt != null && nbt != null) {
                        nt.readFromNBT(nbt);
                        nt.markDirty();
                    }
                }, redo);
                redo.run();
                break;
            }
            case "campoint": {
                String[] parts = id.split("#");
                com.ecoserv.cutscene.Cutscene c = com.ecoserv.cutscene.CutsceneManager.get(parts[0]);
                if (c != null) {
                    int i = Integer.parseInt(parts[1]);
                    if (i >= 0 && i < c.points.size()) {
                        c.points.remove(i);
                        com.ecoserv.cutscene.CutsceneManager.save();
                    }
                }
                break;
            }
            default:
        }
    }

    private static void duplicate(EntityPlayerMP p, String type, String id) {
        switch (type) {
            case "npc": {
                Npc n = NpcManager.get(id);
                if (n != null) {
                    Npc c = JsonUtil.GSON.fromJson(json(n), Npc.class);
                    String nid = n.id + "_2";
                    while (NpcManager.get(nid) != null) {
                        nid = nid + "_";
                    }
                    c.id = nid;
                    c.x += 1.0;
                    restoreNpc(p, c);
                    final String fid = nid;
                    record(p, () -> {
                        Npc x = NpcManager.get(fid);
                        if (x != null) {
                            NpcManager.delete(x);
                        }
                    }, () -> restoreNpc(p, c));
                }
                break;
            }
            case "panel": {
                Panel pn = PanelManager.get(id);
                if (pn != null) {
                    Panel c = JsonUtil.GSON.fromJson(json(pn), Panel.class);
                    String nid = pn.id + "_2";
                    while (PanelManager.get(nid) != null) {
                        nid = nid + "_";
                    }
                    c.id = nid;
                    c.x += 0.5;
                    c.normalize();
                    PanelManager.restore(c);
                    final String json = json(c);
                    record(p, () -> {
                        Panel x = PanelManager.get(c.id);
                        if (x != null) {
                            PanelManager.delete(x);
                        }
                    }, () -> restorePanel(json));
                }
                break;
            }
            case "light": {
                LightObject o = LightingManager.light(id);
                if (o != null) {
                    LightObject c = LightingManager.addLight(o.dim, o.x + 1, o.y, o.z, o.kind);
                    c.color = o.color;
                    c.brightness = o.brightness;
                    c.range = o.range;
                    c.angle = o.angle;
                    c.yaw = o.yaw;
                    c.pitch = o.pitch;
                    LightingManager.lightChanged(c);
                    final String json = json(c);
                    record(p, () -> LightingManager.removeLight(c), () -> restoreLight(json));
                }
                break;
            }
            case "holo": {
                Hologram h = NpcManager.hologram(id);
                if (h != null) {
                    String nid = h.id + "_2";
                    while (NpcManager.hologram(nid) != null) {
                        nid = nid + "_";
                    }
                    Hologram c = NpcManager.createHologram(p, nid);
                    c.x = h.x + 1;
                    c.y = h.y;
                    c.z = h.z;
                    c.lines = new ArrayList<String>(h.lines);
                    NpcManager.respawnHologram(c);
                    record(p, () -> NpcManager.deleteHologram(c), () -> recreateHolo(p, c));
                }
                break;
            }
            default:
                ChatUtil.actionBar(p, "&7Этот объект не копируется");
        }
    }

    // --- свойства и инструменты ---------------------------------------------------------------------

    private static void props(EntityPlayerMP p, String type, String id) {
        switch (type) {
            case "npc":
                new com.ecoserv.npc.NpcScreen(p, id).open();
                break;
            case "holo":
                com.ecoserv.npc.NpcScreen.hologram(p, id).open();
                break;
            case "panel":
                new com.ecoserv.panel.PanelScreen(p, id).open();
                break;
            case "light":
                new com.ecoserv.lighting.LightingScreen(p).light(id).open();
                break;
            case "zone":
                new com.ecoserv.dev.DevZoneScreen(p).editZone(id).open();
                break;
            case "campoint":
                new com.ecoserv.cutscene.CutsceneScreen(p, id.split("#")[0]).open();
                break;
            case "block": {
                BlockPos at = parse(id);
                if (at == null) {
                    return;
                }
                TileEntity te = p.world.getTileEntity(at);
                if (te instanceof TileMarker) {
                    new com.ecoserv.dev.MarkerScreen(p, at).open();
                } else if (te instanceof TileLogic) {
                    new com.ecoserv.dev.LogicScreen(p, at).open();
                } else if (te instanceof TileElectric) {
                    new com.ecoserv.electric.ElectricScreen(p, at).open();
                }
                break;
            }
            default:
        }
    }

    private static void tool(EntityPlayerMP p, String tool) {
        switch (tool) {
            case "lighting":
                new com.ecoserv.lighting.LightingScreen(p).open();
                break;
            case "content":
                new com.ecoserv.content.ContentScreen(p).open();
                break;
            case "build":
                new com.ecoserv.build.BuildScreen(p).open();
                break;
            case "dev":
                com.ecoserv.dev.DevScreen.open(p);
                break;
            default:
        }
    }
}

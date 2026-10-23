package com.ecoserv.client.v2;

import com.ecoserv.client.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Камера 2.0: катсцены (полёт по точкам), свободная камера, тряска, вспышка, кинополосы. Камера — невидимая
 * сущность только на клиенте, которой Minecraft «смотрит» вместо игрока.
 */
@SideOnly(Side.CLIENT)
public class CameraFx {

    private static final class Scene {
        double[][] p;
        int[] ticks;
        int[] hold;
        int[] arrive;
        boolean smooth;
        boolean ease;
        boolean hud;
        boolean bars;
        boolean skip;
        int t;
        int length;
    }

    private static Scene scene;
    private static boolean free;
    private static double fx;
    private static double fy;
    private static double fz;
    private static double pfx;
    private static double pfy;
    private static double pfz;
    private static EntityArmorStand cam;
    private static Entity savedView;
    private static int savedPerspective;
    private static float shakeStrength;
    private static int shakeTicks;
    private static int shakeTotal;
    private static int flashColor;
    private static int flashTicks;
    private static int flashTotal;
    private static float barsAlpha;
    // 2.0 beta3: камера редактора сцены (своя, не зависит от головы игрока)
    private static boolean editor;
    public static double ex;
    public static double ey;
    public static double ez;
    public static float eyaw;
    public static float epitch;
    // 2.0 beta4: камера сцены, на которую переключили игрока
    private static double[] view;
    private static double[] viewFrom;
    private static int viewTr;
    private static int viewT;
    private static boolean viewHud;
    private static boolean viewBars;
    private static boolean viewExit;
    private static int viewTrack = -1;
    private static String viewName = "";

    public static void register() {
        ClientV2.on("cutscene", CameraFx::start);
        ClientV2.on("cutscene_stop", d -> stopScene(false));
        ClientV2.on("freecam", d -> toggleFree());
        ClientV2.on("camview", CameraFx::startView);
        ClientV2.on("camview_off", d -> stopView(false));
        ClientV2.on("fx", d -> {
            String t = d.getString("t");
            if ("shake".equals(t) && ClientSettings.get().screenShake) {
                shakeStrength = Math.max(0.0F, Math.min(10.0F, d.getFloat("s")));
                shakeTicks = shakeTotal = Math.max(1, d.getInteger("ticks"));
            } else if ("flash".equals(t)) {
                flashColor = d.getInteger("c") & 0xFFFFFF;
                flashTicks = flashTotal = Math.max(1, d.getInteger("ticks"));
            }
        });
    }

    public static void reset() {
        view = null;
        scene = null;
        free = false;
        editor = false;
        cam = null;
        savedView = null;
        shakeTicks = 0;
        flashTicks = 0;
        barsAlpha = 0;
    }

    public static boolean active() {
        return scene != null || free || editor || view != null;
    }

    /** Игрок сейчас смотрит через камеру сцены. */
    public static boolean viewing() {
        return view != null;
    }

    private static void startView(NBTTagCompound d) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        if (mc.currentScreen instanceof GuiSceneEditor) {
            mc.displayGuiScreen(null);
        }
        double[] target = {d.getDouble("x"), d.getDouble("y"), d.getDouble("z"), d.getFloat("yaw"), d.getFloat("pitch"),
                d.getFloat("roll"), d.getFloat("fov")};
        int tr = d.getInteger("tr");
        if (view == null || tr > 0) {
            // откуда лететь: текущая точка зрения
            Entity e = mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity();
            viewFrom = view != null ? current() : new double[]{e.posX, e.posY + e.getEyeHeight(), e.posZ, e.rotationYaw, e.rotationPitch, 0,
                    mc.gameSettings.fovSetting};
            viewT = 0;
            viewTr = tr;
        }
        view = target;
        viewHud = d.getBoolean("hud");
        viewBars = d.getBoolean("bars");
        viewExit = d.getBoolean("exit");
        viewTrack = d.getInteger("track");
        viewName = d.getString("name");
        attach();
    }

    private static void stopView(boolean tellServer) {
        if (view == null) {
            return;
        }
        view = null;
        if (scene == null && !free && !editor) {
            detach();
        }
        if (tellServer) {
            ClientV2.send("camview_exit", new NBTTagCompound());
        }
    }

    /** Текущая поза камеры сцены (с переходом и слежением): x, y, z, yaw, pitch, roll, fov. */
    private static double[] current() {
        return current(1.0F);
    }

    private static double[] current(float pt) {
        double[] t = view.clone();
        Minecraft mc = Minecraft.getMinecraft();
        if (viewTrack >= 0 && mc.world != null) {
            Entity e = mc.world.getEntityByID(viewTrack);
            if (e != null) {
                double ex2 = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
                double ey2 = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt + e.getEyeHeight() * 0.85;
                double ez2 = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
                double dx = ex2 - t[0];
                double dy = ey2 - t[1];
                double dz = ez2 - t[2];
                t[3] = Math.toDegrees(Math.atan2(-dx, dz));
                t[4] = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            }
        }
        if (viewFrom != null && viewTr > 0 && viewT < viewTr) {
            double u = Math.min(1.0D, (viewT + pt) / viewTr);
            u = u * u * (3 - 2 * u);
            double[] o = new double[7];
            for (int i = 0; i < 7; i++) {
                double a = viewFrom[i];
                double b = t[i];
                if (i == 3) {
                    while (b - a > 180) {
                        b -= 360;
                    }
                    while (b - a < -180) {
                        b += 360;
                    }
                }
                o[i] = a + (b - a) * u;
            }
            return o;
        }
        return t;
    }

    public static boolean editor() {
        return editor;
    }

    /** Включить камеру редактора с места игрока. */
    public static void startEditor() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        if (!editor) {
            ex = mc.player.posX;
            ey = mc.player.posY + mc.player.getEyeHeight();
            ez = mc.player.posZ;
            eyaw = mc.player.rotationYaw;
            epitch = mc.player.rotationPitch;
        }
        editor = true;
        free = false;
        attach();
    }

    public static void stopEditor() {
        if (!editor) {
            return;
        }
        editor = false;
        if (scene == null && !free) {
            detach();
        }
    }

    // --- запуск/остановка -------------------------------------------------------------------------

    private static void start(NBTTagCompound d) {
        NBTTagList l = d.getTagList("p", 10);
        if (l.tagCount() < 2) {
            return;
        }
        Scene s = new Scene();
        int n = l.tagCount();
        s.p = new double[n][5];
        s.ticks = new int[n];
        s.hold = new int[n];
        s.arrive = new int[n];
        double prevYaw = 0;
        for (int i = 0; i < n; i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            double yaw = t.getFloat("yaw");
            if (i > 0) {
                // без рывка через 180°
                while (yaw - prevYaw > 180) {
                    yaw -= 360;
                }
                while (yaw - prevYaw < -180) {
                    yaw += 360;
                }
            }
            prevYaw = yaw;
            s.p[i] = new double[]{t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), yaw, t.getFloat("pitch")};
            s.ticks[i] = Math.max(1, t.getInteger("ticks"));
            s.hold[i] = Math.max(0, t.getInteger("hold"));
        }
        int at = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                at += s.hold[i - 1] + s.ticks[i];
            }
            s.arrive[i] = at;
        }
        s.length = at + s.hold[n - 1];
        s.smooth = d.getBoolean("smooth");
        s.ease = d.getBoolean("ease");
        s.hud = d.getBoolean("hud");
        s.bars = d.getBoolean("bars");
        s.skip = d.getBoolean("skip");
        if (free) {
            free = false;
        }
        scene = s;
        attach();
    }

    private static void attach() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        if (cam == null || cam.world != mc.world) {
            cam = new EntityArmorStand(mc.world);
            cam.setInvisible(true);
        }
        if (mc.getRenderViewEntity() != cam) {
            savedView = mc.getRenderViewEntity();
            savedPerspective = mc.gameSettings.thirdPersonView;
        }
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(cam);
    }

    private static void detach() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getRenderViewEntity() == cam) {
            mc.setRenderViewEntity(savedView != null && savedView.world == mc.world ? savedView : mc.player);
            mc.gameSettings.thirdPersonView = savedPerspective;
        }
    }

    private static void stopScene(boolean tellServer) {
        if (scene == null) {
            return;
        }
        scene = null;
        if (!free && !editor) {
            detach();
        }
        if (tellServer) {
            ClientV2.send("cutscene_end", new NBTTagCompound());
        }
    }

    private static void toggleFree() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        if (free) {
            free = false;
            if (scene == null && !editor) {
                detach();
            }
            return;
        }
        free = true;
        fx = pfx = mc.player.posX;
        fy = pfy = mc.player.posY + mc.player.getEyeHeight();
        fz = pfz = mc.player.posZ;
        attach();
    }

    // --- тики -------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            if (scene != null || free) {
                reset();
            }
            return;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (view != null) {
            viewT++;
            if (viewExit && mc.currentScreen == null && mc.gameSettings.keyBindSneak.isKeyDown() && viewT > 10) {
                stopView(true);
            }
        }
        boolean wantBars = scene != null && scene.bars || view != null && viewBars;
        barsAlpha = Math.max(0.0F, Math.min(1.0F, barsAlpha + (wantBars ? 0.1F : -0.1F)));
        if (scene != null) {
            scene.t++;
            if (scene.skip && mc.currentScreen == null && mc.gameSettings.keyBindSneak.isKeyDown() && scene.t > 10) {
                stopScene(true);
            } else if (scene.t >= scene.length) {
                stopScene(true);
            }
        }
        if (free) {
            pfx = fx;
            pfy = fy;
            pfz = fz;
            double speed = mc.gameSettings.keyBindSprint.isKeyDown() ? 1.0D : 0.4D;
            double yaw = Math.toRadians(mc.player.rotationYaw);
            double pitch = Math.toRadians(mc.player.rotationPitch);
            double fwd = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
            double str = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
            double up = (mc.gameSettings.keyBindJump.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindSneak.isKeyDown() ? 1 : 0);
            fx += (-Math.sin(yaw) * Math.cos(pitch) * fwd + Math.cos(yaw) * str) * speed;
            fz += (Math.cos(yaw) * Math.cos(pitch) * fwd + Math.sin(yaw) * str) * speed;
            fy += (-Math.sin(pitch) * fwd + up) * speed;
        }
        if (active() && mc.getRenderViewEntity() != cam) {
            attach();
        }
    }

    /** Игрок не ходит, пока камера летает. */
    @SubscribeEvent
    public void onInput(InputUpdateEvent event) {
        if (active()) {
            event.getMovementInput().moveForward = 0.0F;
            event.getMovementInput().moveStrafe = 0.0F;
            event.getMovementInput().jump = false;
            event.getMovementInput().sneak = false;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || cam == null || !active()) {
            return;
        }
        float pt = event.renderTickTime;
        double x;
        double y;
        double z;
        double yaw;
        double pitch;
        Minecraft mc = Minecraft.getMinecraft();
        if (editor && scene == null) {
            x = ex;
            y = ey;
            z = ez;
            yaw = eyaw;
            pitch = epitch;
        } else if (view != null && scene == null) {
            double[] v = current(pt);
            x = v[0];
            y = v[1];
            z = v[2];
            yaw = v[3];
            pitch = v[4];
        } else if (scene != null) {
            double[] v = sample(scene, Math.min(scene.length, scene.t + pt));
            x = v[0];
            y = v[1];
            z = v[2];
            yaw = v[3];
            pitch = v[4];
        } else {
            x = pfx + (fx - pfx) * pt;
            y = pfy + (fy - pfy) * pt;
            z = pfz + (fz - pfz) * pt;
            yaw = mc.player.prevRotationYaw + (mc.player.rotationYaw - mc.player.prevRotationYaw) * pt;
            pitch = mc.player.prevRotationPitch + (mc.player.rotationPitch - mc.player.prevRotationPitch) * pt;
        }
        double camY = y - cam.getEyeHeight();
        cam.posX = cam.prevPosX = cam.lastTickPosX = x;
        cam.posY = cam.prevPosY = cam.lastTickPosY = camY;
        cam.posZ = cam.prevPosZ = cam.lastTickPosZ = z;
        cam.rotationYaw = cam.prevRotationYaw = (float) yaw;
        cam.rotationPitch = cam.prevRotationPitch = (float) pitch;
        cam.rotationYawHead = cam.prevRotationYawHead = (float) yaw;
    }

    private static double ease(double u, boolean on) {
        return on ? u * u * (3 - 2 * u) : u;
    }

    private static double[] sample(Scene s, double t) {
        int n = s.p.length;
        int i = 0;
        while (i < n - 1 && t >= s.arrive[i + 1]) {
            i++;
        }
        if (i >= n - 1) {
            return s.p[n - 1];
        }
        double start = s.arrive[i] + s.hold[i];
        if (t <= start) {
            return s.p[i];
        }
        double u = ease((t - start) / s.ticks[i + 1], s.ease);
        double[] out = new double[5];
        for (int k = 0; k < 5; k++) {
            double p1 = s.p[i][k];
            double p2 = s.p[i + 1][k];
            if (s.smooth) {
                double p0 = s.p[Math.max(0, i - 1)][k];
                double p3 = s.p[Math.min(n - 1, i + 2)][k];
                out[k] = catmull(p0, p1, p2, p3, u);
            } else {
                out[k] = p1 + (p2 - p1) * u;
            }
        }
        return out;
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5D * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    // --- экран ------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        if (view != null && !editor && scene == null) {
            event.setFOV((float) current((float) event.getRenderPartialTicks())[6]);
        }
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup event) {
        if (view != null && !editor && scene == null) {
            event.setRoll(event.getRoll() + (float) current((float) event.getRenderPartialTicks())[5]);
        }
        if (shakeTicks <= 0) {
            return;
        }
        float k = shakeStrength * (shakeTicks / (float) shakeTotal);
        float time = (float) (System.nanoTime() / 1.0E9D);
        event.setYaw(event.getYaw() + (float) Math.sin(time * 47.0F) * k * 0.6F);
        event.setPitch(event.getPitch() + (float) Math.sin(time * 53.0F + 1.3F) * k * 0.6F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 37.0F + 2.1F) * k * 0.4F);
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        boolean hide = scene != null && scene.hud || view != null && viewHud && !editor;
        if (!hide) {
            return;
        }
        switch (event.getType()) {
            case ALL:
            case TEXT:
            case CHAT:
                return;
            default:
                event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        ScaledResolution r = event.getResolution();
        int w = r.getScaledWidth();
        int h = r.getScaledHeight();
        if (barsAlpha > 0.0F) {
            int bar = (int) (h * 0.11F * barsAlpha);
            Gui.drawRect(0, 0, w, bar, 0xFF000000);
            Gui.drawRect(0, h - bar, w, h, 0xFF000000);
        }
        if (view != null && !editor && scene == null) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
            mc.fontRenderer.drawStringWithShadow((blink ? "§c● " : "§8● ") + "§f" + viewName, 8, 8 + (int) (h * 0.11F * barsAlpha), 0xFFFFFF);
            if (viewExit) {
                String t = "§7Shift — выйти из камеры";
                mc.fontRenderer.drawStringWithShadow(t, w - mc.fontRenderer.getStringWidth(t) - 8, h - 14 - (int) (h * 0.11F * barsAlpha), 0xFFFFFF);
            }
        }
        if (flashTicks > 0) {
            float a = flashTicks / (float) flashTotal;
            if (!ClientSettings.get().flashes) {
                a *= 0.35F;
            }
            int alpha = (int) (Math.min(1.0F, a) * 220) & 0xFF;
            Gui.drawRect(0, 0, w, h, (alpha << 24) | flashColor);
        }
    }
}

package com.ecoserv.client;

import com.ecoserv.Ref;
import com.ecoserv.network.PacketHandler;
import com.ecoserv.network.packet.PacketSubworldAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Эффекты ГЛИТЧ-бездны (п. 21): глитч-текстуры по краям экрана («эмо»-текстура, её можно заменить
 * ресурспаком: assets/ecoserv/textures/gui/glitch_overlay.png), полноэкранный глитч-шейдер (если видеокарта
 * не умеет — фильтр эндермена) и окно ENGINE ERROR.
 * «Исчезновение после галочки»: эффекты гаснут через N секунд после ОК; иначе — сами, не дожидаясь окна.
 */
@SideOnly(Side.CLIENT)
public class ClientAbyss {

    private static final ResourceLocation OVERLAY = new ResourceLocation(Ref.MODID, "textures/gui/glitch_overlay.png");
    private static final Random RANDOM = new Random();

    private static boolean active;
    private static boolean waitForOk;
    private static boolean useShader;
    private static boolean useOverlay;
    private static boolean shaderLoaded;
    /** Полноэкранный глитч (ClientGlitchFx) вместо фильтра эндермена. */
    private static boolean useGlitch;
    private static long started;
    /** Когда эффекты погаснут совсем (0 — ждём ОК). */
    private static long endAt;
    private static long fadeFrom;
    /** Через сколько секунд после ОК гаснут эффекты. */
    private static int fadeSeconds;

    /** Пакет с сервера: показать эффекты и окно. */
    public static void start(NBTTagCompound data) {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        active = true;
        started = now;
        waitForOk = data.getBoolean("wait");
        useShader = data.getBoolean("shader");
        useOverlay = data.getBoolean("overlay");
        int effect = Math.max(1, data.getInteger("effect"));
        fadeSeconds = Math.max(0, data.getInteger("fade"));
        endAt = waitForOk ? 0L : now + effect * 1000L;
        fadeFrom = waitForOk ? 0L : Math.max(now, endAt - 1500L);
        // свой глитч-шейдер на весь экран; если видеокарта его не тянет — старый фильтр эндермена
        useGlitch = useShader && ClientGlitchFx.shadersAvailable();
        if (useShader && !useGlitch) {
            loadShader();
        }
        String[] text = pickText(data.getTagList("texts", 10));
        mc.displayGuiScreen(new GuiEngineError(text[0], text[1], text[2]));
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.ENTITY_ENDERMEN_SCREAM, 0.6F, 0.5F);
        }
    }

    /** Текст окна на языке игрока (или английский, или первый из заданных). */
    private static String[] pickText(NBTTagList texts) {
        String lang;
        try {
            lang = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        } catch (Exception e) {
            lang = "en_us";
        }
        NBTTagCompound pick = null;
        NBTTagCompound english = null;
        for (int i = 0; i < texts.tagCount(); i++) {
            NBTTagCompound t = texts.getCompoundTagAt(i);
            String code = t.getString("lang");
            if (code.equalsIgnoreCase(lang)) {
                pick = t;
            }
            if (code.equalsIgnoreCase("en_us")) {
                english = t;
            }
        }
        if (pick == null) {
            pick = english != null ? english : texts.tagCount() > 0 ? texts.getCompoundTagAt(0) : new NBTTagCompound();
        }
        String title = pick.getString("title");
        String button = pick.getString("button");
        return new String[]{title.isEmpty() ? "ENGINE ERROR" : title, pick.getString("text"), button.isEmpty() ? "OK" : button};
    }

    /** Игрок нажал ОК (или закрыл окно). */
    static void onOk() {
        PacketHandler.sendToServer(new PacketSubworldAction(PacketSubworldAction.ABYSS_OK, ""));
        if (!active || !waitForOk) {
            return;
        }
        long now = System.currentTimeMillis();
        endAt = now + Math.max(0, fadeSeconds) * 1000L;
        fadeFrom = now;
        if (fadeSeconds <= 0) {
            stop();
        }
    }

    private static void loadShader() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.entityRenderer == null) {
            return;
        }
        try {
            mc.entityRenderer.loadEntityShader(new EntityEnderman(mc.world));
            shaderLoaded = mc.entityRenderer.getShaderGroup() != null;
        } catch (Throwable t) {
            shaderLoaded = false;
        }
    }

    public static void stop() {
        ClientGlitchFx.screenGlitch = 0.0F;
        useGlitch = false;
        if (shaderLoaded) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.entityRenderer != null) {
                mc.entityRenderer.stopUseShader();
            }
            shaderLoaded = false;
        }
        active = false;
    }

    /** 1 — полная сила, 0 — погасли. */
    private static float strength() {
        long now = System.currentTimeMillis();
        if (endAt == 0L) {
            return Math.min(1.0F, (now - started) / 300.0F);
        }
        if (now >= endAt) {
            return 0.0F;
        }
        if (now < fadeFrom) {
            return 1.0F;
        }
        return Math.max(0.0F, (endAt - now) / (float) Math.max(1L, endAt - fadeFrom));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            stop();
            return;
        }
        if (endAt != 0L && System.currentTimeMillis() >= endAt) {
            stop();
            return;
        }
        if (useGlitch) {
            if (!ClientGlitchFx.shadersAvailable()) {
                // шейдер отказал на ходу — переходим на фильтр эндермена
                useGlitch = false;
                ClientGlitchFx.screenGlitch = 0.0F;
                loadShader();
            } else {
                // сила с «дыханием»: сбой то нарастает, то отпускает
                float pulse = 0.75F + 0.25F * (float) Math.sin(System.currentTimeMillis() / 140.0D);
                ClientGlitchFx.screenGlitch = strength() * pulse;
            }
            return;
        }
        // кто-то сбросил фильтр (смена вида камеры и т.п.) — вернуть
        if (useShader && (!shaderLoaded || mc.entityRenderer.getShaderGroup() == null)) {
            loadShader();
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (!active || !useOverlay || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        float alpha = strength();
        if (alpha <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = event.getResolution();
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableAlpha();
        // рамка из текстуры с дрожанием
        int jx = RANDOM.nextInt(5) - 2;
        int jy = RANDOM.nextInt(5) - 2;
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        mc.getTextureManager().bindTexture(OVERLAY);
        Gui.drawModalRectWithCustomSizedTexture(jx - 4, jy - 4, 0, 0, w + 8, h + 8, w + 8, h + 8);
        // «битые» полосы: сдвинутые по цвету горизонтальные линии
        int bars = 3 + RANDOM.nextInt(6);
        for (int i = 0; i < bars; i++) {
            int y = RANDOM.nextInt(Math.max(1, h));
            int bh = 1 + RANDOM.nextInt(3);
            int x0 = RANDOM.nextInt(Math.max(1, w / 2));
            int x1 = x0 + w / 4 + RANDOM.nextInt(Math.max(1, w / 2));
            int a = (int) (alpha * (90 + RANDOM.nextInt(100)));
            int color = RANDOM.nextBoolean() ? 0xFF1493 : RANDOM.nextBoolean() ? 0x00FFFF : 0x8A2BE2;
            Gui.drawRect(x0, y, x1, y + bh, (a << 24) | color);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}

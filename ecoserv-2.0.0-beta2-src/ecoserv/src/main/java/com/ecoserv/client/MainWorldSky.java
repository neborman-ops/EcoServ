package com.ecoserv.client;

import com.ecoserv.subworld.Subworld;
import com.ecoserv.subworld.SubworldDims;
import com.ecoserv.subworld.SubworldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Небо и туман ОБЫЧНЫХ миров (1.8.0), настроенные в меню мира — как у подмиров. У подмиров это делает их
 * провайдер, а у обычных миров провайдер чужой, поэтому небо подменяется на клиенте у текущего мира.
 * «Обычное небо» в аду так не включить (это решает провайдер ада) — там лучше «сплошной цвет».
 */
@SideOnly(Side.CLIENT)
public class MainWorldSky {

    private static final IRenderHandler NO_CLOUDS = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
        }
    };

    private WorldProvider applied;
    private String signature = "";

    private static String kind(int dim) {
        return dim == -1 ? Subworld.NETHER : dim == 1 ? Subworld.END : Subworld.NORMAL;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) {
            applied = null;
            signature = "";
            return;
        }
        WorldProvider provider = world.provider;
        int dim = provider.getDimension();
        SubworldView v = SubworldDims.clientView(dim);
        boolean ours = v != null && v.isMain() && !"default".equals(v.sky);
        String sig = ours ? v.sky + "|" + v.skyColor : "";
        if (provider == applied && sig.equals(signature)) {
            return;
        }
        if (applied == provider && !ours) {
            // настройку сняли — вернуть небо мира
            provider.setSkyRenderer(null);
            provider.setCloudRenderer(null);
        }
        if (ours) {
            provider.setSkyRenderer(SubworldSky.renderer(dim, kind(dim)));
            provider.setCloudRenderer(SubworldSky.hideClouds(dim, kind(dim)) ? NO_CLOUDS : null);
            applied = provider;
        } else {
            applied = null;
        }
        signature = sig;
    }

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.FogColors event) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }
        int dim = world.provider.getDimension();
        SubworldView v = SubworldDims.clientView(dim);
        if (v == null || !v.isMain() || ("default".equals(v.sky) && v.fogColor < 0)) {
            return;
        }
        Vec3d c = SubworldSky.fogColor(dim, kind(dim));
        if (c != null) {
            event.setRed((float) c.x);
            event.setGreen((float) c.y);
            event.setBlue((float) c.z);
        }
    }
}

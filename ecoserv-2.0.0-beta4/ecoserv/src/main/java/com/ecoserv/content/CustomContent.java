package com.ecoserv.content;

import com.ecoserv.EcoServ;
import com.ecoserv.util.DataStore;
import com.ecoserv.util.PlayerUtil;
import com.ecoserv.v2.ModRegistry2;
import com.ecoserv.v2.V2Actions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * Свой контент: активный набор (что сейчас в игре) и буфер (что появится после применения / перезапуска).
 * На сервере — world/ecoserv/content_active.dat и content_buffer.dat; у клиента — присланный сервером набор.
 */
public final class CustomContent {

    /** Что действует сейчас (на сервере — свой, на клиенте — от сервера). */
    private static volatile ContentPack active = new ContentPack();
    private static ContentPack buffer = new ContentPack();

    public static final ContentBlock[] BLOCKS = new ContentBlock[ContentPack.BLOCKS];
    public static final ContentItem[] ITEMS = new ContentItem[ContentPack.ITEMS];
    public static final ContentFluid[] FLUIDS = new ContentFluid[ContentPack.FLUIDS];
    public static final ContentFluidBlock[] FLUID_BLOCKS = new ContentFluidBlock[ContentPack.FLUIDS];

    private CustomContent() {
    }

    public static ContentPack active() {
        return active;
    }

    public static ContentPack buffer() {
        return buffer;
    }

    /** Клиент: набор пришёл от сервера (или из кэша). */
    public static void setActive(ContentPack p) {
        active = p;
        applyFluids();
    }

    @Nullable
    public static ContentPack.BlockDef blockDef(int slot) {
        return active.block(slot);
    }

    @Nullable
    public static ContentPack.ItemDef itemDef(int slot) {
        return active.item(slot);
    }

    @Nullable
    public static ContentPack.FluidDef fluidDef(int slot) {
        return active.fluid(slot);
    }

    @Nullable
    public static IBlockState blockState(int slot) {
        if (slot < 0 || slot >= BLOCKS.length || BLOCKS[slot] == null || active.block(slot) == null) {
            return null;
        }
        return BLOCKS[slot].getDefaultState();
    }

    // --- регистрация (FMLPreInit — жидкости, события реестра — блоки и предметы) ------------------------

    public static void registerFluids() {
        for (int i = 0; i < ContentPack.FLUIDS; i++) {
            ContentFluid f = new ContentFluid(i);
            net.minecraftforge.fluids.FluidRegistry.registerFluid(f);
            net.minecraftforge.fluids.FluidRegistry.addBucketForFluid(f);
            FLUIDS[i] = f;
        }
    }

    public static void createBlocks() {
        for (int i = 0; i < ContentPack.BLOCKS; i++) {
            BLOCKS[i] = new ContentBlock(i);
        }
        for (int i = 0; i < ContentPack.ITEMS; i++) {
            ITEMS[i] = new ContentItem(i);
        }
        for (int i = 0; i < ContentPack.FLUIDS; i++) {
            FLUID_BLOCKS[i] = new ContentFluidBlock(FLUIDS[i], i);
        }
    }

    /** Свойства жидкостей из набора. */
    static void applyFluids() {
        for (int i = 0; i < ContentPack.FLUIDS; i++) {
            ContentPack.FluidDef d = active.fluid(i);
            Fluid f = FLUIDS[i];
            if (f == null) {
                continue;
            }
            int lum = d == null ? 0 : Math.max(0, Math.min(15, d.luminosity));
            f.setLuminosity(lum);
            f.setDensity(d == null ? 1000 : d.density);
            f.setViscosity(d == null ? 1000 : Math.max(100, d.viscosity));
            f.setTemperature(d == null ? 300 : d.temperature);
            if (FLUID_BLOCKS[i] != null) {
                FLUID_BLOCKS[i].setDensity(d == null ? 1000 : d.density);
                FLUID_BLOCKS[i].setTickRate(Math.max(2, (d == null ? 1000 : d.viscosity) / 200));
            }
        }
    }

    // --- сервер: файлы ----------------------------------------------------------------------------

    private static File file(String name) {
        return DataStore.file(name);
    }

    private static ContentPack readFile(File f) {
        if (!f.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            return ContentPack.read(CompressedStreamTools.readCompressed(in));
        } catch (Throwable t) {
            EcoServ.LOG.warn("Свой контент: {} не прочитан — {}", f.getName(), t.toString());
            return null;
        }
    }

    private static void writeFile(File f, ContentPack p) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            CompressedStreamTools.writeCompressed(p.write(), out);
        } catch (Throwable t) {
            EcoServ.LOG.warn("Свой контент: {} не записан — {}", f.getName(), t.toString());
        }
    }

    /** Старт сервера: буфер становится активным («после перезапуска всё появляется»). */
    public static void load() {
        ContentPack a = readFile(file("content_active.dat"));
        ContentPack b = readFile(file("content_buffer.dat"));
        if (b != null) {
            a = b;
            writeFile(file("content_active.dat"), a);
        }
        if (a == null) {
            a = new ContentPack();
        }
        active = a;
        buffer = a.copy();
        applyFluids();
    }

    public static void saveBuffer() {
        writeFile(file("content_buffer.dat"), buffer);
    }

    public static int pendingChanges() {
        return buffer.hash().equals(active.hash()) ? 0 : 1;
    }

    /** Применить буфер сейчас: всем игрокам обновятся ресурсы. */
    public static void applyNow() {
        active = buffer.copy();
        writeFile(file("content_active.dat"), active);
        applyFluids();
        for (EntityPlayerMP p : PlayerUtil.allOnline()) {
            send(p);
        }
    }

    public static void discard() {
        buffer = active.copy();
        saveBuffer();
    }

    // --- рассылка ------------------------------------------------------------------------------------

    /** Набор по частям: заголовок, текстуры и модели по одной, конец. */
    public static void send(EntityPlayerMP p) {
        ContentPack a = active;
        NBTTagCompound begin = new NBTTagCompound();
        begin.setString("hash", a.hash());
        ContentPack defsOnly = a.copy();
        defsOnly.textures.clear();
        defsOnly.models.clear();
        begin.setTag("defs", defsOnly.write());
        V2Actions.send(p, "content_begin", begin);
        for (Map.Entry<String, byte[]> e : a.textures.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", e.getKey());
            t.setByteArray("png", e.getValue());
            V2Actions.send(p, "content_tex", t);
        }
        for (Map.Entry<String, String> e : a.models.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", e.getKey());
            t.setByteArray("gz", ContentPack.gzip(e.getValue()));
            V2Actions.send(p, "content_model", t);
        }
        V2Actions.send(p, "content_end", new NBTTagCompound());
    }
}

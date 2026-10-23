package com.ecoserv.client;

import com.ecoserv.EcoServ;
import com.ecoserv.region.BlackHoles;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.InflaterInputStream;

/**
 * Вид места назначения внутри портала (1.9.0): настоящие блоки с текстурами, светом и цветом биомов.
 * Данные приходят с сервера (BlackHoles.sample); распаковка и сборка геометрии идут в отдельном потоке,
 * как у обычных чанков, а в главном потоке готовые буферы только загружаются в списки отображения.
 */
@SideOnly(Side.CLIENT)
final class PortalScene implements IBlockAccess {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EcoServ portal view");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static final BlockRenderLayer[] LAYERS = BlockRenderLayer.values();

    final int version;
    int size;
    float offX;
    float offY;
    float offZ;
    int sky;
    boolean hasSky;
    private IBlockState[] palette = new IBlockState[0];
    private short[] blocks = new short[0];
    private byte[] light = new byte[0];
    private byte[] biomes = new byte[0];
    private final IBlockState stone = Blocks.STONE.getDefaultState();
    private final IBlockState air = Blocks.AIR.getDefaultState();

    /** Готовые буферы по слоям (из рабочего потока) и списки отображения (в главном). */
    private volatile BufferBuilder[] built;
    private final int[] lists = new int[LAYERS.length];
    private volatile boolean failed;
    private boolean uploaded;

    private PortalScene(int version) {
        this.version = version;
        for (int i = 0; i < lists.length; i++) {
            lists[i] = -1;
        }
    }

    /** Распаковать и собрать в фоне; null — данные не того формата. */
    static PortalScene start(int version, final byte[] data) {
        final PortalScene scene = new PortalScene(version);
        WORKER.submit(() -> {
            try {
                scene.decode(data);
                scene.build();
            } catch (Throwable t) {
                scene.failed = true;
                EcoServ.LOG.debug("Вид портала не собрался: {}", t.toString());
            }
        });
        return scene;
    }

    private void decode(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data), new java.util.zip.Inflater(), 64 * 1024));
        if (in.readInt() != BlackHoles.PREVIEW_MAGIC) {
            throw new IOException("другой формат вида");
        }
        int s = in.readInt();
        if (s <= 0 || s > 129) {
            throw new IOException("размер " + s);
        }
        size = s;
        offX = in.readFloat();
        offY = in.readFloat();
        offZ = in.readFloat();
        sky = in.readInt();
        hasSky = in.readBoolean();
        int pc = in.readInt();
        palette = new IBlockState[pc];
        for (int i = 0; i < pc; i++) {
            int id = in.readInt();
            IBlockState st = i == 0 ? air : i == 1 ? stone : Block.getStateById(id);
            palette[i] = st == null ? stone : st;
        }
        int n = s * s * s;
        blocks = new short[n];
        for (int i = 0; i < n; i++) {
            blocks[i] = in.readShort();
        }
        light = new byte[n];
        in.readFully(light);
        biomes = new byte[s * s];
        in.readFully(biomes);
        in.close();
    }

    private void build() {
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        BufferBuilder[] out = new BufferBuilder[LAYERS.length];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int s = size;
        try {
            for (int l = 0; l < LAYERS.length; l++) {
                BlockRenderLayer layer = LAYERS[l];
                ForgeHooksClient.setRenderLayer(layer);
                BufferBuilder b = new BufferBuilder(1 << 18);
                b.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                boolean any = false;
                for (int x = 0; x < s; x++) {
                    for (int y = 0; y < s; y++) {
                        for (int z = 0; z < s; z++) {
                            int idx = blocks[(x * s + y) * s + z];
                            if (idx <= 1 || idx >= palette.length) {
                                continue;
                            }
                            IBlockState st = palette[idx];
                            if (st.getRenderType() == EnumBlockRenderType.INVISIBLE
                                    || st.getRenderType() == EnumBlockRenderType.ENTITYBLOCK_ANIMATED
                                    || !st.getBlock().canRenderInLayer(st, layer)) {
                                continue;
                            }
                            pos.setPos(x, y, z);
                            try {
                                any |= dispatcher.renderBlock(st, pos, this, b);
                            } catch (Throwable t) {
                                // блок чужого мода, которому нужен настоящий мир, — пропускаем
                            }
                        }
                    }
                }
                b.finishDrawing();
                out[l] = any ? b : null;
            }
        } finally {
            ForgeHooksClient.setRenderLayer(null);
        }
        built = out;
        // больше не нужны: всё в буферах
        blocks = new short[0];
        palette = new IBlockState[0];
    }

    boolean failed() {
        return failed;
    }

    /** Есть ли что рисовать (загрузит буферы в видеокарту при первом вызове). */
    boolean ready() {
        if (uploaded) {
            return true;
        }
        BufferBuilder[] b = built;
        if (b == null) {
            return false;
        }
        WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();
        for (int i = 0; i < b.length; i++) {
            if (b[i] == null || b[i].getVertexCount() == 0) {
                continue;
            }
            lists[i] = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(lists[i], GL11.GL_COMPILE);
            uploader.draw(b[i]);
            GlStateManager.glEndList();
        }
        built = null;
        uploaded = true;
        return true;
    }

    /** Нарисовать слой (геометрия в координатах ячеек: 0..size). */
    void draw(int layer) {
        if (uploaded && lists[layer] >= 0) {
            GlStateManager.callList(lists[layer]);
        }
    }

    void delete() {
        for (int i = 0; i < lists.length; i++) {
            if (lists[i] >= 0) {
                GLAllocation.deleteDisplayLists(lists[i]);
                lists[i] = -1;
            }
        }
        built = null;
    }

    /** Свет в ячейке (небо, блоки) — для существ в окне. */
    int lightAt(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        if (light.length == 0 || ix < 0 || iy < 0 || iz < 0 || ix >= size || iy >= size || iz >= size) {
            return 15 << 20;
        }
        int v = light[(ix * size + iy) * size + iz] & 255;
        return (v >> 4) << 20 | (v & 15) << 4;
    }

    // --- IBlockAccess для рендера блоков ---------------------------------------------------------------

    private int index(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) {
            return -1;
        }
        return (x * size + y) * size + z;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        int i = index(pos);
        if (i < 0 || light.length == 0) {
            return 15 << 20 | Math.max(0, lightValue) << 4;
        }
        int v = light[i] & 255;
        int blockLight = Math.max(v & 15, lightValue);
        return (v >> 4) << 20 | blockLight << 4;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        int i = index(pos);
        if (i < 0) {
            // за краем куба — «камень»: внешние грани не рисуются, не видно стенок куба
            return stone;
        }
        if (blocks.length == 0) {
            return air;
        }
        int idx = blocks[i];
        return idx <= 0 || idx >= palette.length ? air : palette[idx];
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState st = getBlockState(pos);
        return st.getMaterial() == net.minecraft.block.material.Material.AIR;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        if (x < 0 || z < 0 || x >= size || z >= size || biomes.length == 0) {
            return Biomes.PLAINS;
        }
        return Biome.getBiome(biomes[x * size + z] & 255, Biomes.PLAINS);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        int i = index(pos);
        if (i < 0) {
            return _default;
        }
        IBlockState st = getBlockState(pos);
        return st.isSideSolid(this, pos, side);
    }
}

package com.ecoserv.dev;

import com.ecoserv.script.ScriptRunner;
import com.ecoserv.script.ScriptSteps;
import com.ecoserv.util.ItemKey;
import com.ecoserv.util.Perms;
import com.ecoserv.util.PlayerUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Блок-программа (2.0).
 * <ul>
 * <li>Когда: ПКМ (можно — только с предметом-ключом), сигнал редстоуна, игрок рядом (вход / пока рядом),
 * слово в чате рядом, таймер, событие.</li>
 * <li>Условия: все должны выполняться.</li>
 * <li>Для кого: тот, кто запустил; все рядом; все в мире (глобальный — красный).</li>
 * <li>Действия: шаги сценария (визуальный редактор). Выход: сигнал редстоуна, свечение блока.</li>
 * </ul>
 */
public class TileLogic extends TileEntity implements ITickable {

    public static final String[] TRIGGERS = {"click", "redstone", "near", "chat", "timer", "event"};
    public static final String WHO_SELF = "self";
    public static final String WHO_NEAR = "near";
    public static final String WHO_WORLD = "world";

    private static final Set<TileLogic> LOADED = Collections.newSetFromMap(new WeakHashMap<TileLogic, Boolean>());

    public String name = "";
    public String trigger = "click";
    /** Для кого: self / near / world. */
    public String who = WHO_SELF;
    public int radius = 4;
    public int interval = 100;
    public String eventName = "";
    public String chatWord = "";
    /** Игрок рядом: срабатывать каждые interval тиков, пока он рядом (иначе — только при входе). */
    public boolean repeatNear;
    /** ПКМ: нужен предмет в руке (пусто — не нужен) и забирать ли его. */
    public String keyItem = "";
    public boolean takeKey;
    public List<String> conditions = new ArrayList<String>();
    public String denyText = "";
    public List<String> steps = new ArrayList<String>();
    public int pulseTicks = 10;
    public int cooldown = 20;
    /** Пауза для каждого игрока отдельно (иначе — общая). */
    public boolean cooldownPerPlayer;
    public List<String> roles = new ArrayList<String>();
    public boolean enabled = true;
    public boolean particles = true;

    private int pulse;
    private long lastFire;
    private final java.util.Map<String, Long> lastFireBy = new java.util.HashMap<String, Long>();
    private boolean wasPowered;
    private int timer;
    private final Set<String> near = new HashSet<String>();
    /** Журнал последних срабатываний (не сохраняется). */
    public final List<String> log = new ArrayList<String>();

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) {
            LOADED.add(this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnload() {
        LOADED.remove(this);
    }

    /** Глобальный (красный): действует на всех в мире. */
    public boolean global() {
        return WHO_WORLD.equals(who);
    }

    public int output() {
        return pulse > 0 ? 15 : 0;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (pulse > 0 && --pulse == 0) {
            setLit(false);
        }
        if (!enabled) {
            return;
        }
        switch (trigger) {
            case "redstone": {
                boolean powered = world.isBlockPowered(pos);
                if (powered && !wasPowered) {
                    fire(nearest(), "сигнал редстоуна");
                }
                wasPowered = powered;
                break;
            }
            case "near": {
                if (++timer % 10 != 0) {
                    break;
                }
                List<EntityPlayer> list = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(pos).grow(radius));
                Set<String> now = new HashSet<String>();
                for (EntityPlayer p : list) {
                    if (p instanceof EntityPlayerMP && !p.isSpectator()) {
                        String id = p.getUniqueID().toString();
                        now.add(id);
                        if (!near.contains(id) || (repeatNear && timer % Math.max(10, interval) < 10)) {
                            fire((EntityPlayerMP) p, "игрок рядом");
                        }
                    }
                }
                near.clear();
                near.addAll(now);
                break;
            }
            case "timer": {
                if (++timer >= Math.max(5, interval)) {
                    timer = 0;
                    fire(nearest(), "таймер");
                }
                break;
            }
            default:
        }
    }

    @Nullable
    private EntityPlayerMP nearest() {
        EntityPlayer p = world.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 64.0D, false);
        return p instanceof EntityPlayerMP ? (EntityPlayerMP) p : null;
    }

    /** ПКМ по блоку. */
    public void click(EntityPlayerMP p) {
        if (!keyItem.isEmpty()) {
            ItemStack hand = p.getHeldItemMainhand();
            ItemKey k = ItemKey.parse(keyItem);
            if (hand.isEmpty() || !k.matches(hand)) {
                com.ecoserv.util.ChatUtil.actionBar(p, denyText.isEmpty() ? "&cНужен ключ: " + com.ecoserv.ui.UiUtil.icon(keyItem).getDisplayName() : denyText);
                record(p, "нет ключа");
                return;
            }
            if (!check(p, "нажатие")) {
                return;
            }
            if (takeKey) {
                hand.shrink(1);
            }
            run(p, "нажатие");
            return;
        }
        fire(p, "нажатие");
    }

    /** Слово в чате рядом. */
    public static void onChat(EntityPlayerMP p, String message) {
        if (LOADED.isEmpty()) {
            return;
        }
        String m = message.toLowerCase(Locale.ROOT);
        for (TileLogic t : new ArrayList<TileLogic>(LOADED)) {
            if (t.isInvalid() || !t.enabled || !"chat".equals(t.trigger) || t.chatWord.isEmpty() || t.world != p.world) {
                continue;
            }
            if (p.getDistanceSq(t.pos) <= t.radius * t.radius && m.contains(t.chatWord.toLowerCase(Locale.ROOT))) {
                t.fire(p, "слово в чате");
            }
        }
    }

    /** Сработать: проверки → действия. who — кто запустил (может быть null для таймера без игроков рядом). */
    public void fire(@Nullable EntityPlayerMP who, String why) {
        if (!check(who, why)) {
            return;
        }
        run(who, why);
    }

    private boolean check(@Nullable EntityPlayerMP p, String why) {
        long now = world.getTotalWorldTime();
        if (cooldownPerPlayer && p != null) {
            Long last = lastFireBy.get(p.getUniqueID().toString());
            if (last != null && now - last < cooldown) {
                return false;
            }
        } else if (now - lastFire < cooldown) {
            return false;
        }
        if (p != null && !roles.isEmpty() && !Perms.isAdmin(p) && !com.ecoserv.role.RoleManager.hasAny(p, roles)) {
            deny(p, why, "нет роли");
            return false;
        }
        if (p != null) {
            for (String c : conditions) {
                if (!c.trim().isEmpty() && !ScriptSteps.condition(p, c, name)) {
                    deny(p, why, "не выполнено: " + ConditionCatalog.describe(c));
                    return false;
                }
            }
        }
        return true;
    }

    private void deny(EntityPlayerMP p, String why, String reason) {
        if (!denyText.isEmpty()) {
            com.ecoserv.util.ChatUtil.actionBar(p, com.ecoserv.content.Actions.placeholders(denyText, p, name));
        }
        record(p, why + " → " + reason);
    }

    private void run(@Nullable EntityPlayerMP p, String why) {
        long now = world.getTotalWorldTime();
        lastFire = now;
        if (p != null) {
            lastFireBy.put(p.getUniqueID().toString(), now);
        }
        if (pulseTicks > 0) {
            pulse = pulseTicks;
            setLit(true);
        }
        if (particles && world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.REDSTONE, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                    12, 0.3D, 0.1D, 0.3D, 0.0D);
        }
        String label = "logic_" + (name.isEmpty() ? Long.toString(pos.toLong()) : name);
        int count = 0;
        if (WHO_WORLD.equals(who)) {
            int rd = com.ecoserv.region.RegionManager.regionDim(world.provider.getDimension());
            for (EntityPlayerMP x : PlayerUtil.allOnline()) {
                if (com.ecoserv.region.RegionManager.regionDim(x.dimension) == rd) {
                    ScriptRunner.startSteps(x, label, steps, name);
                    count++;
                }
            }
        } else if (WHO_NEAR.equals(who)) {
            for (EntityPlayer x : world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(pos).grow(radius))) {
                if (x instanceof EntityPlayerMP) {
                    ScriptRunner.startSteps((EntityPlayerMP) x, label, steps, name);
                    count++;
                }
            }
        } else if (p != null) {
            ScriptRunner.startSteps(p, label, steps, name);
            count = 1;
        }
        record(p, why + " → действий: " + steps.size() + ", игрокам: " + count);
        if (p != null && !name.isEmpty()) {
            DevManager.fire("logic", p, name);
        }
    }

    private void record(@Nullable EntityPlayerMP p, String text) {
        String t = String.format("%tT", System.currentTimeMillis());
        log.add(0, "&8" + t + " &f" + (p == null ? "—" : p.getName()) + " &7" + text);
        while (log.size() > 10) {
            log.remove(log.size() - 1);
        }
    }

    private void setLit(boolean lit) {
        IBlockState st = world.getBlockState(pos);
        if (st.getBlock() instanceof BlockLogic && st.getValue(BlockLogic.LIT) != lit) {
            world.setBlockState(pos, st.withProperty(BlockLogic.LIT, lit), 3);
        }
        world.notifyNeighborsOfStateChange(pos, st.getBlock(), false);
    }

    /** Событие из DevEvents — блоки с триггером «событие». */
    static void onEvent(String event, @Nullable String context, @Nullable EntityPlayerMP p) {
        if (LOADED.isEmpty()) {
            return;
        }
        String full = context == null || context.isEmpty() ? event : event + ":" + context;
        for (TileLogic t : new ArrayList<TileLogic>(LOADED)) {
            if (t.isInvalid() || !t.enabled || !"event".equals(t.trigger) || t.eventName.isEmpty()) {
                continue;
            }
            if ("logic".equals(event) && t.name.equalsIgnoreCase(context)) {
                continue;
            }
            if (t.eventName.equalsIgnoreCase(event) || t.eventName.equalsIgnoreCase(full)) {
                t.fire(p, "событие " + full);
            }
        }
    }

    public void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    /** Одна фраза: что делает блок. */
    public String summary() {
        String when;
        switch (trigger) {
            case "redstone":
                when = "на блок подали сигнал редстоуна";
                break;
            case "near":
                when = "игрок подошёл ближе " + radius + " бл." + (repeatNear ? " (и пока рядом)" : "");
                break;
            case "chat":
                when = "рядом написали «" + chatWord + "»";
                break;
            case "timer":
                when = "прошло " + interval + " тиков (" + (interval / 20.0) + " с)";
                break;
            case "event":
                when = "случилось событие «" + eventName + "»";
                break;
            default:
                when = "игрок нажал ПКМ" + (keyItem.isEmpty() ? "" : " с ключом в руке");
        }
        String cond = conditions.isEmpty() ? "" : ", если " + String.join(" и ", describeAll());
        String forWho = WHO_WORLD.equals(who) ? "для всех в мире" : WHO_NEAR.equals(who) ? "для всех рядом (" + radius + " бл.)"
                : "для того, кто запустил";
        return "Когда " + when + cond + " — выполнить " + steps.size() + " действ. " + forWho + ".";
    }

    private List<String> describeAll() {
        List<String> out = new ArrayList<String>();
        for (String c : conditions) {
            out.add(ConditionCatalog.describe(c));
        }
        return out;
    }

    // --- NBT ----------------------------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        readData(nbt);
    }

    private static List<String> list(NBTTagCompound nbt, String key) {
        List<String> out = new ArrayList<String>();
        NBTTagList l = nbt.getTagList(key, 8);
        for (int i = 0; i < l.tagCount(); i++) {
            out.add(l.getStringTagAt(i));
        }
        return out;
    }

    private static NBTTagList tags(List<String> list) {
        NBTTagList l = new NBTTagList();
        for (String s : list) {
            l.appendTag(new NBTTagString(s));
        }
        return l;
    }

    private void readData(NBTTagCompound nbt) {
        name = nbt.getString("name");
        trigger = nbt.hasKey("trigger") ? nbt.getString("trigger") : "click";
        who = nbt.hasKey("who") ? nbt.getString("who") : "global".equals(nbt.getString("scope")) ? WHO_WORLD : WHO_SELF;
        radius = nbt.hasKey("radius") ? nbt.getInteger("radius") : 4;
        interval = nbt.hasKey("interval") ? nbt.getInteger("interval") : 100;
        eventName = nbt.getString("event");
        chatWord = nbt.getString("chat");
        repeatNear = nbt.getBoolean("repeatNear");
        keyItem = nbt.getString("key");
        takeKey = nbt.getBoolean("takeKey");
        denyText = nbt.getString("deny");
        pulseTicks = nbt.hasKey("pulse") ? nbt.getInteger("pulse") : 10;
        cooldown = nbt.hasKey("cooldown") ? nbt.getInteger("cooldown") : 20;
        cooldownPerPlayer = nbt.getBoolean("cdPlayer");
        enabled = !nbt.hasKey("enabled") || nbt.getBoolean("enabled");
        particles = !nbt.hasKey("particles") || nbt.getBoolean("particles");
        steps = list(nbt, "steps");
        roles = list(nbt, "roles");
        conditions = list(nbt, "conds");
        if (nbt.hasKey("cond") && !nbt.getString("cond").isEmpty() && conditions.isEmpty()) {
            conditions.add(nbt.getString("cond"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("name", name);
        nbt.setString("trigger", trigger);
        nbt.setString("who", who);
        nbt.setInteger("radius", radius);
        nbt.setInteger("interval", interval);
        nbt.setString("event", eventName);
        nbt.setString("chat", chatWord);
        nbt.setBoolean("repeatNear", repeatNear);
        nbt.setString("key", keyItem);
        nbt.setBoolean("takeKey", takeKey);
        nbt.setString("deny", denyText);
        nbt.setInteger("pulse", pulseTicks);
        nbt.setInteger("cooldown", cooldown);
        nbt.setBoolean("cdPlayer", cooldownPerPlayer);
        nbt.setBoolean("enabled", enabled);
        nbt.setBoolean("particles", particles);
        nbt.setTag("steps", tags(steps));
        nbt.setTag("roles", tags(roles));
        nbt.setTag("conds", tags(conditions));
        return nbt;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readData(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}

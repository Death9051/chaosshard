package ru.death.chaosshard.merchant;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import ru.death.chaosshard.ChaosConfig;
import ru.death.chaosshard.anchor.AnchorData;
import ru.death.chaosshard.init.ModBlocks;
import ru.death.chaosshard.network.ChaosNetwork;
import ru.death.chaosshard.network.PacketMerchantView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Торговец у Якоря. Берёт монеты Ancient Warfare на счёт игрока (в медных), продаёт постоянные и сменные
 * (раз в игровой день) товары, скупает добычу Lycanites. Сам приходит к Якорю раз в несколько дней,
 * а Колокольчиком его можно позвать к любому своему Якорю.
 */
public final class Merchant {

    public static final long DAY = 24000L;
    private static final String[] METALS = {"copper", "silver", "gold", "ancient"};

    // ------------------------------------------------------------------ монеты

    public static boolean isCoin(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        ResourceLocation r = s.getItem().getRegistryName();
        return r != null && r.getNamespace().startsWith("ancientwarfare") && "coin".equals(r.getPath());
    }

    /** Стоимость одной монеты в медных (0 — не монета или неизвестный металл). */
    public static int coinValue(ItemStack s) {
        if (!isCoin(s)) {
            return 0;
        }
        String metal = s.hasTagCompound() ? s.getTagCompound().getString("metal") : "";
        int[] v = ChaosConfig.merchant.coinValues;
        for (int i = 0; i < METALS.length; i++) {
            if (METALS[i].equals(metal)) {
                return i < v.length ? Math.max(0, v[i]) : 0;
            }
        }
        return 0;
    }

    private static List<ItemStack> pockets(EntityPlayer p) {
        List<ItemStack> out = new ArrayList<>(p.inventory.mainInventory);
        out.addAll(p.inventory.offHandInventory);
        return out;
    }

    /** Сколько монет (в медных) лежит у игрока. */
    public static long coinsWorth(EntityPlayer p) {
        long sum = 0;
        for (ItemStack s : pockets(p)) {
            sum += (long) coinValue(s) * s.getCount();
        }
        return sum;
    }

    /** Все монеты из сумки — на счёт. Возвращает, сколько внесено. */
    public static long deposit(EntityPlayer p) {
        long sum = 0;
        for (ItemStack s : pockets(p)) {
            int v = coinValue(s);
            if (v > 0) {
                sum += (long) v * s.getCount();
                s.setCount(0);
            }
        }
        if (sum > 0) {
            MerchantData.get(p.world).add(p.getUniqueID(), sum);
            p.inventory.markDirty();
        }
        return sum;
    }

    // ------------------------------------------------------------------ товары

    public static final class Offer {
        public final ItemStack stack;
        public final int price;
        public final boolean daily;

        Offer(ItemStack stack, int price, boolean daily) {
            this.stack = stack;
            this.price = price;
            this.daily = daily;
        }
    }

    /**
     * «мод:предмет[@мета][*кол-во][!чары]=цена» → товар (null — нет такого предмета или строка кривая).
     * «!N» — случайные чары уровня N (для снаряжения); r задаёт, какие именно (на день одни и те же).
     */
    static Offer parse(String line, boolean daily, Random r) {
        try {
            int eq = line.lastIndexOf('=');
            int price = Math.max(Integer.parseInt(line.substring(eq + 1).trim()), ChaosConfig.merchant.minPrice);
            String id = line.substring(0, eq).trim();
            int count = 1, meta = 0, ench = 0;
            int bang = id.indexOf('!');
            if (bang >= 0) {
                ench = Integer.parseInt(id.substring(bang + 1));
                id = id.substring(0, bang);
            }
            int star = id.indexOf('*');
            if (star >= 0) {
                count = Integer.parseInt(id.substring(star + 1));
                id = id.substring(0, star);
            }
            int at = id.indexOf('@');
            if (at >= 0) {
                meta = Integer.parseInt(id.substring(at + 1));
                id = id.substring(0, at);
            }
            Item it = Item.getByNameOrId(id);
            if (it == null || price <= 0 || count <= 0) {
                return null;
            }
            ItemStack st = new ItemStack(it, count, meta);
            if (ench > 0) {
                st = net.minecraft.enchantment.EnchantmentHelper.addRandomEnchantment(r != null ? r : new Random(line.hashCode()), st, ench, false);
            }
            return new Offer(st, price, daily);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Номер игрового дня (по верхнему миру) — от него зависят сменные товары. */
    public static long day(World w) {
        World ow = w.getMinecraftServer() != null ? w.getMinecraftServer().getWorld(0) : w;
        return ow.getWorldTime() / DAY;
    }

    /** Сменные товары по категориям: реликвии, снаряжение, материалы, припасы. */
    static String[][] categories() {
        ChaosConfig.MerchantCfg c = ChaosConfig.merchant;
        return new String[][]{c.dailyRelics, c.dailyGear, c.dailyMaterials, c.dailySupplies};
    }

    /** Сначала сменные (из каждой категории по несколько, на день одни и те же), потом постоянные. */
    public static List<Offer> offers(World w) {
        List<Offer> out = new ArrayList<>();
        long day = day(w);
        String[][] cats = categories();
        for (int c = 0; c < cats.length; c++) {
            Random r = new Random(day * 0x9E3779B97F4A7C15L + 1234567L + c * 7919L);
            List<String> pool = new ArrayList<>(java.util.Arrays.asList(cats[c]));
            java.util.Collections.shuffle(pool, r);
            int taken = 0;
            for (String line : pool) {
                if (taken >= ChaosConfig.merchant.dailyPerCategory) {
                    break;
                }
                Offer o = parse(line, true, r);
                if (o != null) {
                    out.add(o);
                    taken++;
                }
            }
        }
        for (String line : ChaosConfig.merchant.goods) {
            Offer o = parse(line, false, null);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }

    /** Купить товар номер idx. Возвращает false, если денег мало или товара нет. */
    public static boolean buy(EntityPlayer p, int idx) {
        List<Offer> list = offers(p.world);
        if (idx < 0 || idx >= list.size()) {
            return false;
        }
        Offer o = list.get(idx);
        MerchantData d = MerchantData.get(p.world);
        if (d.balance(p.getUniqueID()) < o.price) {
            return false;
        }
        d.add(p.getUniqueID(), -o.price);
        ItemStack give = o.stack.copy();
        if (!p.inventory.addItemStackToInventory(give) && !give.isEmpty()) {
            p.dropItem(give, false);
        }
        p.inventory.markDirty();
        return true;
    }

    // ------------------------------------------------------------------ скупка Lycanites

    public static final int CHARGE = 0, MEAT = 1, ORGAN = 2, PART = 3;

    /** Вид добычи Lycanites (−1 — торговец такое не берёт). */
    public static int lycKind(ItemStack s) {
        if (s.isEmpty()) {
            return -1;
        }
        ResourceLocation r = s.getItem().getRegistryName();
        if (r == null || !"lycanitesmobs".equals(r.getNamespace())) {
            return -1;
        }
        String cls = s.getItem().getClass().getSimpleName();
        String path = r.getPath();
        if ("ChargeItem".equals(cls)) {
            return CHARGE;
        }
        if (path.endsWith("_meat") && (path.startsWith("raw_") || path.startsWith("cooked_"))) {
            return MEAT;
        }
        if ("ItemFrostyFur".equals(cls) || "ItemGeistLiver".equals(cls) || "ItemPoisonGland".equals(cls)) {
            return ORGAN;
        }
        if ("ItemEquipmentPart".equals(cls)) {
            return PART;
        }
        return -1;
    }

    public static int sellPrice(ItemStack s) {
        int k = lycKind(s);
        int[] v = ChaosConfig.merchant.lycanitesPrices;
        return k < 0 || k >= v.length ? 0 : Math.max(0, v[k]);
    }

    /** Что из сумки торговец купит: по предмету (с метой) — сколько штук и почём. */
    public static final class Lot {
        public final String key;
        public final ItemStack icon;
        public int count;
        public long total;
        public final boolean part;

        Lot(String key, ItemStack icon, boolean part) {
            this.key = key;
            this.icon = icon;
            this.part = part;
        }
    }

    static String key(ItemStack s) {
        return s.getItem().getRegistryName() + "@" + s.getMetadata();
    }

    public static List<Lot> lots(EntityPlayer p) {
        Map<String, Lot> map = new LinkedHashMap<>();
        for (ItemStack s : pockets(p)) {
            int price = sellPrice(s);
            if (price <= 0) {
                continue;
            }
            Lot l = map.computeIfAbsent(key(s), k -> {
                ItemStack icon = s.copy();
                icon.setCount(1);
                return new Lot(k, icon, lycKind(s) == PART);
            });
            l.count += s.getCount();
            l.total += (long) price * s.getCount();
        }
        return new ArrayList<>(map.values());
    }

    /** Продать всё одного вида (key) или всю добычу, кроме частей снаряжения (key == null). */
    public static long sell(EntityPlayer p, String key) {
        long sum = 0;
        for (ItemStack s : pockets(p)) {
            int price = sellPrice(s);
            if (price <= 0) {
                continue;
            }
            if (key == null ? lycKind(s) == PART : !key.equals(key(s))) {
                continue;
            }
            sum += (long) price * s.getCount();
            s.setCount(0);
        }
        if (sum > 0) {
            MerchantData.get(p.world).add(p.getUniqueID(), sum);
            p.inventory.markDirty();
        }
        return sum;
    }

    // ------------------------------------------------------------------ окно

    public static NBTTagCompound view(EntityPlayer p, EntityMerchant m) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("entity", m.getEntityId());
        t.setLong("balance", MerchantData.get(p.world).balance(p.getUniqueID()));
        t.setLong("coins", coinsWorth(p));
        t.setLong("leave", Math.max(0, m.leaveIn()));
        NBTTagList offers = new NBTTagList();
        for (Offer o : offers(p.world)) {
            NBTTagCompound ot = o.stack.writeToNBT(new NBTTagCompound());
            ot.setInteger("price", o.price);
            ot.setBoolean("daily", o.daily);
            offers.appendTag(ot);
        }
        t.setTag("offers", offers);
        NBTTagList lots = new NBTTagList();
        for (Lot l : lots(p)) {
            NBTTagCompound lt = l.icon.writeToNBT(new NBTTagCompound());
            lt.setString("key", l.key);
            lt.setInteger("n", l.count);
            lt.setLong("total", l.total);
            lt.setBoolean("part", l.part);
            lots.appendTag(lt);
        }
        t.setTag("lots", lots);
        int[] v = ChaosConfig.merchant.coinValues;
        t.setIntArray("values", v);
        return t;
    }

    /** Послать окно игроку: open — открыть (ПКМ по торговцу), иначе только обновить уже открытое. */
    public static void open(EntityPlayerMP p, EntityMerchant m, boolean open) {
        NBTTagCompound t = view(p, m);
        t.setBoolean("open", open);
        ChaosNetwork.CHANNEL.sendTo(new PacketMerchantView(t), p);
    }

    // ------------------------------------------------------------------ приход к Якорю

    /** Якорь по любому его блоку (колонна, плита) или null. */
    public static BlockPos anchorAt(World w, BlockPos pos) {
        Block b = w.getBlockState(pos).getBlock();
        if (b == ModBlocks.CHAOS_ANCHOR) {
            return pos;
        }
        if (b instanceof ru.death.chaosshard.anchor.BlockAnchorPart) {
            return ru.death.chaosshard.anchor.BlockAnchorPart.base(w, pos);
        }
        if (b instanceof ru.death.chaosshard.anchor.BlockAnchorRim) {
            return ru.death.chaosshard.anchor.BlockAnchorRim.center(w, pos);
        }
        return null;
    }

    /** Место у Якоря, где можно встать (null — нет). */
    static BlockPos standNear(World w, BlockPos anchor, Random r) {
        for (int tries = 0; tries < 64; tries++) {
            int rad = 3 + tries / 16;
            double a = r.nextDouble() * Math.PI * 2;
            int x = anchor.getX() + (int) Math.round(Math.cos(a) * rad), z = anchor.getZ() + (int) Math.round(Math.sin(a) * rad);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos p = new BlockPos(x, anchor.getY() + dy, z);
                IBlockState below = w.getBlockState(p.down());
                if (below.isSideSolid(w, p.down(), net.minecraft.util.EnumFacing.UP)
                        && w.getBlockState(p).getCollisionBoundingBox(w, p) == Block.NULL_AABB
                        && w.getBlockState(p.up()).getCollisionBoundingBox(w, p.up()) == Block.NULL_AABB
                        && !w.getBlockState(p).getMaterial().isLiquid()) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Найти торговца игрока (в загруженных мирах). */
    public static EntityMerchant find(UUID owner) {
        for (WorldServer w : FMLCommonHandler.instance().getMinecraftServerInstance().worlds) {
            for (net.minecraft.entity.Entity e : w.loadedEntityList) {
                if (e instanceof EntityMerchant && e.isEntityAlive() && owner.equals(((EntityMerchant) e).owner())) {
                    return (EntityMerchant) e;
                }
            }
        }
        return null;
    }

    /** Поставить торговца у Якоря (прежнего, если он где-то стоит, уводим). Null — встать негде. */
    public static EntityMerchant summon(WorldServer w, BlockPos anchor, UUID owner) {
        BlockPos at = standNear(w, anchor, w.rand);
        if (at == null) {
            return null;
        }
        EntityMerchant old = find(owner);
        if (old != null) {
            old.leave();
        }
        long stay = Math.max(600L, (long) (ChaosConfig.merchant.stayDays * DAY));
        EntityMerchant m = new EntityMerchant(w);
        m.setup(owner, anchor, w.getTotalWorldTime() + stay);
        m.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.rand.nextFloat() * 360F, 0F);
        w.spawnEntity(m);
        w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, m.posX, m.posY + 1, m.posZ, 20, 0.3, 0.6, 0.3, 0.02);
        w.playSound(null, at, SoundEvents.ENTITY_ILLAGER_CAST_SPELL, SoundCategory.NEUTRAL, 0.8F, 0.7F);
        MerchantData d = MerchantData.get(w);
        d.busyUntil.put(owner, overworldTime(w) + stay);
        d.markDirty();
        return m;
    }

    static long overworldTime(World w) {
        return w.getMinecraftServer() != null ? w.getMinecraftServer().getWorld(0).getTotalWorldTime() : w.getTotalWorldTime();
    }

    /** Раз в 10 секунд: не пора ли торговцу прийти к кому-нибудь. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ChaosConfig.merchant.visitDays <= 0) {
            return;
        }
        net.minecraft.server.MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (srv == null || srv.getTickCounter() % 200 != 0) {
            return;
        }
        WorldServer ow = srv.getWorld(0);
        long now = ow.getTotalWorldTime();
        MerchantData d = MerchantData.get(ow);
        long every = Math.max(1200L, (long) (ChaosConfig.merchant.visitDays * DAY));
        for (EntityPlayerMP p : srv.getPlayerList().getPlayers()) {
            visit(p, d, now, every);
        }
    }

    /** Пора ли торговцу прийти к игроку p — и если пора, привести его к ближайшему загруженному Якорю игрока. */
    public static boolean visit(EntityPlayerMP p, MerchantData d, long now, long every) {
        UUID id = p.getUniqueID();
        Long next = d.nextVisit.get(id);
        if (next == null) {
            d.nextVisit.put(id, now + every);
            d.markDirty();
            return false;
        }
        if (now < next || now < d.busyUntil.getOrDefault(id, 0L)) {
            return false;
        }
        WorldServer w = p.getServerWorld();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        AnchorData ad = AnchorData.get(w);
        for (BlockPos a : ad.anchors) {
            AnchorData.Rec rec = ad.rec(a);
            if (rec == null || !id.equals(rec.owner) || !w.isBlockLoaded(a) || w.getBlockState(a).getBlock() != ModBlocks.CHAOS_ANCHOR) {
                continue;
            }
            double dist = a.distanceSq(p.getPosition());
            if (dist < bd) {
                bd = dist;
                best = a;
            }
        }
        if (best == null || summon(w, best, id) == null) {
            return false;   // ни одного своего Якоря рядом — попробуем позже
        }
        d.nextVisit.put(id, now + every + w.rand.nextInt((int) Math.max(1, every / 4)));
        d.markDirty();
        p.sendMessage(new TextComponentTranslation("chaosshard.merchant.arrived").setStyle(
                new net.minecraft.util.text.Style().setColor(TextFormatting.GOLD)));
        return true;
    }
}

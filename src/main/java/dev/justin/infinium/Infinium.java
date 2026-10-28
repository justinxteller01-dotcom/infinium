package dev.justin.infinium;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class Infinium implements ModInitializer {
    public static final String MODID = "infinium";
    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("infinium");
    public static Identifier id(String p) { return Identifier.fromNamespaceAndPath(MODID, p); }

    public static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, id("infinium_tool_materials"));
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, id("infinium"));
    /** Infinity set: own equipment asset (own textures), same stats as Infinium. */
    public static final ResourceKey<EquipmentAsset> INFINITY_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, id("infinity"));

    // 5x netherite durability (2031 -> 10155); attack bonus 5 (netherite 4)
    public static final ToolMaterial MAT = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031 * 5, 9.0F, 5.0F, 15, REPAIR);
    // Pickaxe speed 25: with Efficiency V (+26) = 51. stone 51/1.5/30=1.13 (instamine), cobble 51/2/30=0.85 (not)
    public static final float PICK_SPEED = 25.0F;
    public static final ToolMaterial PICK_MAT = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031 * 5, PICK_SPEED, 5.0F, 15, REPAIR);
    // Shovel speed 100: instamines hardness <= 3.33 even without Efficiency (all vanilla shovel blocks are <= 0.6 hardness... clay 0.6, etc.)
    public static final ToolMaterial SHOVEL_MAT = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031 * 5, 100.0F, 5.0F, 15, REPAIR);

    public static final ArmorMaterial ARMOR;
    static {
        Map<ArmorType, Integer> d = new EnumMap<>(ArmorType.class);
        d.put(ArmorType.BOOTS, 3); d.put(ArmorType.LEGGINGS, 6); d.put(ArmorType.CHESTPLATE, 8); d.put(ArmorType.HELMET, 3); d.put(ArmorType.BODY, 20);
        // 37*5 = 185 durability multiplier (5x netherite); toughness 4 (netherite 3); kb res 0.1 per piece (+0.85 extra on chestplate)
        ARMOR = new ArmorMaterial(37 * 5, d, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0F, 0.1F, REPAIR, ASSET);
        INFINITY_ARMOR = new ArmorMaterial(37 * 5, d, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 5.0F, 0.1F, REPAIR, INFINITY_ASSET);
    }
    public static final ArmorMaterial INFINITY_ARMOR;

    static final ThreadLocal<List<net.minecraft.core.BlockPos>> PENDING = ThreadLocal.withInitial(List::of);
    static final ThreadLocal<Float> PENDING_HARDNESS = ThreadLocal.withInitial(() -> 0F);
    // BreadCraft: leather-level protection, unbreakable
    public static final ResourceKey<EquipmentAsset> BREAD_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, id("bread"));
    public static final ArmorMaterial BREAD;
    static {
        Map<ArmorType, Integer> d = new EnumMap<>(ArmorType.class);
        d.put(ArmorType.BOOTS, 1); d.put(ArmorType.LEGGINGS, 2); d.put(ArmorType.CHESTPLATE, 3); d.put(ArmorType.HELMET, 1); d.put(ArmorType.BODY, 3);
        BREAD = new ArmorMaterial(5, d, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, TagKey.create(Registries.ITEM, id("bread_repair")), BREAD_ASSET);
    }
    public static Item NETHERITE_ROCKET;   // registry id infinium:infinium_rocket (alias infinium:netherite_rocket), name "Infinium Rocket"
    public static Item INF_HELMET, INF_CHESTPLATE, INF_LEGGINGS, INF_BOOTS;
    public static Item BREAD_HELMET, BREAD_CHESTPLATE, BREAD_LEGGINGS, BREAD_BOOTS;
    public static Holder<MobEffect> BLEEDING, BURNING;
    public static EntityType<EnergySlashEntity> ENERGY_SLASH;
    public static Block INFINIUM_FARMLAND;
    public static Item SPACETIME_FRAGMENTS, SPACETIME_CRYSTAL;
    /** Same instance as {@link #SPACETIME_FRAGMENTS}. The id {@code strange_fragments} is an alias. */
    public static Item STRANGE_FRAGMENTS;
    public static Item INGOT, HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, PICKAXE, AXE, SHOVEL, HOE;

    /** Same call as the rocket alias below. First id is the old one, second is the id that is actually registered. */
    private static void aliasItem(String realPath, String oldPath) {
        ((net.fabricmc.fabric.api.event.registry.FabricRegistry) BuiltInRegistries.ITEM).addAlias(id(oldPath), id(realPath));
    }

    static ResourceKey<Item> key(String n) { return ResourceKey.create(Registries.ITEM, id(n)); }
    static Item reg(String n, java.util.function.Function<Item.Properties, Item> f, Item.Properties p) {
        ResourceKey<Item> k = key(n);
        return Registry.register(BuiltInRegistries.ITEM, k, f.apply(p.setId(k)));
    }
    static Item reg(String n, Item.Properties p) { return reg(n, Item::new, p); }

    /** v4: client-only shooting-star particles (spawned by WorldSkyFx, rendered by client/StarParticle). */
    public static final net.minecraft.core.particles.SimpleParticleType STAR_HEAD = net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true);
    public static final net.minecraft.core.particles.SimpleParticleType STAR_TRAIL = net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true);

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("star_head"), STAR_HEAD);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("star_trail"), STAR_TRAIL);
        InfiniumShovelItem.registerComponent();
        InfiniumHoeItem.registerComponent();
        InfiniumAxeItem.registerComponent();
        GlowColor.register();
        ArmorRemoval.register();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack")) dev.justin.infinium.compat.tb.TbStacks.register();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("accessories")) try { dev.justin.infinium.compat.AccessoriesRepair.register(); } catch (Throwable ignored) {}   // pass2
        InfiniumStyle.register();
        InfiniumPickaxeItem.registerComponent();
        InfiniumSwordItem.registerComponent();
        BLEEDING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("bleeding"), new BleedingEffect());
        BURNING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("burning"), new BleedingEffect(true));

        ResourceKey<EntityType<?>> esk = ResourceKey.create(Registries.ENTITY_TYPE, id("energy_slash"));
        ENERGY_SLASH = Registry.register(BuiltInRegistries.ENTITY_TYPE, esk,
            EntityType.Builder.<EnergySlashEntity>of(EnergySlashEntity::new, MobCategory.MISC)
                .sized(3.0F, 0.6F)
                .fireImmune()
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(esk));

        ResourceKey<Block> fk = ResourceKey.create(Registries.BLOCK, id("infinium_farmland"));
        INFINIUM_FARMLAND = Registry.register(BuiltInRegistries.BLOCK, fk, new InfiniumFarmlandBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).randomTicks().strength(0.6F).sound(SoundType.GRAVEL)
                .isViewBlocking((s, l, p) -> true).isSuffocating((s, l, p) -> true).setId(fk)));

        SPACETIME_FRAGMENTS = reg("spacetime_fragments", new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
        STRANGE_FRAGMENTS = SPACETIME_FRAGMENTS;
        aliasItem("spacetime_fragments", "strange_fragments");
        SPACETIME_CRYSTAL = reg("spacetime_crystal", new Item.Properties().fireResistant().rarity(Rarity.EPIC));
        INGOT = reg("infinium_ingot", new Item.Properties().fireResistant().rarity(Rarity.EPIC));

        HELMET = reg("infinium_helmet", new Item.Properties().humanoidArmor(ARMOR, ArmorType.HELMET).fireResistant());
        CHESTPLATE = reg("infinium_chestplate", chestProps(ARMOR));
        LEGGINGS = reg("infinium_leggings", new Item.Properties().humanoidArmor(ARMOR, ArmorType.LEGGINGS).fireResistant());
        BOOTS = reg("infinium_boots", new Item.Properties().humanoidArmor(ARMOR, ArmorType.BOOTS).fireResistant());
        INF_HELMET = reg("infinity_helmet", new Item.Properties().humanoidArmor(INFINITY_ARMOR, ArmorType.HELMET).fireResistant().rarity(Rarity.EPIC));
        INF_CHESTPLATE = reg("infinity_chestplate", chestProps(INFINITY_ARMOR).rarity(Rarity.EPIC));
        INF_LEGGINGS = reg("infinity_leggings", new Item.Properties().humanoidArmor(INFINITY_ARMOR, ArmorType.LEGGINGS).fireResistant().rarity(Rarity.EPIC));
        INF_BOOTS = reg("infinity_boots", new Item.Properties().humanoidArmor(INFINITY_ARMOR, ArmorType.BOOTS).fireResistant().rarity(Rarity.EPIC));

        // tooltip 12 attack damage = 1 (base) + 6 + 5 (material bonus); attack speed 4 - 2.4 = 1.6 (unchanged)
        SWORD = reg("infinium_sword", InfiniumSwordItem::new, new Item.Properties().sword(MAT, 4.0F, -2.4F).fireResistant());
        PICKAXE = reg("infinium_pickaxe", InfiniumPickaxeItem::new, new Item.Properties().pickaxe(PICK_MAT, 1.0F, -2.8F).fireResistant());
        AXE = reg("infinium_axe", p -> new InfiniumAxeItem(MAT, 5.0F, -3.0F, p), new Item.Properties().fireResistant());
        SHOVEL = reg("infinium_shovel", p -> new InfiniumShovelItem(SHOVEL_MAT, 1.5F, -3.0F, p), new Item.Properties().fireResistant());
        HOE = reg("infinium_hoe", p -> new InfiniumHoeItem(MAT, -5.0F, 0.0F, p),
            new Item.Properties().fireResistant().component(DataComponents.UNBREAKABLE, Unit.INSTANCE));

        BREAD_HELMET = reg("bread_helmet", new Item.Properties().humanoidArmor(BREAD, ArmorType.HELMET).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
        BREAD_CHESTPLATE = reg("bread_chestplate", new Item.Properties().humanoidArmor(BREAD, ArmorType.CHESTPLATE).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
        BREAD_LEGGINGS = reg("bread_leggings", new Item.Properties().humanoidArmor(BREAD, ArmorType.LEGGINGS).component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
        BREAD_BOOTS = reg("bread_boots", new Item.Properties().humanoidArmor(BREAD, ArmorType.BOOTS).component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
            .component(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Always have a soft landing.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)))));

        StyleTemplates.register();
        StyleLoot.register();
        DungeonFinder.register();
        RoguelikeCompat.register();
        SingularityStacks.register();
        SingularityStacks.registerLifecycle();
        // R5: "Infinium Rocket". Description id contains "booster_" so Easy Elytra Takeoff uses its booster path (never shrinks the stack).
        NETHERITE_ROCKET = reg("infinium_rocket", NetheriteRocketItem::new, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.RARE)
            .overrideDescription("item.infinium.booster_infinium_rocket")
            .component(DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1, List.of())));
        ((net.fabricmc.fabric.api.event.registry.FabricRegistry) BuiltInRegistries.ITEM).addAlias(id("netherite_rocket"), id("infinium_rocket"));   // old saves
        ModSounds.register();
        SetBonuses.register();
        Perks.register();
        UiUnlocks.register();   // batch1 A1/A3 persistent UI unlock flags
        CrystalCharge.register();
        EnchantRules.init();
        // creative tabs (JEI/EMI list registered items anyway; this makes them visible in vanilla creative too)
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"))).register(o -> { o.accept(STRANGE_FRAGMENTS); o.accept(SPACETIME_CRYSTAL); o.accept(INGOT); for (Item t : StyleTemplates.TEMPLATES.values()) o.accept(t); });
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"))).register(o -> {
            for (Item it : new Item[]{SWORD, AXE}) o.accept(it);
            for (ItemStack st : styledArmor()) o.accept(st);
            for (Item it : new Item[]{BREAD_HELMET, BREAD_CHESTPLATE, BREAD_LEGGINGS, BREAD_BOOTS}) o.accept(it);
        });
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"))).register(o -> {
            for (Item it : new Item[]{SHOVEL, PICKAXE, AXE, HOE, NETHERITE_ROCKET, DungeonFinder.ITEM}) o.accept(it);
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            PENDING.set(InfiniumShovelItem.extraTargets(level, player, pos, state));
            PENDING_HARDNESS.set(state.getDestroySpeed(level, pos));
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            List<net.minecraft.core.BlockPos> extras = PENDING.get();
            PENDING.set(List.of());
            if (!extras.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel sl && player instanceof ServerPlayer sp)
                InfiniumShovelItem.breakExtras(sl, sp, extras, PENDING_HARDNESS.get());
        });

        ModeFx.register();
        dev.justin.infinium.ability.Abilities.registerNetworking();
        SpacetimeWarp.register();
        AirJumps.register();   // G4b
        dev.justin.infinium.ability.XrayPulse.register();
        // M5: an ability works only with a full matching set on (matches the hidden HUD) and while its toggle
        // (settings menu) is on. Same resolver on client (key + HUD) and server (tryActivate).
        dev.justin.infinium.ability.Abilities.setEnabledResolver((p, a) -> setWorn(p) && Perks.on(p, Perk.ability(a.id().getPath())));   // M3: X-ray pulse ability (replaces continuous X-ray)   // M2: warp is the first ability (its old Ctrl payload is removed)   // M1 ability core (abilities themselves register in M2/M3, here in common init)
        PayloadTypeRegistry.serverboundPlay().register(ToggleGlidePayload.TYPE, ToggleGlidePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleGlidePayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            boolean fromSlot = payload.invIndex() >= 0 || payload.slot() >= 0;   // inventory wing square: a click, no sneak needed
            if (!fromSlot && (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())) return;   // gesture: sneak + empty hand only
            ItemStack stack = ItemStack.EMPTY;
            if (payload.invIndex() >= 0) {
                if (payload.invIndex() < player.getInventory().getContainerSize()) stack = player.getInventory().getItem(payload.invIndex());
            } else {
                AbstractContainerMenu menu = player.containerMenu;
                if (menu != null && menu.containerId == payload.containerId() && payload.slot() >= 0 && payload.slot() < menu.slots.size())
                    stack = menu.slots.get(payload.slot()).getItem();
            }
            if (!isChestplate(stack)) stack = player.getItemBySlot(EquipmentSlot.CHEST);
            long now = player.level().getGameTime();
            Long last = WING_KEY_LAST.get(player.getUUID());
            if (last != null && now - last < 5) return;   // rate limit: 1 per 5 ticks
            WING_KEY_LAST.put(player.getUUID(), now);
            toggleWings(player, stack);
        });
        PayloadTypeRegistry.serverboundPlay().register(WingsKeyPayload.TYPE, WingsKeyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WingsKeyPayload.TYPE, (payload, ctx) -> {   // custom wings key: worn chestplate, no gesture checks
            ServerPlayer player = ctx.player();
            long now = player.level().getGameTime();
            Long last = WING_KEY_LAST.get(player.getUUID());
            if (last != null && now - last < 5) return;   // rate limit: 1 per 5 ticks
            WING_KEY_LAST.put(player.getUUID(), now);
            toggleWings(player, player.getItemBySlot(EquipmentSlot.CHEST));
        });
        PayloadTypeRegistry.serverboundPlay().register(FlightThrustPayload.TYPE, FlightThrustPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlightThrustStatePayload.TYPE, FlightThrustStatePayload.CODEC);   // M6
        ServerPlayNetworking.registerGlobalReceiver(FlightThrustPayload.TYPE, (payload, ctx) -> {
            InfinityFlight.setThrustHeld(ctx.player().getUUID(), payload.active());
            // M6: relay to nearby (tracking) players for remote thrust particles + sounds.
            var sp = ctx.player();
            FlightThrustStatePayload relay = new FlightThrustStatePayload(sp.getId(), payload.active());
            ctx.server().execute(() -> {
                for (net.minecraft.server.level.ServerPlayer o : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(sp))
                    if (o != sp && ServerPlayNetworking.canSend(o, FlightThrustStatePayload.TYPE)) ServerPlayNetworking.send(o, relay);
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(SwordSlashPayload.TYPE, SwordSlashPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SwordSlashPayload.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                SwordSlashHandler.handleServerSlash(ctx.player(), payload.yaw(), payload.pitch(), payload.roll(), payload.color(), payload.isStab());
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(SwordThemePayload.TYPE, SwordThemePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SwordThemePayload.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                byte sing = SwordTheme.allowed(SetLook.SINGULARITY, payload.singularity()) ? payload.singularity() : (byte) SetLook.SINGULARITY.ordinal();
                byte cp = SwordTheme.allowed(SetLook.CYBERPSYCHO, payload.cyberpsycho()) ? payload.cyberpsycho() : (byte) SetLook.CYBERPSYCHO.ordinal();
                byte inf = SwordTheme.allowed(SetLook.INFINITY, payload.infinity()) ? payload.infinity() : (byte) SetLook.INFINITY.ordinal();
                SwordTheme.set(ctx.player().getUUID(), new byte[]{
                    (byte) SetLook.ASTRAL.ordinal(),
                    (byte) SetLook.CYBERNETIC.ordinal(),
                    sing,
                    cp,
                    inf
                });
            });
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> {
            WING_KEY_LAST.remove(h.player.getUUID());
            InfinityFlight.reset(h.player.getUUID());
            SwordSlashHandler.onPlayerDisconnect(h.player.getUUID());
            SwordTheme.remove(h.player.getUUID());
        });
        InfiniumConfig.load();
        PayloadTypeRegistry.clientboundPlay().register(InfiniumConfigSyncPayload.TYPE, InfiniumConfigSyncPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.getPlayer(), new InfiniumConfigSyncPayload(InfiniumConfig.toJson()));
        });
        dev.justin.infinium.loot.NaturalRefill.register();
        dev.justin.infinium.loot.ChestHints.register();
        dev.justin.infinium.dungeon.DungeonReset.register();
        dev.justin.infinium.world.MeteorShower.register();
        dev.justin.infinium.InfiniumCommands.register();
    }
    private static final java.util.Map<java.util.UUID, Long> WING_KEY_LAST = new java.util.concurrent.ConcurrentHashMap<>();
    static void toggleWings(ServerPlayer player, ItemStack stack) {
            if (!isChestplate(stack)) return;
            boolean on = !stack.has(DataComponents.GLIDER);
            if (on) stack.set(DataComponents.GLIDER, Unit.INSTANCE); else stack.remove(DataComponents.GLIDER);
            if (!on && player.isFallFlying()) player.stopFallFlying();
            if (stack == player.getItemBySlot(EquipmentSlot.CHEST)) {
                player.setItemSlot(EquipmentSlot.CHEST, stack.copy());
            }
            int fam = Math.max(0, Math.min(4, Perk.family(stack)));
            Holder<SoundEvent> s = ModSounds.wingsToggle(fam, on);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                s.value(), SoundSource.PLAYERS, 0.65F, 1.0F);
            ModeFx.burst(player, ModeFx.WINGS, on ? 1 : 0, stack);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Mode: Wings " + (on ? "On" : "Off")).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.sendAllDataToRemote();
    }

    /** Chestplate properties shared by Infinium and Infinity: +0.85 KB resistance block and wings default ON (R8 13.4). */
    static Item.Properties chestProps(ArmorMaterial mat) {
        Item.Properties chest = new Item.Properties().humanoidArmor(mat, ArmorType.CHESTPLATE).fireResistant();
        java.util.List<net.minecraft.world.item.component.ItemAttributeModifiers.Entry> mods =
            new java.util.ArrayList<>(mat.createAttributes(ArmorType.CHESTPLATE).modifiers());
        mods.removeIf(e -> e.attribute().equals(Attributes.KNOCKBACK_RESISTANCE));   // material's own KB line folded into ours
        double kb = 0.85;
        for (var e : mat.createAttributes(ArmorType.CHESTPLATE).modifiers()) if (e.attribute().equals(Attributes.KNOCKBACK_RESISTANCE)) kb += e.modifier().amount();
        mods.add(new net.minecraft.world.item.component.ItemAttributeModifiers.Entry(Attributes.KNOCKBACK_RESISTANCE,
            new AttributeModifier(CHEST_KB_ID, kb, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST,
            net.minecraft.world.item.component.ItemAttributeModifiers.Display.override(
                Component.literal("Extreme extra knockback resistance").withStyle(ChatFormatting.LIGHT_PURPLE))));
        chest.attributes(new net.minecraft.world.item.component.ItemAttributeModifiers(mods));
        chest.component(DataComponents.GLIDER, Unit.INSTANCE);   // wings default ON; OFF = removal patch
        return chest;
    }
    public static final Identifier CHEST_KB_ID = id("chestplate_knockback");
    public static final double CHEST_KB = 0.85 + 0.1;

    /** The four styled Infinium armor sets + the Infinity set, for the creative tab and JEI. */
    public static List<ItemStack> styledArmor() {
        java.util.ArrayList<ItemStack> l = new java.util.ArrayList<>();
        for (InfiniumStyle st : InfiniumStyle.values()) for (Item it : new Item[]{HELMET, CHESTPLATE, LEGGINGS, BOOTS}) {
            ItemStack s = new ItemStack(it); s.set(InfiniumStyle.STYLE, st); l.add(s);
        }
        for (Item it : new Item[]{INF_HELMET, INF_CHESTPLATE, INF_LEGGINGS, INF_BOOTS}) l.add(new ItemStack(it));
        return l;
    }
    // ---------- item / set helpers (Infinium OR Infinity) ----------
    public static boolean isHelmet(ItemStack s)     { return HELMET != null && (s.is(HELMET) || s.is(INF_HELMET)); }
    public static boolean isChestplate(ItemStack s) { return HELMET != null && (s.is(CHESTPLATE) || s.is(INF_CHESTPLATE)); }
    public static boolean isLeggings(ItemStack s)   { return HELMET != null && (s.is(LEGGINGS) || s.is(INF_LEGGINGS)); }
    public static boolean isBoots(ItemStack s)      { return HELMET != null && (s.is(BOOTS) || s.is(INF_BOOTS)); }
    public static boolean isArmor(ItemStack s)      { return isHelmet(s) || isChestplate(s) || isLeggings(s) || isBoots(s); }
    public static boolean isInfinity(ItemStack s)   { return HELMET != null && (s.is(INF_HELMET) || s.is(INF_CHESTPLATE) || s.is(INF_LEGGINGS) || s.is(INF_BOOTS)); }
    public static boolean isTool(ItemStack s)       { return HELMET != null && (s.is(SWORD) || s.is(PICKAXE) || s.is(AXE) || s.is(SHOVEL) || s.is(HOE)); }
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Infinium OR Infinity piece in its own slot (broken pieces count too; only standard stats/enchantments switch off). */
    public static boolean wearingPiece(LivingEntity e, EquipmentSlot slot) {
        ItemStack s = e.getItemBySlot(slot);
        boolean ok = switch (slot) { case HEAD -> isHelmet(s); case CHEST -> isChestplate(s); case LEGS -> isLeggings(s); case FEET -> isBoots(s); default -> false; };
        return ok;   // R4: a piece at the durability floor keeps its Infinium perks and still counts toward its set
    }
    /** M4 (bug 5): a complete matching Infinium set is worn (SetLook != null). Gates the matrix screen, all Infinium HUD/inventory
     *  elements and crystal insertion. A mixed 4-piece combo that matches no set does NOT count. */
    public static boolean setWorn(LivingEntity e) { return SetLook.of(e) != null; }
    public static boolean fullSet(LivingEntity e) { if (HELMET == null) return false; for (EquipmentSlot sl : ARMOR_SLOTS) if (!wearingPiece(e, sl)) return false; return true; }
    /** Piece counts toward a style set: Infinity pieces count as Singularity AND Cyber Psycho (never Astral/Cybernetic). */
    public static boolean countsAs(ItemStack piece, InfiniumStyle set) {
        if (isInfinity(piece)) return true;
        InfiniumStyle st = InfiniumStyle.get(piece);
        return st == set || (set == InfiniumStyle.ASTRAL && st == InfiniumStyle.SINGULARITY) || (set == InfiniumStyle.CYBERNETIC && st == InfiniumStyle.CYBERPSYCHO);
    }
    public static boolean styledSet(LivingEntity e, InfiniumStyle set) {
        if (!fullSet(e)) return false;
        for (EquipmentSlot sl : ARMOR_SLOTS) if (!countsAs(e.getItemBySlot(sl), set)) return false;
        return true;
    }
    public static boolean infinitySet(LivingEntity e) {
        if (!fullSet(e)) return false;
        for (EquipmentSlot sl : ARMOR_SLOTS) if (!isInfinity(e.getItemBySlot(sl))) return false;
        return true;
    }
    public static boolean cyberPsychoSet(LivingEntity e) { return styledSet(e, InfiniumStyle.CYBERPSYCHO); }
    /** Leggings speed (land + water, velocity-based, no FOV change): Infinity 20 %, Cyber Psycho 15 %, Cybernetic 10 %, any piece 5 %.
     *  Set values are gated by the current set's leggings-speed perk, the base 5 % by the piece's own perk (13.14). */
    public static float legSpeedFactor(LivingEntity e) {
        // R2: per piece, from the leggings' own family (Astral/Singularity 5, Cybernetic 10, Cyber Psycho 15, Infinity 20 %).
        int fam = Perk.family(e.getItemBySlot(EquipmentSlot.LEGS));
        if (fam < 0 || !Perks.on(e, Perk.piece(fam, EquipmentSlot.LEGS))) return 1F;
        return (float) (1 + PerkValues.legSpeed(PerkValues.Family.values()[fam]));
    }
    public static double bootJumpBoost(ItemStack boots) {
        if (boots == null || !isBoots(boots) || InfiniumDurability.disabled(boots)) return 0.0;
        // M4: matches the boot tooltips in Info.pieceLine: Astral +1, Singularity +2, Infinity +3 blocks.
        // Cybernetic / Cyber Psycho boots have no per-piece jump (their jump is the set Jump perk).
        return switch (Perk.family(boots)) {
            case 0 -> PerkValues.JUMP_CYBERNETIC;   // +0.16310 (+1 block)
            case 2 -> PerkValues.JUMP_CYBERPSYCHO;  // +0.29539 (+2 blocks)
            case 4 -> PerkValues.JUMP_INFINITY;     // +0.41076 (+3 blocks)
            default -> 0.0;
        };
    }

    public static double bootSafeFallDistance(ItemStack boots) {
        if (boots == null || !isBoots(boots) || InfiniumDurability.disabled(boots)) return 0.0;
        if (isInfinity(boots)) return 3.0; // +3 blocks safe fall
        InfiniumStyle st = InfiniumStyle.get(boots);
        if (st == InfiniumStyle.SINGULARITY || st == InfiniumStyle.CYBERPSYCHO) return 2.0; // +2 blocks
        if (st == InfiniumStyle.ASTRAL || st == InfiniumStyle.CYBERNETIC) return 1.0; // +1 block
        return 0.0;
    }

    /** Legacy single-item check (bread boots). */
    public static boolean wearing(LivingEntity e, EquipmentSlot slot, Item item) { return e.getItemBySlot(slot).is(item); }
    /** Sword hit damage without a set bonus: item modifier (MAT 5 + 4 = 9) + player base 1 = 10 (shown as "10 Attack Damage"). */
    public static final float SWORD_BASE_DAMAGE = 10.0F;
    public static float getSwordBaseAttackDamageAttribute() {
        return 1.0F + MAT.attackDamageBonus() + 4.0F; // 10.0F
    }

    /** Sword set bonus (locked 2026-10-28): Tier 1 (no set, Astral, Cybernetic) +0, Tier 2 (Singularity, Cyber Psycho) +1, Tier 3 (Infinity) +2. */
    public static float swordSetBonus(SetLook l) {
        if (l == SetLook.INFINITY) return 2.0F;
        if (l == SetLook.SINGULARITY || l == SetLook.CYBERPSYCHO) return 1.0F;
        return 0.0F;
    }

    /** Per-set sword base damage: 10 / 11 / 12. Energy slash and beam percentages use this. */
    public static float swordBaseDamage(LivingEntity e) {
        return SWORD_BASE_DAMAGE + swordSetBonus(SetLook.of(e));
    }
}

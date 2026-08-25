package maxsuperman.addons.roller.modules;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import maxsuperman.addons.roller.gui.screens.EnchantmentSelectScreen;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import org.apache.commons.io.FilenameUtils;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VillagerRoller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSound = settings.createGroup("Sound");
    private final SettingGroup sgChatFeedback = settings.createGroup("Chat feedback", false);

    private final Setting<Boolean> disableIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-when-found")
        .description("Disable enchantment from list if found")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectIfFound = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-when-found")
        .description("Disconnect from server when enchantment from list if found")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> saveListToConfig = sgGeneral.add(new BoolSetting.Builder()
        .name("save-list-to-config")
        .description("Toggles saving and loading of rolling list to config and copypaste buffer")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePlaySound = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-sound")
        .description("Plays sound when it finds desired trade")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<SoundEvent>> sound = sgSound.add(new SoundEventListSetting.Builder()
        .name("sound-to-play")
        .description("Sound that will be played when desired trade is found if enabled")
        .defaultValue(Collections.singletonList(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK))
        .build()
    );

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("sound-pitch")
        .description("Playing sound pitch")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("Playing sound volume")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> pauseOnScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-screens")
        .description("Pauses rolling if any screen is open")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> headRotateOnPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place")
        .description("Look to the block while placing it?")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> failedToPlaceDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-fail-delay")
        .description("Delay after failed block place (milliseconds)")
        .defaultValue(1500)
        .min(0)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> failedToPlaceDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("place-fail-disable")
        .description("Disables roller if block placement fails")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxProfessionWaitTime = sgGeneral.add(new IntSetting.Builder()
        .name("max-profession-wait-time")
        .description("Time to wait if villager does not take profession (milliseconds). Zero = unlimited.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> onlyTradeable = sgGeneral.add(new BoolSetting.Builder()
        .name("only-tradeable")
        .description("Hide enchantments that are not marked as tradeable")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sortEnchantments = sgGeneral.add(new BoolSetting.Builder()
        .name("sort-enchantments")
        .description("Show enchantments sorted by their name")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantRebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("CivBreak")
        .description("Uses CivBreak to mine the lectern instantly. Best to just stay over the lectern slot.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> interactRetry = sgGeneral.add(new IntSetting.Builder()
        .name("interact-retry")
        .description("If server did not acknowledge villager interact packet, send another one after this many ticks. Zero = no retries.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> cfSetup = sgChatFeedback.add(new BoolSetting.Builder()
        .name("setup")
        .description("Hints on what to do in the beginning (otherwise denoted in modules list state)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfPausedOnScreen = sgChatFeedback.add(new BoolSetting.Builder()
        .name("paused-on-screen")
        .description("Rolling paused, interact with villager to continue")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfLowerLevel = sgChatFeedback.add(new BoolSetting.Builder()
        .name("found-lower-level")
        .description("Found enchant %s but it is not max level: %d (max) > %d (found)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfTooExpensive = sgChatFeedback.add(new BoolSetting.Builder()
        .name("found-too-expensive")
        .description("Found enchant %s but it costs too much: %s (max price) < %d (cost)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfIgnored = sgChatFeedback.add(new BoolSetting.Builder()
        .name("found-not-on-the-list")
        .description("Found enchant %s but it is not in the list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfProfessionTimeout = sgChatFeedback.add(new BoolSetting.Builder()
        .name("profession-timeout")
        .description("Villager did not take profession within the specified time")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfPlaceFailed = sgChatFeedback.add(new BoolSetting.Builder()
        .name("place-failed")
        .description("Failed placing, can't place or can't get lectern to hotbar (they still trigger place-failed settings)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfDiscrepancy = sgChatFeedback.add(new BoolSetting.Builder()
        .name("discrepancy")
        .description("Somehow roller got into state it was not expecting (likely AC mess)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfSentRetryInteract = sgChatFeedback.add(new BoolSetting.Builder()
        .name("sent-retry-interact")
        .description("Lets you know server dropping initial interact packets and additional was sent.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> cfBlockPlaceBounce = sgChatFeedback.add(new BoolSetting.Builder()
        .name("block-place-bounce")
        .description("Lets you know if placement was momentarily reverted and then placed again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cfFoundMatching = sgChatFeedback.add(new BoolSetting.Builder()
        .name("found-matching")
        .description("Lets you know what was found before stopping.")
        .defaultValue(true)
        .build()
    );

    private enum State {
        DISABLED,
        WAITING_FOR_TARGET_BLOCK,
        WAITING_FOR_TARGET_VILLAGER,
        ROLLING_BREAKING_BLOCK,
        ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR,
        ROLLING_PLACING_BLOCK,
        ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW,
        ROLLING_WAITING_FOR_VILLAGER_TRADES
    }

    private static final Path CONFIG_PATH = MeteorClient.FOLDER.toPath().resolve("VillagerRoller");
    private State currentState = State.DISABLED;
    private VillagerEntity rollingVillager;
    private BlockPos rollingBlockPos;
    private Block rollingBlock;
    private final List<RollingEnchantment> searchingEnchants = new ArrayList<>();
    private long failedToPlacePrevMsg = System.currentTimeMillis();
    private long currentProfessionWaitTime;

    public VillagerRoller() {
        super(Categories.Misc, "villager-roller", "Rolls trades.");
    }

    @Override
    public void onActivate() {
        if (toggleOnBindRelease) {
            toggleOnBindRelease = false;
            if (cfSetup.get()) {
                warning("You had 'Toggle on bind release' set to true, I just saved you some troubleshooting by turning it off");
            }
        }
        currentState = State.WAITING_FOR_TARGET_BLOCK; // Induláskor az első állapot, hogy megtudja melyik block a pulpitus
        if (cfSetup.get()) {
            info("Attack block you want to roll");
        }
    }

    @Override
    public void onDeactivate() {
        currentState = State.DISABLED;
    }

    @Override
    public String getInfoString() {
        return currentState.toString();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        if (saveListToConfig.get()) {
            NbtList l = new NbtList();
            for (RollingEnchantment e : searchingEnchants) {
                l.add(e.toTag());
            }
            tag.put("rolling", l);
        }
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        if (saveListToConfig.get()) {
            NbtList l = tag.getListOrEmpty("rolling");
            searchingEnchants.clear();
            for (NbtElement e : l) {
                if (e.getType() != NbtElement.COMPOUND_TYPE) {
                    info("Invalid list element");
                    continue;
                }
                searchingEnchants.add(new RollingEnchantment().fromTag((NbtCompound) e));
            }
        }
        return this;
    }

    private boolean loadSearchingFromFile(File f) {
        if (!f.exists() || !f.canRead()) {
            error("File does not exist or can not be loaded");
            return false;
        }
        NbtCompound r = null;
        try {
            r = NbtIo.read(f.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (r == null) {
            error("Failed to load nbt from file");
            return false;
        }
        NbtList l = r.getListOrEmpty("rolling");
        searchingEnchants.clear();
        for (NbtElement e : l) {
            if (e.getType() != NbtElement.COMPOUND_TYPE) {
                error("Invalid list element");
                return false;
            }
            searchingEnchants.add(new RollingEnchantment().fromTag((NbtCompound) e));
        }
        return true;
    }

    public boolean saveSearchingToFile(File f) {
        NbtList l = new NbtList();
        for (RollingEnchantment e : searchingEnchants) {
            l.add(e.toTag());
        }
        NbtCompound c = new NbtCompound();
        c.put("rolling", l);
        if (Files.notExists(f.getParentFile().toPath()) && !f.getParentFile().mkdirs()) {
            error("Failed to make directories");
            return false;
        }
        try {
            NbtIo.write(c, f.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        fillWidget(theme, list);
        return list;
    }

    private void fillWidget(GuiTheme theme, WVerticalList list) {
        WSection loadDataSection = list.add(theme.section("Config Saving")).expandX().widget();

        WTable control = loadDataSection.add(theme.table()).expandX().widget();

        WTextBox savedConfigName = control.add(theme.textBox("default")).expandWidgetX().expandCellX().expandX().widget();
        WButton save = control.add(theme.button("Save")).expandX().widget();
        save.action = () -> {
            if (saveSearchingToFile(new File(new File(MeteorClient.FOLDER, "VillagerRoller"), savedConfigName.get() + ".nbt"))) {
                info("Saved successfully");
            } else {
                error("Save failed");
            }
            list.clear();
            fillWidget(theme, list);
        };
        control.row();

        ArrayList<String> configs = new ArrayList<>();
        if (Files.notExists(CONFIG_PATH)) {
            if (!CONFIG_PATH.toFile().mkdirs()) error("Failed to create directory [{}]", CONFIG_PATH);
        } else {
            try (DirectoryStream<Path> configDir = Files.newDirectoryStream(CONFIG_PATH)) {
                for (Path config : configDir) {
                    configs.add(FilenameUtils.removeExtension(config.getFileName().toString()));
                }
            } catch (IOException e) {
                error("Failed to list directory", e);
            }
        }
        if (!configs.isEmpty()) {
            WDropdown<String> loadedConfigName = control.add(theme.dropdown(configs.toArray(new String[0]), "default")).expandWidgetX().expandCellX().expandX().widget();
            WButton load = control.add(theme.button("Load")).expandX().widget();
            load.action = () -> {
                if (loadSearchingFromFile(new File(new File(MeteorClient.FOLDER, "VillagerRoller"), loadedConfigName.get() + ".nbt"))) {
                    list.clear();
                    fillWidget(theme, list);
                    info("Loaded successfully");
                } else {
                    error("Failed to load file.");
                }
            };
        }

        WSection enchantments = list.add(theme.section("Enchantments")).expandX().widget();

        WTable table = enchantments.add(theme.table()).expandX().widget();
        table.add(theme.item(Items.BOOK.getDefaultStack()));
        table.add(theme.label("Enchantment"));
        table.add(theme.label("Level"));
        table.add(theme.label("Cost"));
        table.add(theme.label("Enabled"));
        table.add(theme.label("Remove"));
        table.row();
        if (sortEnchantments.get()) {
            searchingEnchants.removeIf(ench -> ench.enchantment == null);
            searchingEnchants.sort(Comparator.comparing(o -> o.enchantment));
        }

        Optional<Registry<Enchantment>> reg;
        if (mc.world != null) {
            reg = mc.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
        } else {
            reg = Optional.empty();
        }

        for (int i = 0; i < searchingEnchants.size(); i++) {
            RollingEnchantment e = searchingEnchants.get(i);
            Optional<RegistryEntry.Reference<Enchantment>> en;
            if (reg.isPresent()) {
                en = reg.get().getEntry(e.enchantment);
            } else {
                en = Optional.empty();
            }
            final int si = i;
            ItemStack book = Items.ENCHANTED_BOOK.getDefaultStack();
            int maxlevel = 255;
            if (en.isPresent()) { // Ha megtaláltuk a keresett Enchantmentet
                book = EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(en.get(), en.get().value().getMaxLevel()));
                maxlevel = en.get().value().getMaxLevel();
            }
            table.add(theme.item(book));

            WHorizontalList label = theme.horizontalList();
            WButton c = label.add(theme.button("Change")).widget();
            c.action = () -> mc.setScreen(new EnchantmentSelectScreen(theme, onlyTradeable.get(), sel -> {
                searchingEnchants.set(si, sel);
                list.clear();
                fillWidget(theme, list);
            }));
            if (en.isPresent()) {
                label.add(theme.label(Names.get(en.get())));
            } else {
                label.add(theme.label(e.enchantment.toString()));
            }
            table.add(label);

            WIntEdit lev = table.add(theme.intEdit(e.minLevel, 0, maxlevel, true)).minWidth(40).expandX().widget();
            lev.action = () -> e.minLevel = lev.get();
            //lev.tooltip = "Minimum enchantment level, 0 acts as maximum possible only (for custom 0 acts like 1)";
            // "Minimum enchantment level. Set to 0 for maximum level (defaults to 1 for custom enchantments)."
            lev.tooltip = "Minimum enchantment level to search for. Entering 0 automatically selects the maximum possible level (for custom enchantments, 0 defaults to level 1).";

            WHorizontalList costbox = table.add(theme.horizontalList()).minWidth(50).expandX().widget();
            WIntEdit cost = costbox.add(theme.intEdit(e.maxCost, 0, 64, false)).minWidth(40).expandX().widget();
            // Itt figyelni kellene hogy 0; minimum és maximum limiteken le lépjen túl
            // Ugyanígy az optimalt át kell írni mimimumra, és az optimális ár a max-minimum 25%-a
            // Minimum: (2+3*level)*treause
            // Maximum: Min(64,(6+13*level)*treasure))
            // Optimum: Mimimum + ((Max-Min)*0.25) => (Min*3+Max)/4
            cost.action = () -> e.maxCost = cost.get();
            /*
            // Ehhez rögzítenin kell a mincost és maxcost értékeket is
            cost.action = () -> {
                int val = cost.get();

                if (val > 1 && val < minCost) {
                    val = minCost;
                    cost.set(minCost);
                } else if ((val < 0) || (val=1)) {
                    val = 0;
                    cost.set(0);
                } else if (val > maxCost) {
                    val = maxCost;
                    cost.set(maxCost);
                }

                e.maxCost = val;
            };
            */
            cost.tooltip = "Maximum cost in emeralds, 0 means no limit";
            
            // Ez nem Optimal, hanem minimum! Az Optimal Price képletét fent írtam.
            WButton setOptimal = costbox.add(theme.button("O")).widget();
            setOptimal.tooltip = "Set to optimal price (2 + maxLevel*3) (double if treasure) (if known)";
            setOptimal.action = () -> {
                list.clear();
                en.ifPresent(enchantmentReference -> e.maxCost = getMinimumPrice(enchantmentReference));
                fillWidget(theme, list);
            };

            WCheckbox enabled = table.add(theme.checkbox(e.enabled)).widget();
            enabled.action = () -> e.enabled = enabled.checked;
            enabled.tooltip = "Enabled?";

            WMinus del = table.add(theme.minus()).widget();
            del.action = () -> {
                list.clear();
                searchingEnchants.remove(e);
                fillWidget(theme, list);
            };
            table.row();
        }

        WTable controls = list.add(theme.table()).expandX().widget();

        WButton removeAll = controls.add(theme.button("Remove all")).expandX().widget(); // Minden okosság elvétele
        removeAll.action = () -> {
            list.clear();
            searchingEnchants.clear();
            fillWidget(theme, list);
        };

        WButton add = controls.add(theme.button("Add")).expandX().widget(); // Egy okosság hozzáadása
        add.action = () -> mc.setScreen(new EnchantmentSelectScreen(theme, onlyTradeable.get(), e -> {
            e.minLevel = 1;
            e.maxCost = 64;
            e.enabled = true;
            searchingEnchants.add(e);
            list.clear();
            fillWidget(theme, list);
        }));

        WButton addAll = controls.add(theme.button("Add all")).expandX().widget(); // Minden okosság hozzáadása, maximális szinten, a legalacsonyabb áron
        addAll.action = () -> {
            list.clear();
            searchingEnchants.clear();
            if (reg.isPresent()) {
                for (RegistryEntry<Enchantment> e : getEnchants(onlyTradeable.get())) {
                    searchingEnchants.add(new RollingEnchantment(reg.get().getId(e.value()), e.value().getMaxLevel(), getMinimumPrice(e), true));
                }
            }
            fillWidget(theme, list);
        };
        controls.row();

        WButton setOptimalForAll = controls.add(theme.button("Set optimal for all")).expandX().widget(); //Adott okosság, adott szinten beállítani a legalacsonyabb árat
        setOptimalForAll.action = () -> {
            list.clear();
            if (reg.isPresent()) {
                for (RollingEnchantment e : searchingEnchants) {
                    reg.get().getEntry(e.enchantment).ifPresent(enchantmentReference -> e.maxCost = getMinimumPrice(enchantmentReference));
                }
            }
            fillWidget(theme, list);
        };

        WButton priceBumpUp = controls.add(theme.button("+1 to price for all")).expandX().widget(); // Ár növelése 1-el
        priceBumpUp.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                if (e.maxCost < 64) e.maxCost++;
            }
            fillWidget(theme, list);
        };

        WButton priceBumpDown = controls.add(theme.button("-1 to price for all")).expandX().widget(); // Ár csökkentése 1-el
        priceBumpDown.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                if (e.maxCost > 0) e.maxCost--;
            }
            fillWidget(theme, list);
        };
        controls.row();

        WButton setZeroForAll = controls.add(theme.button("Set zero price for all")).expandX().widget(); // Összes árának 0-sa, azaz bármely árat elfogad
        setZeroForAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.maxCost = 0;
            }
            fillWidget(theme, list);
        };

        WButton enableAll = controls.add(theme.button("Enable all")).expandX().widget(); // Összeset engedélyezi
        enableAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.enabled = true;
            }
            fillWidget(theme, list);
        };

        WButton disableAll = controls.add(theme.button("Disable all")).expandX().widget(); // Összeset letiltja
        disableAll.action = () -> {
            list.clear();
            for (RollingEnchantment e : searchingEnchants) {
                e.enabled = false;
            }
            fillWidget(theme, list);
        };
        controls.row();

    }

    public List<RegistryEntry<Enchantment>> getEnchants(boolean onlyTradeable) { // okosságok lekérése
        // Elvileg kiegészíthetnénk a listát a már kifűzött adatokkal, hogy ne kelljen mélyében turkálni.
        if (mc.world == null) {
            return Collections.emptyList();
        }
        var reg = mc.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT); // Összes okosság lekérése
        if (reg.isEmpty()) {
            return Collections.emptyList();
        }
        List<RegistryEntry<Enchantment>> available = new ArrayList<>();
        if (onlyTradeable) { // Ha csak kereskedhető
            var i = reg.get().iterateEntries(EnchantmentTags.TRADEABLE);
            i.iterator().forEachRemaining(available::add);
            return available;
        } else { // Mindet lekéri. Ez azért kell, mert ha modolt a szerver, akkor lehet nem tradelhető is a falusinál
            for (var a : reg.get().getIndexedEntries()) {
                available.add(a);
            }
            return available;
        }
    }

    public static int getMinimumPrice(RegistryEntry<Enchantment> e) {
        if (e == null) return 0;
        return e.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE) ? (2 + 3 * e.value().getMaxLevel()) * 2 : 2 + 3 * e.value().getMaxLevel();
    }

    private long waitingForTradesTicks = 0;

    public void triggerInteract() {
        if (pauseOnScreen.get() && mc.currentScreen != null) {
            if (cfPausedOnScreen.get()) {
                info("Rolling paused, interact with villager to continue");
            }
        } else {
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d villagerPos = rollingVillager.getEyePos();
            EntityHitResult entityHitResult = ProjectileUtil.raycast(mc.player, playerPos, villagerPos, rollingVillager.getBoundingBox(), Entity::canHit, playerPos.squaredDistanceTo(villagerPos));
            if (entityHitResult == null) {
                // Raycast didn't find villager entity?
                mc.interactionManager.interactEntity(mc.player, rollingVillager, Hand.MAIN_HAND);
                waitingForTradesTicks = 0;
            } else {
                ActionResult actionResult = mc.interactionManager.interactEntityAtLocation(mc.player, rollingVillager, entityHitResult, Hand.MAIN_HAND);
                if (!actionResult.isAccepted()) {
                    mc.interactionManager.interactEntity(mc.player, rollingVillager, Hand.MAIN_HAND);
                    waitingForTradesTicks = 0;
                }
            }
        }
    }

    public List<Pair<RegistryEntry<Enchantment>, Integer>> getEnchants(ItemStack stack) {
        List<Pair<RegistryEntry<Enchantment>, Integer>> ret = new ArrayList<>();
        for (var e : EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries()) {
            ret.add(ObjectIntImmutablePair.of(e.getKey(), e.getIntValue()));
        }
        return ret;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (currentState != State.ROLLING_WAITING_FOR_VILLAGER_TRADES) return;
        if (!(event.packet instanceof SetTradeOffersS2CPacket p)) return;
        mc.executeSync(() -> triggerTradeCheck(p.getOffers()));
    }

    private class rawTradeItem {
        public String ID="";
        public int Count=0;

        // 1. Alapértelmezett üres konstruktor (ha kézzel hoznád létre)
        public rawTradeItem() {
        }

        // 2. Adat-alapú konstruktor (közvetlen értékekből)
        public rawTradeItem(String id, int count) {
            this.ID = id;
            this.Count = count;
        }

        // 3. Minecraft ItemStack-ből építkező konstruktor (1.21.x kompatibilis)
        public rawTradeItem(ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                // Megadja a pontos azonosítót (pl. "minecraft:paper" vagy "minecraft:enchanted_book")
                //this.ID = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                this.ID = stack.getItem().toString();
                this.Count = stack.getCount();
            }
        }
    }
    private class rawTrade {
        public rawTradeItem Buy1;
        public rawTradeItem Buy2;
        public rawTradeItem Sell;

        // 1. Konstruktor: Közvetlen rawTradeItem elemekből
        public rawTrade(rawTradeItem buy1, rawTradeItem buy2, rawTradeItem sell) {
            this.Buy1 = buy1;
            this.Buy2 = buy2;
            this.Sell = sell;
        }

        // 2. Konstruktor: Direct ItemStack-ekből
        public rawTrade(ItemStack buy1, ItemStack buy2, ItemStack sell) {
            this.Buy1 = (buy1 != null && !buy1.isEmpty()) ? new rawTradeItem(buy1) : null;
            this.Buy2 = (buy2 != null && !buy2.isEmpty()) ? new rawTradeItem(buy2) : null;
            this.Sell = (sell != null && !sell.isEmpty()) ? new rawTradeItem(sell) : null;
        }

        // 3. Konstruktor: Minecraft TradeOffer / MerchantOffer objektumból
        public rawTrade(TradeOffer offer) {
            // A modded/vanilla API-tól függően (pl. offer.getOriginalFirstBuyItem() / offer.getSellItem())
            //this(offer.getOriginalFirstBuyItem(), offer.getSecondBuyItem().orElse(TradedItem.EMPTY).ItemStack(), offer.getSellItem());
            this(offer.getOriginalFirstBuyItem(), offer.getDisplayedSecondBuyItem(), offer.getSellItem());
        }
    }
    private class extTrade {
        public rawTrade rTrade;
        public String Item="";
        public int Price = 0;
        public boolean Sell=false;
        public String sItem="";
        public int sPrice=0;
        public int Count=0;
        //public extEnchantList ByEnch;

        public extTrade(TradeOffer o) {
            this.rTrade=new rawTrade(o);
            //this.Sell=this.rTrade.Sell.isOf(Items.EMERALD);
            this.Sell=rTrade.Sell.ID=="minecraft:emerald";
            ItemStack By=o.getOriginalFirstBuyItem();
            ItemStack By2=o.getDisplayedSecondBuyItem();
            ItemStack Sll=o.getSellItem();
            ItemStack It=(this.Sell) ? Sll:By;
            //ItemStack Pr=(this.Sell) ? By:Sll;
            //this.Item=It.getItem().getName().getString();
            this.Item=It.getItemName().getString();
            this.Price=By.getCount();
            this.Count=Sll.getCount();
            //this.sItem=By2.getItem().getName().getString();
            this.sItem=By2.getItemName().getString();
            this.sPrice=By2.getCount();
            // És hol az enchantok? És minek az enchantjai?
        }
    }
    public void triggerTradeCheck(TradeOfferList l) {
        // Tárolni kéne még a falusit is...
        // Illetve a Tárgyat mint mutatót, és az enchantokat is mint mutatót
        //  meg kellene tárolni a falusit is... hogy kié a trade
        //  meg azt is, hogy fixált-e
        // trade adatok: Miket miért
        // 24 papír => Smari
        // Első tétel a kincs, második a smari de mivel mindig smari, ezért csak szám
        // 24 papír => 1
        // papír => 24  24 papír egy smari
        // Polc <= 9  9smari egy polc
        // Üveg <= 15:4   15 smari 4 üveg
        // EKönyv <= 10   10 smari (és egy könyv) az okoskönyv
        // EPáncél <= 10+dia 10 smari+1dia
        // EPáncél <= 15+2dia 15 smari+2dia
        // Lényeg, az ár mindenképp kiírva, akkor is ha 1. a másodlagos csak ha nagyobb. Ha több darabot kap, akkor : után
        // tárgy neve + ha smarit kap, akkor a tárgy darabszáma [:+ a másodlagos [száma]tárgya] 
        // tárgy neve + ha nem smarit kap, akkor a smari darabszáma + a másodlagos [száma]tárgya
        // Tehát irány, tárgy, ár, darab, kiegészítés tárgya, darabja 
        //   (az ár, darab a buy az ár, a sell a darab Ha 24 papírért két smarit kapok akkor 24:2-d az ára a smarinak)
        // Ha sell smaragd akkor irány =>, játékos elad; falusi megvesz
        //  Nem szokott lenni, hogy a tárgyat egyébb tárggyal adjuk.
        // Ezért inkább EPáncél<= 10+dia alak szokott lenni
        Boolean isPaper=false;
        Boolean isEnch=false;
        TradeOffer trPaper=null;
        TradeOffer trEnch=null;
        ItemStack itEnch=null;
        for (TradeOffer offer : l) {
            // Elsőnel betöltjük mind a három részét a Trade-nek... Persze kérdés, hogy kell-e a másodikat betölteni.. az csak enchantnál van.
            // Ha az első papír akkor jelöljük és betesszük a trPaper-be
            // Ha a harmadik enchantolt, akkor jelöljük, és betesszük az trEnch-be
            // Illetve ha úgy van beállítva akkor kiírjuk a talált Trade-t (Enchantnál Neve, Szint, Ára)
            // Ha a trade-ket mi akarjuk tárolni, akkor a sok változós helyett elég a mit mennyiért. kivéve az enchantnál, ott kell a szint is.
            ItemStack firstBuy = offer.getOriginalFirstBuyItem();
            ItemStack secondBuy = offer.getDisplayedSecondBuyItem();            
            ItemStack sellItem = offer.getSellItem();

            // logoljuk
            String secondStr = secondBuy.isEmpty() ? "" : secondBuy.getCount() + "x " + secondBuy.getItem().getName().getString() + "  ";
            String sTrade = firstBuy.getCount() + "x " + firstBuy.getItem().getName().getString() + "  " 
              + secondStr               
              + " Sell_Item:" + sellItem.getName().getString()
              //+ " Sell_ID2:" + sellItem.getItem().getString()              
              + " Sell_FrmName:" + sellItem.getFormattedName().getString()
              + " Sell:" + sellItem.toString()
              + sellItem.getCount() + "x " + sellItem.getItem().getName().getString();
            info(sTrade);
            
            if (firstBuy.isOf(Items.PAPER) && !isPaper) {
                isPaper=true;
                if (trPaper==null) {trPaper=offer;}
                //info(String.format("Found Paper on second Trade"));
            }
            
            if (sellItem.isOf(Items.ENCHANTED_BOOK) && !(sellItem.get(DataComponentTypes.STORED_ENCHANTMENTS) == null)){
                isEnch=true;
                if (trEnch==null) {
                    trEnch=offer;
                    itEnch=sellItem;
                }
            }

        }

        // Original Routine (Was in For Cycle, itEnch was sellItem)
        if (isEnch) {
            

        for (Pair<RegistryEntry<Enchantment>, Integer> enchant : getEnchants(itEnch)) {
            int enchantLevel = enchant.right();
            var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            String enchantIdString = reg.getId(enchant.key().value()).toString();
            String enchantName = Names.get(enchant.key());

            boolean found = false;
            for (RollingEnchantment e : searchingEnchants) {
                if (!e.enabled || !e.enchantment.toString().equals(enchantIdString)) continue;
                found = true;
                if (e.minLevel <= 0) { // wait for max level
                    int ml = enchant.key().value().getMaxLevel();
                    if (enchantLevel < ml) {
                        if (cfLowerLevel.get()) {
                            info(String.format("Found enchant %s but it is not max level: %d (max) > %d (found)",
                                enchantName, ml, enchantLevel));
                        }
                        continue;
                    }
                } else if (e.minLevel > enchantLevel) { //wait for bigger level
                    if (cfLowerLevel.get()) {
                        info(String.format("Found enchant %s but it has too low level: %d (requested level) > %d (rolled level)",
                            enchantName, e.minLevel, enchantLevel));
                    }
                    continue;
                }
                if (e.maxCost > 0 && trEnch.getOriginalFirstBuyItem().getCount() > e.maxCost) {
                    if (cfTooExpensive.get()) {
                        info(String.format("Found enchant %s but it costs too much: %s (max price) < %d (cost)",
                            enchantName, e.maxCost, trEnch.getOriginalFirstBuyItem().getCount()));
                    }
                    continue;
                }
                // not continoued so found the expected enchantment.
                if (disableIfFound.get()) e.enabled = false;
                if (cfFoundMatching.get()) {
                    info(String.format("Found matching enchant %s (level %d) for %d emeralds and stopped.",
                        //enchantName, enchantLevel, offer.getBaseCostA().getCount()));
                        enchantName, enchantLevel, trEnch.getOriginalFirstBuyItem().getCount()));
                }
                toggle(); // lekapcsolja a Villager Rollert
                if (enablePlaySound.get() && !sound.get().isEmpty()) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(sound.get().get(0),
                        soundPitch.get().floatValue(), soundVolume.get().floatValue()));
                }
                if (disconnectIfFound.get()) {
                    String levelText = (enchantLevel > 1 || enchant.key().value().getMaxLevel() > 1) ? " " + enchantLevel : "";
                    String message = String.format(
                        "%s[%s%s%s] Found enchant %s%s%s%s for %s%d%s emeralds and automatically disconnected.",
                        Formatting.GRAY,
                        Formatting.GREEN,
                        title,
                        Formatting.GRAY,
                        Formatting.WHITE,
                        enchantName,
                        levelText,
                        Formatting.GRAY,
                        Formatting.WHITE,
                        trEnch.getOriginalFirstBuyItem().getCount(),
                        Formatting.GRAY
                    );
                    mc.getNetworkHandler().getConnection().disconnect(Text.of(message));
                }
                break;
            }
            if (!found && cfIgnored.get()) {
                info(String.format("Found enchant %s but it is not in the list.", enchantName));
            }
        }}

        String Signals="There are";
        if (isPaper) {Signals+=" Paper";}
        if (isEnch) {Signals+=(isPaper ? " &" : "")+" Enchantment";}
        info(Signals);

        mc.player.closeHandledScreen();
        currentState = State.ROLLING_BREAKING_BLOCK;
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_VILLAGER) return;
        if (!(event.entity instanceof VillagerEntity villager)) return;

        rollingVillager = villager;
        currentState = State.ROLLING_BREAKING_BLOCK; // ennek a trade checknek kellene lennie...
        if (cfSetup.get()) {
            info("We got your villager");
        }
        event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onStartBreakingBlockEvent(StartBreakingBlockEvent event) {
        if (currentState != State.WAITING_FOR_TARGET_BLOCK) return;

        rollingBlockPos = event.blockPos;
        rollingBlock = mc.world.getBlockState(rollingBlockPos).getBlock();
        currentState = State.WAITING_FOR_TARGET_VILLAGER;
        if (instantRebreak.get()) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, rollingBlockPos, Direction.UP));
        }
        if (cfSetup.get()) {
            info("Rolling block selected, now interact with villager you want to roll");
        }
    }

    private void placeFailed(String msg) {
        if (failedToPlacePrevMsg + failedToPlaceDelay.get() <= System.currentTimeMillis()) {
            if (cfPlaceFailed.get()) {
                info(msg);
            }
            failedToPlacePrevMsg = System.currentTimeMillis();
        }
        if (failedToPlaceDisable.get()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        switch (currentState) {
            case ROLLING_BREAKING_BLOCK -> { // Kitöri a pulpitust
                if (instantRebreak.get()) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, rollingBlockPos, Direction.DOWN));
                }
                if (mc.world.getBlockState(rollingBlockPos) == Blocks.AIR.getDefaultState()) {
                    // info("Block is broken, waiting for villager to clean profession...");
                    currentState = State.ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR; // Következő állapot: Falusi vegye észre a pulpitus hiányát.
                } else if (!instantRebreak.get() && !BlockUtils.breakBlock(rollingBlockPos, true)) {
                    error("Can not break specified block");
                    toggle();
                }
            }
            case ROLLING_WAITING_FOR_VILLAGER_PROFESSION_CLEAR -> { // Falusi, nincs pulpitus!
                if (mc.world.getBlockState(rollingBlockPos).isOf(Blocks.LECTERN)) {
                    if (cfDiscrepancy.get()) {
                        info("Rolling block mining reverted?");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK; // Következő állapot (Még ott a pulpitus) Pulpitus törése
                    return;
                }
                rollingVillager.getVillagerData().profession().getKey().ifPresent(profession -> {
                    if (profession == VillagerProfession.NONE) {
                        // info("Profession cleared");
                        currentState = State.ROLLING_PLACING_BLOCK; // Következő állapot: Pulpitus lerakása
                    }
                });
            }            
            case ROLLING_PLACING_BLOCK -> { // Pulpitus lerakása
                if (mc.world.getBlockState(rollingBlockPos).isOf(Blocks.LECTERN)) { // Már ott van.
                    if (cfBlockPlaceBounce.get()) {
                        info("Lectern placement bounced?");
                    }
                    currentState = State.ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW; // Következő állapot (Már ott a pulpitus): Falusi vegye észre
                    return;
                }
                FindItemResult item = InvUtils.findInHotbar(rollingBlock.asItem()); // Pulpitus megkeresése a hotbar-on
                if (!item.found()) { // Nincs ott
                    placeFailed("Lectern not found in hotbar");
                    return;
                }
                if (!BlockUtils.canPlace(rollingBlockPos, true)) { // Nem tudja lerakni
                    placeFailed("Can't place lectern");
                    return;
                }
                if (!BlockUtils.place(rollingBlockPos, item, headRotateOnPlace.get(), 5)) { // nem tudta lerakni
                    placeFailed("Failed to place lectern");
                    return;
                }
                currentState = State.ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW; // következő állapot: Falusi vegye észre!
                if (maxProfessionWaitTime.get() > 0) {
                    currentProfessionWaitTime = System.currentTimeMillis(); // idő indítása
                }
            }
            case ROLLING_WAITING_FOR_VILLAGER_PROFESSION_NEW -> { // Vár hogy a Falusi észrevegye a pulpitust
                if (maxProfessionWaitTime.get() > 0 && (currentProfessionWaitTime + maxProfessionWaitTime.get() <= System.currentTimeMillis())) {
                    if (cfProfessionTimeout.get()) {
                        info("Villager did not take profession within the specified time");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK; // Következő lépés (nem vette észre sok ideig), pulpitus törése
                    return;
                }
                if (mc.world.getBlockState(rollingBlockPos) == Blocks.AIR.getDefaultState()) {
                    if (cfDiscrepancy.get()) {
                        info("Lectern placement reverted by server (AC?)");
                    }
                    currentState = State.ROLLING_PLACING_BLOCK; // Következő lépés (Nincs Pulpitus), Pulpitus lerakása
                    return;
                }
                if (!mc.world.getBlockState(rollingBlockPos).isOf(Blocks.LECTERN)) {
                    if (cfDiscrepancy.get()) {
                        info("Placed wrong block?!");
                    }
                    currentState = State.ROLLING_BREAKING_BLOCK; // Következő lépés (Nem pulpitus), Block törése
                    return;
                }
                rollingVillager.getVillagerData().profession().getKey().ifPresent(profession -> {
                    if (profession != VillagerProfession.NONE) {
                        currentState = State.ROLLING_WAITING_FOR_VILLAGER_TRADES; // Következő lépés, Kereskedésekre várva
                        triggerInteract();
                    }
                });
            }
            case ROLLING_WAITING_FOR_VILLAGER_TRADES -> { //Várakozik, és újraküldi a jobb gombot, amíg nem érkezett válasz.
                var retryTicks = interactRetry.get();
                if (retryTicks > 0) {
                    if (waitingForTradesTicks >= retryTicks) {
                        if (cfSentRetryInteract.get()) {
                            info("Sending another interact packet");
                        }
                        triggerInteract();
                    } else {
                        waitingForTradesTicks++;
                    }
                }
            }
            default -> {
                // Wait for another state
            }
        }
    }

    public static class RollingEnchantment implements ISerializable<RollingEnchantment> {
        private Identifier enchantment;
        private int minLevel;
        private int maxCost;
        private boolean enabled;

        public RollingEnchantment(Identifier enchantment, int minLevel, int maxCost, boolean enabled) {
            this.enchantment = enchantment;
            this.minLevel = minLevel;
            this.maxCost = maxCost;
            this.enabled = enabled;
        }

        public RollingEnchantment() {
            enchantment = Identifier.of("minecraft", "protection");
            minLevel = 0;
            maxCost = 0;
            enabled = false;
        }

        @Override
        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();
            tag.putString("enchantment", enchantment.toString());
            tag.putInt("minLevel", minLevel);
            tag.putInt("maxCost", maxCost);
            tag.putBoolean("enabled", enabled);
            return tag;
        }

        @Override
        public RollingEnchantment fromTag(NbtCompound tag) {
            enchantment = Identifier.tryParse(tag.getString("enchantment", ""));
            minLevel = tag.getInt("minLevel", 1);
            maxCost = tag.getInt("maxCost", 64);
            enabled = tag.getBoolean("enabled", true);
            return this;
        }
    }
}

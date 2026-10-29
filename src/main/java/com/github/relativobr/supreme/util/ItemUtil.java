package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.machine.AbstractQuarry;
import com.github.relativobr.supreme.machine.AbstractQuarryOutputItem;
import com.github.relativobr.supreme.machine.tech.MobTechGeneric;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactivity;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.github.relativobr.supreme.Supreme.getSupremeOptions;
import static com.github.relativobr.supreme.util.CompatibilySupremeLegacy.getNewIdSupremeLegacy;
import static com.github.relativobr.supreme.util.CompatibilySupremeLegacy.getOldIdSupremeLegacy;

public class ItemUtil {


    /** Applies the optional production limiter to generator output and buffers only. */
    public static int getValueGeneratorsWithLimit(int value) {
        return Math.min((getSupremeOptions().isLimitProductionGenerators() ? (value / 5) : value), 16000000);
    }

    /**
     * Capacitor capacity is storage, not generator production, and must never be reduced by the
     * production limiter or the historical 16 MJ generator ceiling.
     */
    public static int getCapacitorCapacity(int value) {
        return Math.max(1, value);
    }

    public static SupremeQuarryOutput getOutputQuarry(@Nonnull SlimefunItemStack item) {

        ConfigurationSection typeSection = Supreme.inst().getConfig()
                .getConfigurationSection("quarry-custom-output");

        if (typeSection == null) {
            Supreme.inst().log(Level.SEVERE,
                    "Config section \"quarry-custom-output\" missing, Check your config and report this!");
            return null;
        }

        // find path
        String itemPath = getNewIdSupremeLegacy(item.getItemId()).toLowerCase();
        ConfigurationSection itemSection = typeSection.getConfigurationSection(itemPath);

        List<AbstractQuarryOutputItem> outputItems = new ArrayList<>();
        if (itemSection != null) {
            int checkLimitChance = 0;
            for (int i = 1; i <= 12; i++) {
                ConfigurationSection itemConfig = itemSection.getConfigurationSection(String.valueOf(i));
                if (itemConfig == null || checkLimitChance >= 100) {
                    break;
                }
                int chance = itemConfig.getInt("chance");
                if (checkLimitChance + chance >= 100) {
                    chance = 100 - checkLimitChance;
                }
                final String itemId = itemConfig.getString("item");
                if (itemId == null) {
                    continue;
                }
                if (itemConfig.getBoolean("is-slimefun")) {
                    SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
                    //check Legacy Supreme
                    if (slimefunItem == null && getSupremeOptions().isUseLegacySupremeexpansionItemId()) {
                        slimefunItem = SlimefunItem.getById(getOldIdSupremeLegacy(itemId));
                    }
                    if (slimefunItem != null) {
                        outputItems.add(
                                AbstractQuarryOutputItem.builder().itemStack(slimefunItem.getItem().clone())
                                        .chance(getSupremeOptions().isLimitProductionQuarry() ? (chance / 2) : chance)
                                        .build());
                    }
                } else {
                    final Material material = Material.matchMaterial(itemId);
                    if (material != null) {
                        outputItems.add(AbstractQuarryOutputItem.builder().itemStack(new ItemStack(material, 1))
                                .chance(getSupremeOptions().isLimitProductionQuarry() ? (chance / 2) : chance)
                                .build());
                    }
                }
                checkLimitChance = checkLimitChance + chance;
            }
        } else {
            Supreme.inst().log(Level.SEVERE,
                    "Config section for " + itemPath + " missing, Check your config and report this!");
        }

        return SupremeQuarryOutput.builder().outputItems(outputItems).build();
    }

    public static void addLoreQuarry(@Nonnull AbstractQuarry quarry) {
        ItemStack item = quarry.getItem();
        ItemMeta meta = item.getItemMeta();
        // Historical layout: spacer, generated output chances, optional limiter, then original lore.
        List<String> generated = new ArrayList<>();
        generated.add(" ");
        final SupremeQuarryOutput output = quarry.getOutput();
        final List<AbstractQuarryOutputItem> outputItems = output.getOutputItems().stream().filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (AbstractQuarryOutputItem outputItem : outputItems) {
            if (outputItem != null && outputItem.getItemStack() != null && outputItem.getItemStack().getItemMeta() != null) {
                generated.add(formatQuarryOutputLine(outputItem.getItemStack(), outputItem.getChance()));
            }
        }

        if (getSupremeOptions().isLimitProductionQuarry()) {
            generated.add(" ");
            generated.add(LegacyComponentSerializer.legacySection()
                    .serialize(Component.text("Limit Production Quarry", NamedTextColor.GOLD)));
        }

        prependQuarryLore(meta, generated);
        item.setItemMeta(meta);
    }

    /** Preserve the original legacy-colored chance line without deprecated ItemMeta String accessors. */
    static String formatQuarryOutputLine(ItemStack output, int chance) {
        ItemMeta outputMeta = output.getItemMeta();
        String name = output.getType().name();
        if (outputMeta.hasDisplayName()) {
            name = LegacyComponentSerializer.legacySection().serialize(outputMeta.displayName());
        } else {
            name = name.replace("_", " ");
            name = name.substring(0, 1).toUpperCase().concat(name.substring(1).toLowerCase());
            name = LegacyComponentSerializer.legacySection().serialize(Component.text(name, NamedTextColor.AQUA));
        }
        return name + " " + LegacyComponentSerializer.legacySection()
                .serialize(Component.text(chance + "%", NamedTextColor.YELLOW));
    }

    /**
     * The generated quarry header has historically appeared before old item lore.
     * Preserve pre-existing Adventure components (including events, fonts and data) without
     * flattening stored items to deprecated String lore or changing the original line order.
     */
    static void prependQuarryLore(ItemMeta meta, List<String> generated) {
        List<Component> original = meta.lore();
        List<Component> result = new ArrayList<>(generated.size() + (original == null ? 0 : original.size()));
        for (String line : generated) {
            result.add(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        if (original != null) {
            result.addAll(original);
        }
        meta.lore(result);
    }


    public static ItemStack getItemQuarry(SupremeQuarryOutput output, int randomInt) {
        AbstractQuarryOutputItem selected = QuarryRollSelection.select(
                output.getOutputItems(), AbstractQuarryOutputItem::getChance, randomInt);
        return selected == null ? null : selected.getItemStack();
    }


    public static String buildNameTier(String newName, Integer tier) {
        switch (tier) {
            case 1:
                return legacyTierName(newName, " I", NamedTextColor.GRAY);
            case 2:
                return legacyTierName(newName, " II", NamedTextColor.DARK_GREEN);
            case 3:
                return legacyTierName(newName, " III", NamedTextColor.GREEN);
            case 4:
                return legacyTierName(newName, " IV", NamedTextColor.DARK_BLUE);
            case 5:
                return legacyTierName(newName, " V", NamedTextColor.BLUE);
            case 6:
                return legacyTierName(newName, " VI", NamedTextColor.GOLD);
            case 7:
                return legacyTierName(newName, " VII", NamedTextColor.YELLOW);
            case 8:
                return legacyTierName(newName, " VIII", NamedTextColor.DARK_RED);
            case 9:
                return legacyTierName(newName, " IX", NamedTextColor.DARK_PURPLE);
            default:
                return legacyTierName(newName, "", NamedTextColor.DARK_GRAY);
        }
    }

    /** Construct new template labels using the same section colors as the historical String API. */
    private static String legacyTierName(String name, String suffix, NamedTextColor color) {
        return LegacyComponentSerializer.legacySection().serialize(Component.text(name + suffix, color));
    }

    public static String buildIdTier(String newName, Integer tier) {
        switch (tier) {
            case 1:
                return newName + "_I";
            case 2:
                return newName + "_II";
            case 3:
                return newName + "_III";
            case 4:
                return newName + "_IV";
            case 5:
                return newName + "_V";
            case 6:
                return newName + "_VI";
            case 7:
                return newName + "_VII";
            case 8:
                return newName + "_VIII";
            case 9:
                return newName + "_IX";
            default:
                return newName + "_0";
        }
    }

    public static String buildLoreRadioactivityType(MobTechGeneric.MobTechType mobTechType) {
        Radioactivity radioactivity;
        switch (mobTechType) {
            case MUTATION_INTELLIGENCE:
            case MUTATION_BERSERK:
            case MUTATION_LUCK:
                radioactivity = Radioactivity.VERY_DEADLY;
                break;
            case ROBOTIC_CLONING:
            case ROBOTIC_ACCELERATION:
            case ROBOTIC_EFFICIENCY:
                radioactivity = Radioactivity.HIGH;
                break;
            case SIMPLE:
            default:
                radioactivity = Radioactivity.LOW;
        }
        return radioactivity.getLore();
    }

    /**
     * Keep generated mob-tech lore as legacy section-formatted Strings. Existing saved items,
     * SlimefunItemStack constructors and the public buildLoreType descriptor depend on these
     * exact section-code sequences, including repeated color codes between fragments.
     */
    public static String buildLoreType(MobTechGeneric.MobTechType mobTechType, Integer tier) {
        String tierString = String.valueOf(tier + 1);
        switch (mobTechType) {
            case MUTATION_BERSERK:
            case ROBOTIC_ACCELERATION:
                return legacyColoredText(tierString + "x ", NamedTextColor.GREEN)
                        + legacyColoredText("increase speed ", NamedTextColor.GRAY)
                        + legacyColoredText(tierString + "0%", NamedTextColor.RED)
                        + legacyColoredText(" increase energy", NamedTextColor.GRAY);
            case MUTATION_LUCK:
            case ROBOTIC_CLONING:
                return buildLoreTypeLuckAndCloning(tier);
            case MUTATION_INTELLIGENCE:
            case ROBOTIC_EFFICIENCY:
                return legacyColoredText(tierString + "0% ", NamedTextColor.GREEN)
                        + legacyColoredText(" decrease energy", NamedTextColor.GRAY);
            case SIMPLE:
            default:
                return legacyColoredText("Increases processing speed", NamedTextColor.GRAY);
        }
    }

    private static String buildLoreTypeLuckAndCloning(Integer tier) {
        int bonus = tier >= 9 ? 4 : tier >= 6 ? 3 : tier >= 4 ? 2 : 1;
        return legacyColoredText("+" + bonus + " stack", NamedTextColor.GREEN)
                + legacyColoredText(" clone", NamedTextColor.GRAY);
    }

    /** Serialize each historical color fragment individually, preserving its explicit prefix. */
    private static String legacyColoredText(String text, NamedTextColor color) {
        return LegacyComponentSerializer.legacySection().serialize(Component.text(text, color));
    }

    private static String buildLoreTypeAmount(MobTechGeneric.MobTechType mobTechType, Integer tier) {
        String tierString = String.valueOf(tier + 1);
        switch (mobTechType) {
            case MUTATION_BERSERK:
            case ROBOTIC_ACCELERATION:
            case MUTATION_INTELLIGENCE:
            case ROBOTIC_EFFICIENCY:
                return legacyColoredText("Value process ", NamedTextColor.GRAY)
                        + legacyColoredText("(" + tierString + " * amount stack * 0.15625)",
                                NamedTextColor.YELLOW);
            case MUTATION_LUCK:
            case ROBOTIC_CLONING:
                return legacyColoredText("Value process ", NamedTextColor.GRAY)
                        + legacyColoredText("(" + Supreme.getSupremeOptions().getMaxAmountTechGenerator()
                                + "x per stack)", NamedTextColor.YELLOW);
            case SIMPLE:
            default:
                return legacyColoredText("Value process ", NamedTextColor.GRAY)
                        + legacyColoredText("(amount stack * 0.15625)", NamedTextColor.YELLOW);
        }
    }

    public static SlimefunItemStack buildItemFromMobTechDTO(MobTechGeneric mobTechGeneric, Integer tier) {
        SlimefunItemStack itemStack = new SlimefunItemStack(buildIdTier(mobTechGeneric.getId(), tier),
                mobTechGeneric.getTexture(),
                buildNameTier(mobTechGeneric.getName(), tier), "",
                buildLoreRadioactivityType(mobTechGeneric.getMobTechType()),
                buildLoreType(mobTechGeneric.getMobTechType(), tier),
                buildLoreTypeAmount(mobTechGeneric.getMobTechType(), tier), "", "&3Supreme Component");
        ItemMeta itemMeta = itemStack.getItemMeta();
        PersistentDataAPI.setInt(itemMeta, new NamespacedKey(Supreme.inst(), "mob_tech_tier"), tier);
        PersistentDataAPI.setString(itemMeta, new NamespacedKey(Supreme.inst(), "mob_tech_type"), mobTechGeneric.getMobTechType().name());
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}

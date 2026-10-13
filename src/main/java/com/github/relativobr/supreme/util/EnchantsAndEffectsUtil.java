package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.gear.SupremeSoulboundGear;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

import static com.github.relativobr.supreme.util.CompatibilySupremeLegacy.getNewIdSupremeLegacy;

public class EnchantsAndEffectsUtil {

    public static void addGearThorniumEnchants(@Nonnull SlimefunItemStack... items) {

        ConfigurationSection typeSection = Supreme.inst().getConfig().getConfigurationSection("supreme-enchant");

        if (typeSection == null) {
            Supreme.inst().log(Level.SEVERE,
                    "Config section \"supreme-enchant\" missing, Check your config and report this!");
            return;
        }

        for (SlimefunItemStack item : items) {

            ItemMeta meta = item.getItemMeta();

            // Generate only the new lines; retain existing component lore without flattening it.
            List<String> lore = new ArrayList<>();

            // Slimefun's lore fallback recognizes the canonical gray marker, not an aqua label.
            lore.add(ChatColor.GRAY + "Soulbound");
            SupremeSoulboundGear.markTemplate(meta, Slimefun.getRegistry().getSoulboundDataKey());

            // find path
            String itemPath = getNewIdSupremeLegacy(item.getItemId()).toLowerCase();

            buildLoreEffects(lore, itemPath);

            GearLore.append(meta, lore);

            ConfigurationSection itemSection = typeSection.getConfigurationSection(itemPath);
            if (itemSection != null) {
                // unbreakable and enchants
                meta.setUnbreakable(itemSection.getBoolean("unbreakable"));
                for (Map.Entry<Enchantment, Integer> entry : getEnchants(itemSection).entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            } else {
                Supreme.inst().log(Level.SEVERE,
                        "Config section for " + itemPath + " missing, Check your config and report this!");
            }

            // add meta
            item.setItemMeta(meta);

        }
    }

    public static void addGearBasicEnchants(@Nonnull SlimefunItemStack... items) {
        ConfigurationSection typeSection = Supreme.inst().getConfig().getConfigurationSection("supreme-enchant");

        if (typeSection == null) {
            Supreme.inst().log(Level.SEVERE,
                    "Config section \"supreme-enchant\" missing, Check your config and report this!");
            return;
        }

        for (SlimefunItemStack item : items) {

            ItemMeta meta = item.getItemMeta();

            // Generate only the new lines; retain existing component lore without flattening it.
            List<String> lore = new ArrayList<>();

            // Slimefun's lore fallback recognizes the canonical gray marker, not an aqua label.
            lore.add(ChatColor.GRAY + "Soulbound");
            SupremeSoulboundGear.markTemplate(meta, Slimefun.getRegistry().getSoulboundDataKey());

            // find path
            String itemPath = getNewIdSupremeLegacy(item.getItemId()).toLowerCase();

            GearLore.append(meta, lore);

            ConfigurationSection itemSection = typeSection.getConfigurationSection(itemPath);
            if (itemSection != null) {
                // unbreakable and enchants
                meta.setUnbreakable(itemSection.getBoolean("unbreakable"));
                for (Map.Entry<Enchantment, Integer> entry : getEnchants(itemSection).entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            } else {
                Supreme.inst().log(Level.SEVERE,
                        "Config section for " + itemPath + " missing, Check your config and report this!");
            }

            // add meta
            item.setItemMeta(meta);

        }
    }

    @Nonnull
    static Map<Enchantment, Integer> getEnchants(@Nonnull ConfigurationSection section) {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        for (String path : section.getKeys(false)) {
            if ("unbreakable".equalsIgnoreCase(path)) {
                continue;
            }

            int level = section.getInt(path);
            if (level == 0) {
                continue;
            }
            Enchantment enchantment = SupremeEnchantmentResolver.resolve(path);
            if (enchantment == null) {
                Supreme plugin = Supreme.inst();
                if (plugin != null) {
                    plugin.log(Level.WARNING, "Unknown supreme-enchant key '" + path
                            + "' in " + section.getCurrentPath() + "; skipping this enchantment.");
                }
                continue;
            }

            // Keep Supreme's historical configuration behavior for out-of-range levels.
            enchants.put(enchantment, level > 0 && level <= 100 ? level : 1);
        }
        return enchants;
    }


    public static PotionEffect[] getPotionEffects(SlimefunItemStack item) {
        ConfigurationSection settings = Supreme.inst().getConfig().getConfigurationSection("supreme-effect");
        if (settings == null) {
            Supreme.inst().log(Level.SEVERE,
                    "Config section \"supreme-effect\" missing; no armor potion effects can be applied.");
            return new PotionEffect[0];
        }
        return getPotionEffectsForItemPath(
                getNewIdSupremeLegacy(item.getItemId()).toLowerCase(Locale.ROOT), settings);
    }

    /**
     * Resolves a single Supreme gear type from the same config used for tooltips.
     * Kept separate from plugin setup so all armor tiers can be regression tested.
     */
    static PotionEffect[] getPotionEffectsForItemPath(String itemPath, ConfigurationSection settings) {
        if (settings == null || isBasicArmor(itemPath)) {
            return new PotionEffect[0];
        }
        int amplifier = getAmplifier(settings, itemPath);
        return getEffects(getEffectSection(settings, itemPath), amplifier);
    }

    private static void buildLoreEffects(List<String> lore, String itemPath) {
        ConfigurationSection settings = Supreme.inst().getConfig().getConfigurationSection("supreme-effect");
        if (settings == null) {
            Supreme.inst().log(Level.SEVERE,
                    "Config section \"supreme-effect\" missing; armor potion lore cannot be generated.");
            return;
        }
        lore.addAll(getEffectLoreForItemPath(itemPath, settings));
    }

    static List<String> getEffectLoreForItemPath(String itemPath, ConfigurationSection settings) {
        if (settings == null || isBasicArmor(itemPath)) {
            return List.of();
        }

        String level = buildLoreAmplifier(getAmplifier(settings, itemPath));
        List<String> lore = new ArrayList<>();
        for (String effectName : getNamesEffects(getEffectSection(settings, itemPath))) {
            lore.add(ChatColor.DARK_PURPLE + effectName + level);
        }
        return lore;
    }

    private static boolean isBasicArmor(String itemPath) {
        return itemPath.contains("adamantium")
                || itemPath.contains("aurum")
                || itemPath.contains("titanium");
    }

    private static ConfigurationSection getEffectSection(ConfigurationSection settings, String itemPath) {
        if (itemPath.contains("helmet")) {
            return settings.getConfigurationSection("helmet");
        } else if (itemPath.contains("chestplate")) {
            return settings.getConfigurationSection("chestplate");
        } else if (itemPath.contains("leggings")) {
            return settings.getConfigurationSection("leggings");
        } else if (itemPath.contains("boots")) {
            return settings.getConfigurationSection("boots");
        }
        return null;
    }

    private static String buildLoreAmplifier(int amplifier) {
        // The effect amplifier is zero-based; support the historical 0..10 cap.
        String[] levels = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        return amplifier >= 0 && amplifier < levels.length ? " " + levels[amplifier] : "";
    }

    private static int getAmplifier(ConfigurationSection typeSection, String itemPath) {
        int amplifier = 0;
        if (itemPath.endsWith("supreme")) {
            amplifier = typeSection.getInt("amplifier_supreme", 3);
        } else if (itemPath.endsWith("legendary")) {
            amplifier = typeSection.getInt("amplifier_legendary", 3);
        } else if (itemPath.endsWith("epic")) {
            amplifier = typeSection.getInt("amplifier_epic", 2);
        } else if (itemPath.endsWith("rare")) {
            amplifier = typeSection.getInt("amplifier_rare", 2);
        } else if (itemPath.endsWith("magic")) {
            amplifier = typeSection.getInt("amplifier_magic", 1);
        } else if (itemPath.endsWith("thornium")) {
            amplifier = typeSection.getInt("amplifier_thornium", 1);
        }
        // fix start value 0
        amplifier--;
        // fix max value 10
        if (amplifier > 10) {
            amplifier = 10;
        }
        // fix min value 0
        if (amplifier < 0) {
            amplifier = 0;
        }
        return amplifier;
    }

    private static final Set<String> WARNED_EFFECT_KEYS = new HashSet<>();

    private static PotionEffectType resolveEffect(String key) {
        PotionEffectType type = SupremePotionEffects.resolve(key);
        if (type == null && WARNED_EFFECT_KEYS.add(key)) {
            Supreme plugin = Supreme.inst();
            if (plugin != null) {
                plugin.log(Level.WARNING,
                        "Unknown supreme-effect potion key '" + key
                        + "'. This effect will not be applied; use a Minecraft registry name.");
            }
        }
        return type;
    }

    private static PotionEffect[] getEffects(ConfigurationSection section, int amplifier) {
        if (section == null) {
            return new PotionEffect[0];
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (!section.getBoolean(key)) {
                continue;
            }
            PotionEffectType type = resolveEffect(key);
            if (type != null) {
                // Preserve 30-second duration and the intentionally hidden particles/icon.
                effects.add(new PotionEffect(type, 600, amplifier, false, false, false));
            }
        }
        return effects.toArray(new PotionEffect[0]);
    }

    private static List<String> getNamesEffects(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (!section.getBoolean(key)) {
                continue;
            }
            PotionEffectType type = resolveEffect(key);
            if (type != null) {
                names.add(SupremePotionEffects.displayName(type));
            }
        }
        return names;
    }

}

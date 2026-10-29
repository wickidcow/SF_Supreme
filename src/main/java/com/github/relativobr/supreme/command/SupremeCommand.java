package com.github.relativobr.supreme.command;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.gear.AbstractArmor;
import com.github.relativobr.supreme.generic.machine.GenericMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.util.SupremeRecipeDoctor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Administrative Supreme diagnostics. */
public final class SupremeCommand implements CommandExecutor, TabCompleter {

  private static final int MAX_FINDINGS_IN_CHAT = 25;
  // These private wire-format tokens preserve the existing transient messages and
  // any section-coded text supplied by diagnostic providers. They are never saved.
  private static final String AQUA = "\u00a7b";
  private static final String WHITE = "\u00a7f";
  private static final String GRAY = "\u00a77";
  private static final String DARK_GRAY = "\u00a78";
  private static final String RED = "\u00a7c";
  private static final String GREEN = "\u00a7a";
  private static final String YELLOW = "\u00a7e";
  private static final LegacyComponentSerializer COMMAND_TEXT = LegacyComponentSerializer.legacySection();
  private final Supreme plugin;

  public SupremeCommand(Supreme plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command,
      @Nonnull String label, @Nonnull String[] args) {
    if (args.length == 0) {
      send(sender, AQUA + "Supreme Legacy " + WHITE
          + plugin.getPluginMeta().getVersion());
      send(sender, GRAY + "/" + label + " doctor recipes"
          + DARK_GRAY + " - audit Supreme machine recipes");
      send(sender, GRAY + "/" + label + " doctor machine"
          + DARK_GRAY + " - inspect the Supreme machine you are looking at");
      send(sender, GRAY + "/" + label + " doctor retry"
          + DARK_GRAY + " - re-read a blocked machine checkpoint after repair");
      send(sender, GRAY + "/" + label + " doctor soulbound"
          + DARK_GRAY + " - inspect Supreme gear held in your main hand");
      send(sender, GRAY + "/" + label + " doctor armor"
          + DARK_GRAY + " - inspect worn Supreme armor effects and protection");
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("doctor")) {
      if (!sender.hasPermission("supreme.admin")) {
        send(sender, RED + "You do not have permission to run Supreme diagnostics.");
        return true;
      }

      if (args[1].equalsIgnoreCase("recipes")) {
        runRecipeDoctor(sender);
        return true;
      }
      if (args[1].equalsIgnoreCase("soulbound")) {
        runSoulboundDoctor(sender);
        return true;
      }
      if (args[1].equalsIgnoreCase("armor")) {
        runArmorDoctor(sender);
        return true;
      }
      if (args[1].equalsIgnoreCase("machine")) {
        runMachineDoctor(sender, false);
        return true;
      }
      if (args[1].equalsIgnoreCase("retry")) {
        runMachineDoctor(sender, true);
        return true;
      }
    }

    send(sender, RED + "Usage: /" + label + " doctor <recipes|machine|retry|soulbound|armor>");
    return true;
  }

  private static void send(CommandSender sender, String message) {
    sender.sendMessage(commandMessage(message));
  }

  // A compatibility boundary for command output only: never reinterpret ampersands,
  // MiniMessage tags, item lore or persisted records while modernizing the transport.
  static Component commandMessage(String message) {
    return COMMAND_TEXT.deserialize(message);
  }

  private void runArmorDoctor(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      send(sender, RED + "Wear Supreme armor to inspect its potion effects.");
      return;
    }

    boolean coreEffectsEnabled = Slimefun.getCfg().getBoolean("options.enable-armor-effects");
    int refreshSeconds = Slimefun.getCfg().getInt("options.armor-update-interval");
    send(sender, AQUA + "Supreme Armor Effects Doctor");
    send(sender, GRAY + "Slimefun armor effects: "
        + (coreEffectsEnabled ? GREEN + "enabled" : RED + "disabled")
        + GRAY + " | Refresh: " + WHITE + refreshSeconds + "s");

    String[] slots = {"Boots", "Leggings", "Chestplate", "Helmet"};
    ItemStack[] worn = player.getInventory().getArmorContents();
    int detected = 0;
    for (int i = 0; i < slots.length; i++) {
      ItemStack stack = worn[i];
      SlimefunItem sfItem = SlimefunItem.getByItem(stack);
      if (!(sfItem instanceof AbstractArmor armor)) {
        send(sender, GRAY + slots[i] + ": " + DARK_GRAY
            + "not recognized as Supreme armor");
        continue;
      }
      detected++;
      boolean allowed = armor.canUse(player, false) && !armor.isDisabledIn(player.getWorld());
      send(sender, GRAY + slots[i] + ": " + WHITE + armor.getId()
          + (allowed ? "" : RED + " [unavailable in this world / not unlocked]"));
      PotionEffect[] configured = armor.getPotionEffects();
      if (configured.length == 0) {
        send(sender, DARK_GRAY + "  No potion bonuses for this armor tier.");
      }
      for (PotionEffect desired : configured) {
        PotionEffect active = player.getPotionEffect(desired.getType());
        boolean present = active != null;
        String effect = desired.getType().getKey().getKey().replace('_', ' ');
        send(sender, DARK_GRAY + "  " + effect + " "
            + (desired.getAmplifier() + 1) + ": "
            + (present ? GREEN + "active (level " + (active.getAmplifier() + 1) + ")"
                       : YELLOW + "not active"));
      }
    }

    if (detected == 0) {
      send(sender, YELLOW
          + "No registered Supreme armor detected. Check the saved item IDs and compatibility settings.");
      return;
    }
    send(sender, GRAY + "Matching four-piece Supreme set (radiation): "
        + status(hasCompleteSet(player, ProtectionType.RADIATION)));
    send(sender, GRAY + "Matching four-piece Supreme set (bees): "
        + status(hasCompleteSet(player, ProtectionType.BEES)));
    send(sender, GRAY + "Matching four-piece Supreme set (elytra collision): "
        + status(hasCompleteSet(player, ProtectionType.FLYING_INTO_WALL)));
    send(sender, DARK_GRAY
        + "Active status includes effects from other plugins/potions; it does not prove their source.");
  }

  private static boolean hasCompleteSet(Player player, ProtectionType protection) {
    org.bukkit.NamespacedKey setId = null;
    for (ItemStack piece : player.getInventory().getArmorContents()) {
      SlimefunItem item = SlimefunItem.getByItem(piece);
      if (!(item instanceof AbstractArmor armor) || !armor.canUse(player, false)
          || armor.isDisabledIn(player.getWorld())) {
        return false;
      }
      if (!java.util.Arrays.asList(armor.getProtectionTypes()).contains(protection)) {
        return false;
      }
      org.bukkit.NamespacedKey id = armor.getArmorSetId();
      if (setId != null && !setId.equals(id)) {
        return false;
      }
      setId = id;
    }
    return setId != null;
  }

  private static String status(boolean enabled) {
    return enabled ? GREEN + "yes" : YELLOW + "no";
  }

  private void runSoulboundDoctor(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      send(sender, RED + "Hold Supreme gear in your main hand to inspect its soulbound status.");
      return;
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType().isAir()) {
      send(sender, RED + "Hold the Supreme armor, weapon or tool in your main hand.");
      return;
    }

    String savedId = Slimefun.getItemDataService().getItemData(held).orElse("(no Slimefun ID)");
    boolean recognized = plugin.getSoulboundCompatibility().recognizesSupremeGear(held);
    boolean historical = plugin.getSoulboundCompatibility().isHistoricalGear(held);
    boolean bound = SlimefunUtils.isSoulbound(held, player.getWorld());

    send(sender, AQUA + "Supreme Soulbound Doctor");
    send(sender, GRAY + "Saved ID: " + WHITE + savedId);
    send(sender, GRAY + "Registered Supreme gear: "
        + (recognized ? GREEN + "yes" : RED + "no"));
    send(sender, GRAY + "Historical Supreme ID: "
        + (historical ? YELLOW + "yes" : WHITE + "no"));
    send(sender, GRAY + "Slimefun soulbound in this world: "
        + (bound ? GREEN + "yes" : RED + "no"));
    if (!bound && !recognized) {
      send(sender, YELLOW
          + "This item cannot be safely recovered from its display lore alone. Check the saved item ID.");
    } else if (!bound) {
      send(sender, YELLOW
          + "A recognized old gear ID can be given Slimefun's native tag by the death compatibility handler.");
    }
  }

  private void runRecipeDoctor(CommandSender sender) {
    SupremeRecipeDoctor.Report report = SupremeRecipeDoctor.scan();
    send(sender, AQUA + "Supreme Recipe Doctor");
    send(sender, GRAY + "Recipe groups: " + WHITE + report.groups()
        + GRAY + " | Recipes: " + WHITE + report.recipes());
    send(sender, GRAY + "Errors: "
        + (report.errors() == 0 ? GREEN : RED) + report.errors()
        + GRAY + " | Warnings: "
        + (report.warnings() == 0 ? GREEN : YELLOW) + report.warnings());

    if (report.findings().isEmpty()) {
      send(sender, GREEN + "All audited Supreme recipes passed validation.");
      return;
    }

    int shown = Math.min(MAX_FINDINGS_IN_CHAT, report.findings().size());
    for (int i = 0; i < shown; i++) {
      String finding = report.findings().get(i);
      String color = finding.startsWith("ERROR") ? RED : YELLOW;
      send(sender, color + finding);
    }
    if (report.findings().size() > shown) {
      send(sender, GRAY + "...and " + (report.findings().size() - shown)
          + " more finding(s). See the server log for the full audit.");
    }
    plugin.logRecipeDoctor(report);
  }

  @SuppressWarnings("deprecation")
  private void runMachineDoctor(CommandSender sender, boolean retry) {
    if (!(sender instanceof Player player)) {
      send(sender, RED + "Machine Doctor must be run by a player looking at a block.");
      return;
    }

    Block target = player.getTargetBlockExact(8);
    if (target == null) {
      send(sender, RED + "Look directly at a Supreme machine within 8 blocks.");
      return;
    }

    SlimefunItem item = BlockStorage.check(target);
    if (retry) {
      if (!(item instanceof GenericMachine machine)) {
        send(sender, RED + "That block does not support Supreme checkpoint recovery.");
        return;
      }
      boolean readable = machine.retryMachineRecovery(target);
      send(sender, readable
          ? GREEN + "Checkpoint is readable. Normal machine ticking can resume."
          : RED + "Recovery remains blocked. The original checkpoint is still retained.");
    }
    List<String> diagnosticLines;
    if (item instanceof SupremeMachineDiagnostics diagnostics) {
      diagnosticLines = diagnostics.getMachineDiagnosticLines(target);
    } else if (item instanceof GenericMachine machine) {
      diagnosticLines = machine.getMachineDiagnosticLines(target);
    } else {
      send(sender, RED
          + "That block is not a Supreme machine supported by Machine Doctor.");
      return;
    }

    send(sender, AQUA + "Supreme Machine Doctor");
    send(sender, GRAY + target.getWorld().getName() + " "
        + target.getX() + "," + target.getY() + "," + target.getZ());
    for (String line : diagnosticLines) {
      send(sender, GRAY + line);
    }
  }

  @Override
  public List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command,
      @Nonnull String alias, @Nonnull String[] args) {
    List<String> options = new ArrayList<>();
    if (args.length == 1) {
      addIfMatches(options, "doctor", args[0]);
    } else if (args.length == 2 && args[0].equalsIgnoreCase("doctor")) {
      addIfMatches(options, "recipes", args[1]);
      addIfMatches(options, "machine", args[1]);
      addIfMatches(options, "retry", args[1]);
      addIfMatches(options, "soulbound", args[1]);
      addIfMatches(options, "armor", args[1]);
    }
    return options;
  }

  private static void addIfMatches(List<String> options, String value, String typed) {
    if (value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
      options.add(value);
    }
  }
}

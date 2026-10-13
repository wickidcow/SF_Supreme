package com.github.relativobr.supreme.command;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.gear.AbstractArmor;
import com.github.relativobr.supreme.generic.machine.GenericMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.util.SupremeRecipeDoctor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ArmorProtectionUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.ChatColor;
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
  private final Supreme plugin;

  public SupremeCommand(Supreme plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command,
      @Nonnull String label, @Nonnull String[] args) {
    if (args.length == 0) {
      sender.sendMessage(ChatColor.AQUA + "Supreme Legacy " + ChatColor.WHITE
          + plugin.getDescription().getVersion());
      sender.sendMessage(ChatColor.GRAY + "/" + label + " doctor recipes"
          + ChatColor.DARK_GRAY + " - audit Supreme machine recipes");
      sender.sendMessage(ChatColor.GRAY + "/" + label + " doctor machine"
          + ChatColor.DARK_GRAY + " - inspect the Supreme machine you are looking at");
      sender.sendMessage(ChatColor.GRAY + "/" + label + " doctor retry"
          + ChatColor.DARK_GRAY + " - re-read a blocked machine checkpoint after repair");
      sender.sendMessage(ChatColor.GRAY + "/" + label + " doctor soulbound"
          + ChatColor.DARK_GRAY + " - inspect Supreme gear held in your main hand");
      sender.sendMessage(ChatColor.GRAY + "/" + label + " doctor armor"
          + ChatColor.DARK_GRAY + " - inspect worn Supreme armor effects and protection");
      return true;
    }

    if (args.length >= 2 && args[0].equalsIgnoreCase("doctor")) {
      if (!sender.hasPermission("supreme.admin")) {
        sender.sendMessage(ChatColor.RED + "You do not have permission to run Supreme diagnostics.");
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

    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " doctor <recipes|machine|retry|soulbound|armor>");
    return true;
  }

  private void runArmorDoctor(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "Wear Supreme armor to inspect its potion effects.");
      return;
    }

    boolean coreEffectsEnabled = Slimefun.getCfg().getBoolean("options.enable-armor-effects");
    int refreshSeconds = Slimefun.getCfg().getInt("options.armor-update-interval");
    sender.sendMessage(ChatColor.AQUA + "Supreme Armor Effects Doctor");
    sender.sendMessage(ChatColor.GRAY + "Slimefun armor effects: "
        + (coreEffectsEnabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled")
        + ChatColor.GRAY + " | Refresh: " + ChatColor.WHITE + refreshSeconds + "s");

    String[] slots = {"Boots", "Leggings", "Chestplate", "Helmet"};
    ItemStack[] worn = player.getInventory().getArmorContents();
    int detected = 0;
    for (int i = 0; i < slots.length; i++) {
      ItemStack stack = worn[i];
      SlimefunItem sfItem = SlimefunItem.getByItem(stack);
      if (!(sfItem instanceof AbstractArmor armor)) {
        sender.sendMessage(ChatColor.GRAY + slots[i] + ": " + ChatColor.DARK_GRAY
            + "not recognized as Supreme armor");
        continue;
      }
      detected++;
      boolean allowed = armor.canUse(player, false) && !armor.isDisabledIn(player.getWorld());
      sender.sendMessage(ChatColor.GRAY + slots[i] + ": " + ChatColor.WHITE + armor.getId()
          + (allowed ? "" : ChatColor.RED + " [unavailable in this world / not unlocked]"));
      PotionEffect[] configured = armor.getPotionEffects();
      if (configured.length == 0) {
        sender.sendMessage(ChatColor.DARK_GRAY + "  No potion bonuses for this armor tier.");
      }
      for (PotionEffect desired : configured) {
        PotionEffect active = player.getPotionEffect(desired.getType());
        boolean present = active != null;
        String effect = desired.getType().getKeyOrThrow().getKey().replace('_', ' ');
        sender.sendMessage(ChatColor.DARK_GRAY + "  " + effect + " "
            + (desired.getAmplifier() + 1) + ": "
            + (present ? ChatColor.GREEN + "active (level " + (active.getAmplifier() + 1) + ")"
                       : ChatColor.YELLOW + "not active"));
      }
    }

    if (detected == 0) {
      sender.sendMessage(ChatColor.YELLOW
          + "No registered Supreme armor detected. Check the saved item IDs and compatibility settings.");
      return;
    }
    sender.sendMessage(ChatColor.GRAY + "Full-set radiation protection: "
        + status(ArmorProtectionUtils.hasFullProtectionAgainst(player, ProtectionType.RADIATION)));
    sender.sendMessage(ChatColor.GRAY + "Full-set bee protection: "
        + status(ArmorProtectionUtils.hasFullProtectionAgainst(player, ProtectionType.BEES)));
    sender.sendMessage(ChatColor.GRAY + "Full-set elytra wall protection: "
        + status(ArmorProtectionUtils.hasFullProtectionAgainst(player, ProtectionType.FLYING_INTO_WALL)));
    sender.sendMessage(ChatColor.DARK_GRAY
        + "Active status includes effects from other plugins/potions; it does not prove their source.");
  }

  private static String status(boolean enabled) {
    return enabled ? ChatColor.GREEN + "yes" : ChatColor.YELLOW + "no";
  }

  private void runSoulboundDoctor(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "Hold Supreme gear in your main hand to inspect its soulbound status.");
      return;
    }

    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType().isAir()) {
      sender.sendMessage(ChatColor.RED + "Hold the Supreme armor, weapon or tool in your main hand.");
      return;
    }

    String savedId = Slimefun.getItemDataService().getItemData(held).orElse("(no Slimefun ID)");
    boolean recognized = plugin.getSoulboundCompatibility().recognizesSupremeGear(held);
    boolean historical = plugin.getSoulboundCompatibility().isHistoricalGear(held);
    boolean bound = SlimefunUtils.isSoulbound(held, player.getWorld());

    sender.sendMessage(ChatColor.AQUA + "Supreme Soulbound Doctor");
    sender.sendMessage(ChatColor.GRAY + "Saved ID: " + ChatColor.WHITE + savedId);
    sender.sendMessage(ChatColor.GRAY + "Registered Supreme gear: "
        + (recognized ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
    sender.sendMessage(ChatColor.GRAY + "Historical Supreme ID: "
        + (historical ? ChatColor.YELLOW + "yes" : ChatColor.WHITE + "no"));
    sender.sendMessage(ChatColor.GRAY + "Slimefun soulbound in this world: "
        + (bound ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
    if (!bound && !recognized) {
      sender.sendMessage(ChatColor.YELLOW
          + "This item cannot be safely recovered from its display lore alone. Check the saved item ID.");
    } else if (!bound) {
      sender.sendMessage(ChatColor.YELLOW
          + "A recognized old gear ID can be given Slimefun's native tag by the death compatibility handler.");
    }
  }

  private void runRecipeDoctor(CommandSender sender) {
    SupremeRecipeDoctor.Report report = SupremeRecipeDoctor.scan();
    sender.sendMessage(ChatColor.AQUA + "Supreme Recipe Doctor");
    sender.sendMessage(ChatColor.GRAY + "Recipe groups: " + ChatColor.WHITE + report.groups()
        + ChatColor.GRAY + " | Recipes: " + ChatColor.WHITE + report.recipes());
    sender.sendMessage(ChatColor.GRAY + "Errors: "
        + (report.errors() == 0 ? ChatColor.GREEN : ChatColor.RED) + report.errors()
        + ChatColor.GRAY + " | Warnings: "
        + (report.warnings() == 0 ? ChatColor.GREEN : ChatColor.YELLOW) + report.warnings());

    if (report.findings().isEmpty()) {
      sender.sendMessage(ChatColor.GREEN + "All audited Supreme recipes passed validation.");
      return;
    }

    int shown = Math.min(MAX_FINDINGS_IN_CHAT, report.findings().size());
    for (int i = 0; i < shown; i++) {
      String finding = report.findings().get(i);
      ChatColor color = finding.startsWith("ERROR") ? ChatColor.RED : ChatColor.YELLOW;
      sender.sendMessage(color + finding);
    }
    if (report.findings().size() > shown) {
      sender.sendMessage(ChatColor.GRAY + "...and " + (report.findings().size() - shown)
          + " more finding(s). See the server log for the full audit.");
    }
    plugin.logRecipeDoctor(report);
  }

  @SuppressWarnings("deprecation")
  private void runMachineDoctor(CommandSender sender, boolean retry) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "Machine Doctor must be run by a player looking at a block.");
      return;
    }

    Block target = player.getTargetBlockExact(8);
    if (target == null) {
      sender.sendMessage(ChatColor.RED + "Look directly at a Supreme machine within 8 blocks.");
      return;
    }

    SlimefunItem item = BlockStorage.check(target);
    if (retry) {
      if (!(item instanceof GenericMachine machine)) {
        sender.sendMessage(ChatColor.RED + "That block does not support Supreme checkpoint recovery.");
        return;
      }
      boolean readable = machine.retryMachineRecovery(target);
      sender.sendMessage(readable
          ? ChatColor.GREEN + "Checkpoint is readable. Normal machine ticking can resume."
          : ChatColor.RED + "Recovery remains blocked. The original checkpoint is still retained.");
    }
    List<String> diagnosticLines;
    if (item instanceof SupremeMachineDiagnostics diagnostics) {
      diagnosticLines = diagnostics.getMachineDiagnosticLines(target);
    } else if (item instanceof GenericMachine machine) {
      diagnosticLines = machine.getMachineDiagnosticLines(target);
    } else {
      sender.sendMessage(ChatColor.RED
          + "That block is not a Supreme machine supported by Machine Doctor.");
      return;
    }

    sender.sendMessage(ChatColor.AQUA + "Supreme Machine Doctor");
    sender.sendMessage(ChatColor.GRAY + target.getWorld().getName() + " "
        + target.getX() + "," + target.getY() + "," + target.getZ());
    for (String line : diagnosticLines) {
      sender.sendMessage(ChatColor.GRAY + line);
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

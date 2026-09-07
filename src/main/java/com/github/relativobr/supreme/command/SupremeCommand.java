package com.github.relativobr.supreme.command;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.generic.machine.GenericMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.util.SupremeRecipeDoctor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
      if (args[1].equalsIgnoreCase("machine")) {
        runMachineDoctor(sender);
        return true;
      }
    }

    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " doctor <recipes|machine>");
    return true;
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
  private void runMachineDoctor(CommandSender sender) {
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
    }
    return options;
  }

  private static void addIfMatches(List<String> options, String value, String typed) {
    if (value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
      options.add(value);
    }
  }
}

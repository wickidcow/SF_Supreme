package com.github.relativobr.supreme.command;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.util.SupremeRecipeDoctor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

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
      return true;
    }

    if (args.length >= 2
        && args[0].equalsIgnoreCase("doctor")
        && args[1].equalsIgnoreCase("recipes")) {
      if (!sender.hasPermission("supreme.admin")) {
        sender.sendMessage(ChatColor.RED + "You do not have permission to run Supreme diagnostics.");
        return true;
      }

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
        return true;
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
      return true;
    }

    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " doctor recipes");
    return true;
  }

  @Override
  public List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command,
      @Nonnull String alias, @Nonnull String[] args) {
    List<String> options = new ArrayList<>();
    if (args.length == 1) {
      addIfMatches(options, "doctor", args[0]);
    } else if (args.length == 2 && args[0].equalsIgnoreCase("doctor")) {
      addIfMatches(options, "recipes", args[1]);
    }
    return options;
  }

  private static void addIfMatches(List<String> options, String value, String typed) {
    if (value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
      options.add(value);
    }
  }
}

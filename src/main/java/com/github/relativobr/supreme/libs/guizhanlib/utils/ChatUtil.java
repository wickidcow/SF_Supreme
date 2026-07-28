package com.github.relativobr.supreme.libs.guizhanlib.utils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/** Minimal, embedded replacement for the subset of GuizhanLib used by Supreme. */
public final class ChatUtil {

  private ChatUtil() {}

  @Nonnull
  public static String color(@Nonnull String value) {
    return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(value));
  }

  @Nonnull
  public static List<String> color(@Nonnull List<String> values) {
    return Objects.requireNonNull(values).stream().map(ChatUtil::color).collect(Collectors.toList());
  }

  @ParametersAreNonnullByDefault
  public static void send(CommandSender sender, String message, Object... arguments) {
    sender.sendMessage(color(MessageFormat.format(message, arguments)));
  }
}

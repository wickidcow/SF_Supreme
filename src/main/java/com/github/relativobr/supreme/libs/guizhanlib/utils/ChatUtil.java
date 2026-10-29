package com.github.relativobr.supreme.libs.guizhanlib.utils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;

/** Minimal, embedded replacement for the subset of GuizhanLib used by Supreme. */
public final class ChatUtil {

  private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

  private ChatUtil() {}

  @Nonnull
  public static String color(@Nonnull String value) {
    // This String API is also used for item text. A Component round-trip would
    // normalize redundant codes/resets; retain every original character instead.
    char[] text = Objects.requireNonNull(value).toCharArray();
    for (int i = 0; i + 1 < text.length; i++) {
      if (text[i] == '&' && LEGACY_CODES.indexOf(text[i + 1]) >= 0) {
        text[i] = '\u00a7';
        text[i + 1] = Character.toLowerCase(text[i + 1]);
      }
    }
    return new String(text);
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

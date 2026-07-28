package com.github.relativobr.supreme.libs.guizhanlib.utils;

import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Minimal, embedded replacement for the subset of GuizhanLib used by Supreme. */
public final class StringUtil {

  private StringUtil() {}

  @Nonnull
  public static String dehumanize(@Nonnull String value) {
    return Objects.requireNonNull(value).toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }
}

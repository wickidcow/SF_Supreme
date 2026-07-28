package com.github.relativobr.supreme.libs.guizhanlib.localization;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Minimal, embedded replacement for the subset of GuizhanLib used by Supreme. */
public final class Language {

  private final String name;
  private final File currentFile;
  private final FileConfiguration currentConfig;

  @ParametersAreNonnullByDefault
  public Language(String name, File currentFile, FileConfiguration defaultConfig) {
    this.name = Objects.requireNonNull(name, "Language key cannot be null");
    this.currentFile = Objects.requireNonNull(currentFile, "Language file cannot be null");
    Objects.requireNonNull(defaultConfig, "Default language config cannot be null");

    currentConfig = YamlConfiguration.loadConfiguration(currentFile);
    currentConfig.setDefaults(defaultConfig);
    for (String key : defaultConfig.getKeys(true)) {
      if (!currentConfig.contains(key)) {
        currentConfig.set(key, defaultConfig.get(key));
      }
    }
    save();
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Nonnull
  public FileConfiguration getLang() {
    return currentConfig;
  }

  public void save() {
    try {
      currentConfig.save(currentFile);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not save language file " + currentFile, ex);
    }
  }
}

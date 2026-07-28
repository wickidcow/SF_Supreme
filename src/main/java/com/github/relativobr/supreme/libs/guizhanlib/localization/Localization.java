package com.github.relativobr.supreme.libs.guizhanlib.localization;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal, embedded replacement for the subset of GuizhanLib used by Supreme. */
public class Localization {

  private final JavaPlugin plugin;
  private final String languageFolderName;
  private final File languageFolder;
  private final List<String> languages = new LinkedList<>();
  private final Map<String, Language> languageMap = new LinkedHashMap<>();

  public Localization(JavaPlugin plugin) {
    this(plugin, "lang");
  }

  @ParametersAreNonnullByDefault
  public Localization(JavaPlugin plugin, String folderName) {
    this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
    languageFolderName = Objects.requireNonNull(folderName, "Language folder cannot be null");

    if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
      plugin.getLogger().warning("Could not create plugin data folder");
    }
    languageFolder = new File(plugin.getDataFolder(), folderName);
    if (!languageFolder.exists() && !languageFolder.mkdirs()) {
      plugin.getLogger().warning("Could not create language folder");
    }
  }

  public final void addLanguage(@Nonnull String languageName) {
    Objects.requireNonNull(languageName, "Language file name cannot be null");
    if (languageMap.containsKey(languageName)) {
      return;
    }

    String resourcePath = languageFolderName + '/' + languageName + ".yml";
    InputStream resource = plugin.getResource(resourcePath);
    if (resource == null) {
      plugin.getLogger().log(Level.WARNING,
          "Language {0} is not bundled; falling back to en-US", languageName);
      if (!"en-US".equals(languageName)) {
        addLanguage("en-US");
      }
      return;
    }

    File languageFile = new File(languageFolder, languageName + ".yml");
    if (!languageFile.exists()) {
      plugin.saveResource(resourcePath, false);
    }

    try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
      FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
      languages.add(languageName);
      languageMap.put(languageName, new Language(languageName, languageFile, defaults));
    } catch (Exception ex) {
      plugin.getLogger().log(Level.SEVERE, "Could not load language " + languageName, ex);
    }
  }

  @Nonnull
  public String getString(@Nonnull String path) {
    Objects.requireNonNull(path, "Path cannot be null");
    for (String language : languages) {
      String value = languageMap.get(language).getLang().getString(path);
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  @Nonnull
  public List<String> getStringList(@Nonnull String path) {
    Objects.requireNonNull(path, "Path cannot be null");
    for (String language : languages) {
      List<String> value = languageMap.get(language).getLang().getStringList(path);
      if (!value.isEmpty()) {
        return value;
      }
    }
    return new ArrayList<>();
  }

  @Nonnull
  public String[] getStringArray(@Nonnull String path) {
    return getStringList(path).toArray(String[]::new);
  }
}

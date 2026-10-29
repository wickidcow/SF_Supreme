package com.github.relativobr.supreme.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.relativobr.supreme.Supreme;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Executes command entry points without a server or saved player/world records. */
class SupremeCommandTextTest {
  private final Supreme plugin = mock(Supreme.class);
  private final SupremeCommand executor = new SupremeCommand(plugin);
  private final Command command = mock(Command.class);
  private final CommandSender sender = mock(CommandSender.class);
  private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

  @Test
  void helpKeepsVersionColorsTextAndOrdering() {
    PluginMeta metadata = mock(PluginMeta.class);
    when(plugin.getPluginMeta()).thenReturn(metadata);
    when(metadata.getVersion()).thenReturn("1.0.20");

    assertTrue(executor.onCommand(sender, command, "supreme", new String[0]));

    assertMessages(
        "\u00a7bSupreme Legacy \u00a7f1.0.20",
        "\u00a77/supreme doctor recipes\u00a78 - audit Supreme machine recipes",
        "\u00a77/supreme doctor machine\u00a78 - inspect the Supreme machine you are looking at",
        "\u00a77/supreme doctor retry\u00a78 - re-read a blocked machine checkpoint after repair",
        "\u00a77/supreme doctor soulbound\u00a78 - inspect Supreme gear held in your main hand",
        "\u00a77/supreme doctor armor\u00a78 - inspect worn Supreme armor effects and protection");
  }

  @Test
  void permissionDenialDoesNotTouchPluginOrStorage() {
    assertTrue(executor.onCommand(sender, command, "supreme", new String[] {"doctor", "retry"}));
    assertMessages("\u00a7cYou do not have permission to run Supreme diagnostics.");
    verify(sender).hasPermission("supreme.admin");
    verifyNoInteractions(plugin);
  }

  @Test
  void usagePreservesActualAliasAndLiteralAmpersands() {
    assertTrue(executor.onCommand(sender, command, "s&c", new String[] {"unknown"}));
    assertMessages("\u00a7cUsage: /s&c doctor <recipes|machine|retry|soulbound|armor>");
    verifyNoInteractions(plugin);
  }

  @Test
  void consoleArmorGuardIsUnchanged() {
    when(sender.hasPermission("supreme.admin")).thenReturn(true);
    assertTrue(executor.onCommand(sender, command, "supreme", new String[] {"doctor", "armor"}));
    assertMessages("\u00a7cWear Supreme armor to inspect its potion effects.");
    verifyNoInteractions(plugin);
  }

  @Test
  void consoleSoulboundGuardIsUnchanged() {
    when(sender.hasPermission("supreme.admin")).thenReturn(true);
    assertTrue(executor.onCommand(sender, command, "supreme", new String[] {"doctor", "soulbound"}));
    assertMessages("\u00a7cHold Supreme gear in your main hand to inspect its soulbound status.");
    verifyNoInteractions(plugin);
  }

  @Test
  void consoleMachineAndRetryGuardsDoNotAttemptRecovery() {
    when(sender.hasPermission("supreme.admin")).thenReturn(true);
    assertTrue(executor.onCommand(sender, command, "supreme", new String[] {"doctor", "machine"}));
    assertTrue(executor.onCommand(sender, command, "supreme", new String[] {"doctor", "retry"}));
    assertMessages(
        "\u00a7cMachine Doctor must be run by a player looking at a block.",
        "\u00a7cMachine Doctor must be run by a player looking at a block.");
    verifyNoInteractions(plugin);
  }

  @Test
  void allHistoricalPaletteColorsHaveTheSameAdventureMeaning() {
    String[] codes = {"\u00a7b", "\u00a7f", "\u00a77", "\u00a78", "\u00a7c", "\u00a7a", "\u00a7e"};
    NamedTextColor[] colors = {NamedTextColor.AQUA, NamedTextColor.WHITE, NamedTextColor.GRAY,
        NamedTextColor.DARK_GRAY, NamedTextColor.RED, NamedTextColor.GREEN, NamedTextColor.YELLOW};
    for (int i = 0; i < codes.length; i++) {
      Component rendered = SupremeCommand.commandMessage(codes[i] + "value");
      assertEquals(colors[i], rendered.color());
      assertEquals(codes[i] + "value", legacy.serialize(rendered));
    }
  }

  @Test
  void embeddedDiagnosticFormattingAndResetRemainIntact() {
    String diagnostic = "\u00a77Stored: \u00a7a12\u00a7l ready\u00a7r plain";
    assertEquals(diagnostic, legacy.serialize(SupremeCommand.commandMessage(diagnostic)));
  }

  @Test
  void plainAmpersandsAndMiniMessageTagsAreNotReinterpreted() {
    String diagnostic = "\u00a77A & B &c <red>literal</red> ✓";
    assertEquals(diagnostic, legacy.serialize(SupremeCommand.commandMessage(diagnostic)));
  }

  @Test
  void tabCompletionKeepsCommandNamesAndCaseInsensitiveMatching() {
    assertEquals(List.of("doctor"), executor.onTabComplete(sender, command, "supreme", new String[] {"DO"}));
    assertEquals(List.of("recipes", "retry"),
        executor.onTabComplete(sender, command, "supreme", new String[] {"DOCTOR", "RE"}));
    verifyNoInteractions(plugin);
  }

  private void assertMessages(String... expected) {
    ArgumentCaptor<Component> messages = ArgumentCaptor.forClass(Component.class);
    verify(sender, times(expected.length)).sendMessage(messages.capture());
    assertEquals(List.of(expected), messages.getAllValues().stream().map(legacy::serialize).toList());
    verify(sender, never()).sendMessage(anyString());
  }
}

package com.github.relativobr.supreme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

class SupremeLocalizationActionBarTest {

  @Test
  void namedColorsAndDynamicTextKeepTheirOriginalLegacyPresentation() {
    String message = "&6Supreme &7capacity: &f4";
    String actual = LegacyComponentSerializer.legacySection()
        .serialize(SupremeLocalization.actionBarComponent(message));

    assertEquals("\u00a76Supreme \u00a77capacity: \u00a7f4", actual);
  }

  @Test
  void ExistingSectionFormattedMessagesAreStillRendered() {
    String message = "\u00a7aReady\u00a7c!";
    String actual = LegacyComponentSerializer.legacySection()
        .serialize(SupremeLocalization.actionBarComponent(message));

    assertEquals(message, actual);
  }
}

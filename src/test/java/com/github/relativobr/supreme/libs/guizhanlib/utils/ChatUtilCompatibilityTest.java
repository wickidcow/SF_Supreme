package com.github.relativobr.supreme.libs.guizhanlib.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatUtilCompatibilityTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "", "&", "&&", "&&a", "&A&LText&R", "&a&a&l&l", "&#ff00ff",
      "&x&F&F&0&0&8&8Text", "&X&F", "A&B", "\u00a7Aexisting",
      "<red>&zliteral</red>", "&K&L&M&N&O&R", "&g&h&i&j&p&q&s&t&u&v&w&y&z",
      "line1\n&Cline2\r\n", "&\u212A&\u0130"
  })
  void examplesPreserveEveryCharacterRatherThanNormalizingComponents(String input) {
    assertEquals(historical(input), ChatUtil.color(input));
  }

  @Test
  void everyUtf16CodeUnitAfterAnAmpersandMatchesTheHistoricalApi() {
    for (int next = Character.MIN_VALUE; next <= Character.MAX_VALUE; next++) {
      String input = "prefix&" + (char) next + "&A suffix";
      assertEquals(historical(input), ChatUtil.color(input), "UTF-16 code unit " + next);
    }
  }

  @Test
  void deterministicMixedStringsRetainAdjacentCodesResetsAndUnicode() {
    Random random = new Random(0x51F2026L);
    String alphabet = "&&0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx#<>";
    for (int sample = 0; sample < 4096; sample++) {
      StringBuilder input = new StringBuilder();
      for (int index = 0; index < 128; index++) {
        input.append(random.nextBoolean()
            ? alphabet.charAt(random.nextInt(alphabet.length()))
            : (char) random.nextInt(Character.MAX_VALUE + 1));
      }
      String value = input.toString();
      assertEquals(historical(value), ChatUtil.color(value), "sample " + sample);
    }
  }

  @Test
  void nullStringKeepsTheExistingNullPointerFailure() {
    assertThrows(NullPointerException.class, () -> ChatUtil.color((String) null));
  }

  @Test
  void nullListKeepsTheExistingNullPointerFailure() {
    assertThrows(NullPointerException.class, () -> ChatUtil.color((List<String>) null));
  }

  @Test
  void nullListEntryFailsWithoutChangingTheInputList() {
    List<String> input = new ArrayList<>(Arrays.asList("&Afirst", null, "last"));
    List<String> before = new ArrayList<>(input);
    assertThrows(NullPointerException.class, () -> ChatUtil.color(input));
    assertEquals(before, input);
  }

  @Test
  void listOutputPreservesOrderDuplicatesAndIndependentMutability() {
    List<String> input = List.of("&A&lFirst", "&A&lFirst", "plain", "&x&F");
    List<String> result = ChatUtil.color(input);
    assertEquals(input.stream().map(ChatUtilCompatibilityTest::historical).toList(), result);
    result.set(0, "changed");
    result.add("extra");
    assertEquals("&A&lFirst", input.getFirst());
    assertEquals(4, input.size());
    assertTrue(ChatUtil.color(List.<String>of()).isEmpty());
  }

  @Test
  void sendFormatsArgumentsBeforeTranslationAndKeepsTheStringOverload() {
    CommandSender sender = mock(CommandSender.class);
    String format = "&a{0}: ''{1}'' <red> &z";
    Object[] arguments = {"&Bname", "&x&F&F&0&0&A&1value"};
    ChatUtil.send(sender, format, arguments);
    verify(sender).sendMessage(historical(MessageFormat.format(format, arguments)));
    verifyNoMoreInteractions(sender);
  }

  @Test
  void publicMethodDescriptorsRemainAvailable() throws Exception {
    var stringColor = ChatUtil.class.getMethod("color", String.class);
    var listColor = ChatUtil.class.getMethod("color", List.class);
    var send = ChatUtil.class.getMethod("send", CommandSender.class, String.class, Object[].class);
    assertEquals(String.class, stringColor.getReturnType());
    assertEquals(List.class, listColor.getReturnType());
    assertEquals(void.class, send.getReturnType());
    assertTrue(send.isVarArgs());
    for (var method : List.of(stringColor, listColor, send)) {
      assertTrue(Modifier.isPublic(method.getModifiers()));
      assertTrue(Modifier.isStatic(method.getModifiers()));
    }
  }

  // Intentional reference implementation: compare with the real supported Paper
  // baseline rather than silently changing stored/generated legacy text syntax.
  @SuppressWarnings("deprecation")
  private static String historical(String value) {
    return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
  }
}

package com.github.relativobr.supreme.util;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves generator outputs from their final registered crafting definition.
 *
 * <p>Supreme registers generator card recipes while addons are still setting up. Keeping a
 * snapshot of an early SlimefunItemStack as the machine output can diverge from the final
 * template used by crafting and /sf cheat. Only outputs explicitly declared as
 * SlimefunItemStack are resolved by id; vanilla and custom non-template stacks are preserved.
 *
 * <p>The resolver is called only when a generator card is selected or changed. The returned
 * stack is independent of the shared registered item template and may safely have its amount
 * changed by the machine without mutating the registry.
 */
public final class TechGeneratorRecipeOutput {

  private TechGeneratorRecipeOutput() {}

  @Nonnull
  public static ItemStack resolve(@Nonnull ItemStack declaredOutput,
      @Nonnull Function<String, ItemStack> registeredCraftingOutput) {
    if (declaredOutput instanceof SlimefunItemStack template) {
      ItemStack registered = registeredCraftingOutput.apply(template.getItemId());
      if (registered != null) {
        return registered.clone();
      }
    }
    return declaredOutput.clone();
  }
}

package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import com.github.relativobr.supreme.machine.ElectricCrafter;
import com.github.relativobr.supreme.machine.ForgeIngot;
import com.github.relativobr.supreme.machine.ForgeMagical;
import com.github.relativobr.supreme.machine.Foundry;
import com.github.relativobr.supreme.machine.MagicAltar;
import com.github.relativobr.supreme.machine.multiblock.ElectricCoreFabricator;
import com.github.relativobr.supreme.machine.multiblock.ElectricGearFabricator;
import com.github.relativobr.supreme.machine.multiblock.ElectricMagicalFabricator;
import com.github.relativobr.supreme.machine.recipe.VirtualGardenMachineRecipe;
import com.github.relativobr.supreme.machine.tech.MobTechMutationGeneric;
import com.github.relativobr.supreme.machine.tech.TechGenerator;
import com.github.relativobr.supreme.machine.tech.TechMutation;
import com.github.relativobr.supreme.machine.tech.TechRobotic;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Static recipe audit for Supreme's production machines. */
public final class SupremeRecipeDoctor {

  private static final int MEDIUM_INPUT_SLOTS = 9;
  private static final int MEDIUM_OUTPUT_SLOTS = 1;

  private SupremeRecipeDoctor() {}

  public record Report(int groups, int recipes, int errors, int warnings, List<String> findings) {
    public boolean healthy() {
      return errors == 0;
    }
  }

  private record RecipeGroup(String name, List<AbstractItemRecipe> recipes,
                             int inputSlots, int outputSlots) {}

  public static Report scan() {
    List<RecipeGroup> groups = new ArrayList<>();
    groups.add(new RecipeGroup("Electric Core Fabricator", ElectricCoreFabricator.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Electric Gear Fabricator", ElectricGearFabricator.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Electric Magical Fabricator", ElectricMagicalFabricator.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Electric Crafter", ElectricCrafter.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Forge Ingot", ForgeIngot.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Forge Magical", ForgeMagical.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Foundry", Foundry.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Magic Altar", MagicAltar.getAllRecipe(),
        MEDIUM_INPUT_SLOTS, MEDIUM_OUTPUT_SLOTS));
    groups.add(new RecipeGroup("Virtual Garden", VirtualGardenMachineRecipe.getAllRecipe(), 1, 36));
    groups.add(new RecipeGroup("Tech Generator", new ArrayList<>(TechGenerator.receitasParaProduzir), 1, 10));
    groups.add(new RecipeGroup("Tech Robotic", new ArrayList<>(TechRobotic.recipes), 1, 1));
    groups.add(new RecipeGroup("Tech Mutation", mutationRecipes(), 2, 1));

    List<String> findings = new ArrayList<>();
    int recipeCount = 0;
    int errors = 0;
    int warnings = 0;

    for (RecipeGroup group : groups) {
      Map<String, Integer> signatures = new HashMap<>();
      int index = 0;
      for (AbstractItemRecipe recipe : group.recipes()) {
        index++;
        recipeCount++;
        if (recipe == null) {
          errors++;
          findings.add("ERROR " + group.name() + " #" + index + ": null recipe");
          continue;
        }

        ItemStack[] inputs = recipe.getInputNotNull();
        ItemStack[] outputs = recipe.getOutputNotNull();
        if (inputs.length == 0) {
          errors++;
          findings.add("ERROR " + group.name() + " #" + index + ": no usable inputs");
        }
        if (outputs.length == 0) {
          errors++;
          findings.add("ERROR " + group.name() + " #" + index + ": no usable outputs");
        }

        errors += validateStacks(group.name(), index, "input", inputs, findings);
        errors += validateStacks(group.name(), index, "output", outputs, findings);

        int requiredInputSlots = estimateRequiredSlots(inputs);
        if (requiredInputSlots > group.inputSlots()) {
          errors++;
          findings.add("ERROR " + group.name() + " #" + index + ": needs "
              + requiredInputSlots + " input slots but machine exposes " + group.inputSlots());
        }

        int requiredOutputSlots = estimateRequiredSlots(outputs);
        if (requiredOutputSlots > group.outputSlots()) {
          errors++;
          findings.add("ERROR " + group.name() + " #" + index + ": output needs "
              + requiredOutputSlots + " slots but machine exposes " + group.outputSlots());
        }

        String signature = recipeSignature(inputs);
        if (!signature.isEmpty()) {
          Integer previous = signatures.putIfAbsent(signature, index);
          if (previous != null) {
            warnings++;
            findings.add("WARN " + group.name() + " #" + index
                + ": same input signature as recipe #" + previous);
          }
        }
      }
    }

    return new Report(groups.size(), recipeCount, errors, warnings, List.copyOf(findings));
  }

  private static int validateStacks(String group, int recipeIndex, String side,
      ItemStack[] stacks, List<String> findings) {
    int errors = 0;
    for (int i = 0; i < stacks.length; i++) {
      ItemStack stack = stacks[i];
      if (stack == null || stack.getType() == Material.AIR) {
        errors++;
        findings.add("ERROR " + group + " #" + recipeIndex + ": invalid " + side
            + " stack at index " + i);
        continue;
      }
      if (stack.getAmount() <= 0) {
        errors++;
        findings.add("ERROR " + group + " #" + recipeIndex + ": non-positive " + side
            + " amount at index " + i);
      }
    }
    return errors;
  }

  private static int estimateRequiredSlots(ItemStack[] stacks) {
    Map<String, Integer> amounts = new LinkedHashMap<>();
    Map<String, Integer> stackSizes = new HashMap<>();
    for (ItemStack stack : stacks) {
      if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
        continue;
      }
      String key = itemKey(stack);
      amounts.merge(key, stack.getAmount(), Integer::sum);
      stackSizes.putIfAbsent(key, Math.max(1, Math.min(64, stack.getMaxStackSize())));
    }

    int slots = 0;
    for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
      int max = stackSizes.getOrDefault(entry.getKey(), 64);
      slots += (entry.getValue() + max - 1) / max;
    }
    return slots;
  }

  private static String recipeSignature(ItemStack[] stacks) {
    Map<String, Integer> grouped = new java.util.TreeMap<>();
    for (ItemStack stack : stacks) {
      if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
        continue;
      }
      grouped.merge(itemKey(stack), stack.getAmount(), Integer::sum);
    }
    return grouped.toString();
  }

  private static String itemKey(ItemStack stack) {
    SlimefunItem item = SlimefunItem.getByItem(stack);
    if (item != null) {
      return "sf:" + item.getId();
    }
    return "mc:" + stack.getType().getKey();
  }

  private static List<AbstractItemRecipe> mutationRecipes() {
    List<AbstractItemRecipe> recipes = new ArrayList<>();
    for (MobTechMutationGeneric recipe : TechMutation.recipes) {
      if (recipe != null && recipe.getInput1() != null && recipe.getInput2() != null
          && recipe.getOutput() != null) {
        recipes.add(new AbstractItemRecipe(
            new ItemStack[]{recipe.getInput1(), recipe.getInput2()}, recipe.getOutput()));
      } else {
        recipes.add(null);
      }
    }
    return recipes;
  }
}

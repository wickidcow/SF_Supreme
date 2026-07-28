package com.github.relativobr.supreme.libs.guizhanlib.slimefun.machines;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/** Embedded GuizhanLib-compatible menu block wrapper. */
@ParametersAreNonnullByDefault
public abstract class MenuBlock extends SlimefunItem {

  protected MenuBlock(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType,
      ItemStack[] recipe) {
    super(itemGroup, item, recipeType, recipe);
    addItemHandler(
        new BlockBreakHandler(false, false) {
          @Override
          public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
            BlockMenu menu = BlockStorage.getInventory(event.getBlock());
            if (menu != null) {
              onBreak(event, menu);
            }
          }
        },
        new BlockPlaceHandler(false) {
          @Override
          public void onPlayerPlace(BlockPlaceEvent event) {
            onPlace(event, event.getBlockPlaced());
          }
        });
  }

  @Override
  public final void postRegister() {
    new MenuBlockPreset(this);
  }

  protected abstract void setup(BlockMenuPreset preset);

  @Nonnull
  protected final int[] getTransportSlots(DirtyChestMenu menu, ItemTransportFlow flow,
      ItemStack item) {
    return switch (flow) {
      case INSERT -> getInputSlots(menu, item);
      case WITHDRAW -> getOutputSlots();
      default -> new int[0];
    };
  }

  protected int[] getInputSlots(DirtyChestMenu menu, ItemStack item) {
    return getInputSlots();
  }

  protected abstract int[] getInputSlots();

  protected abstract int[] getOutputSlots();

  protected void onNewInstance(BlockMenu menu, Block block) {}

  protected void onBreak(BlockBreakEvent event, BlockMenu menu) {
    Location location = menu.getLocation();
    menu.dropItems(location, getInputSlots());
    menu.dropItems(location, getOutputSlots());
  }

  protected void onPlace(BlockPlaceEvent event, Block block) {}
}

package com.github.relativobr.supreme.libs.guizhanlib.slimefun.machines;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@ParametersAreNonnullByDefault
final class MenuBlockPreset extends BlockMenuPreset {

  private final MenuBlock menuBlock;

  MenuBlockPreset(MenuBlock menuBlock) {
    super(menuBlock.getId(), menuBlock.getItemName());
    this.menuBlock = menuBlock;
    menuBlock.setup(this);
  }

  @Override
  public void newInstance(BlockMenu menu, Block block) {
    menuBlock.onNewInstance(menu, block);
  }

  @Override
  public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow,
      ItemStack item) {
    return menuBlock.getTransportSlots(menu, flow, item);
  }

  @Override
  public void init() {}

  @Override
  public boolean canOpen(Block block, Player player) {
    return Slimefun.getProtectionManager()
        .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK)
        && menuBlock.canUse(player, false);
  }

  @Override
  public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
    return new int[0];
  }
}

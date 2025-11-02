package com.bgsoftware.wildtools.tools;

import com.bgsoftware.wildtools.Locale;
import com.bgsoftware.wildtools.SellWandLogger;
import com.bgsoftware.wildtools.api.events.SellWandUseEvent;
import com.bgsoftware.wildtools.api.hooks.SellInfo;
import com.bgsoftware.wildtools.api.hooks.SoldItem;
import com.bgsoftware.wildtools.api.objects.ToolMode;
import com.bgsoftware.wildtools.api.objects.tools.SellTool;
import com.bgsoftware.wildtools.utils.BukkitUtils;
import com.bgsoftware.wildtools.utils.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class WSellTool extends WTool implements SellTool {

    public WSellTool(Material type, String name) {
        super(type, name, ToolMode.SELL);
    }

    @Override
    public boolean onBlockInteract(PlayerInteractEvent e) {
        if (!plugin.getProviders().hasEconomyProvider()) {
            e.getPlayer().sendMessage(ChatColor.RED + "You tried to use a sell-wand, but the server doesn't have Vault installed. " +
                    "Please contact the server administrators if you believe that this is an error.");
            return false;
        }

        if (!BukkitUtils.canInteractBlock(e.getPlayer(), e.getClickedBlock(), e.getItem()))
            return false;

        BlockState blockState = e.getClickedBlock().getState();

        if (!plugin.getProviders().isContainer(blockState)) {
            Locale.INVALID_CONTAINER_SELL_WAND.send(e.getPlayer());
            return false;
        }

        Inventory inventory = blockState instanceof InventoryHolder ? ((InventoryHolder) blockState).getInventory() : null;

        SellInfo sellInfo = plugin.getProviders().sellContainer(blockState, inventory, e.getPlayer());

        Map<Integer, SoldItem> toSell = sellInfo.getSoldItems();
        double totalEarnings = sellInfo.getTotalEarnings();
        double multiplier = getMultiplier();

        String message = toSell.isEmpty()
                ? Locale.NO_SELL_ITEMS.getMessage()
                : e.getPlayer().isSneaking()
                    ? Locale.SELL_INSPECT.getMessage()
                    : Locale.SOLD_CHEST.getMessage();

        SellWandUseEvent sellWandUseEvent = new SellWandUseEvent(e.getPlayer(), blockState, totalEarnings,
                multiplier, message == null ? "" : message);
        Bukkit.getPluginManager().callEvent(sellWandUseEvent);

        if (sellWandUseEvent.isCancelled())
            return false;

        multiplier = sellWandUseEvent.getMultiplier();
        totalEarnings = sellWandUseEvent.getPrice() * multiplier;

        if (sellWandUseEvent.shouldSell()) {
            plugin.getProviders().getEconomyProvider().depositPlayer(e.getPlayer(), totalEarnings);
            plugin.getProviders().removeContainer(blockState, inventory, sellInfo);

            if (!toSell.isEmpty())
                reduceDurablility(e.getPlayer(), 1, e.getItem());

            Block soldContainer = e.getClickedBlock();

            for (SoldItem soldItem : toSell.values()) {
                SellWandLogger.log(e.getPlayer().getName() + " sold x" + soldItem.getItem().getAmount() + " " +
                        soldItem.getItem().getType() + " from " + soldContainer.getWorld().getName() + "," +
                        soldContainer.getX() + "," + soldContainer.getY() + "," + soldContainer.getZ() + " for $" +
                        soldItem.getPrice() + " (Multiplier: " + multiplier + ")");
            }
        }

        //noinspection all
        message = sellWandUseEvent.getMessage().replace("{0}", NumberUtils.format(totalEarnings))
                .replace("{1}", multiplier != 1 && Locale.MULTIPLIER.getMessage() != null ? Locale.MULTIPLIER.getMessage(multiplier) : "");

        String[] soldInfo = new String[toSell.size()];
        int i = 0;
        for (SoldItem soldItem : toSell.values()) {
            soldInfo[i++] = Locale.SOLD_ITEM.getMessage().replace("{0}", soldItem.getItem().getType().name().replace("_", " "))
                    .replace("{1}", Integer.toString(soldItem.getItem().getAmount()))
                    .replace("{2}", NumberUtils.format(soldItem.getPrice() * multiplier));
        }

        if (!message.isEmpty())
            e.getPlayer().sendMessage(message);

        if(soldInfo.length > 0)
            e.getPlayer().sendMessage(soldInfo);

        return true;
    }

}

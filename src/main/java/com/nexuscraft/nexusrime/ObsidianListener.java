package com.nexuscraft.nexusrime;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.Random;

/**
 * Mining obsidian has a chance to shake loose dragonglass. It listens for the block's drop, so it
 * only ever pays when the obsidian really gave something up (mined with a diamond or netherite pick),
 * never in creative mode, and never for crying obsidian.
 */
final class ObsidianListener implements Listener {

    private final RimeConfig cfg;
    private final RimeItems items;
    private final Random random;

    ObsidianListener(RimeConfig cfg, RimeItems items, Random random) {
        this.cfg = cfg;
        this.items = items;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (event.getBlockState() == null || event.getBlockState().getType() != Material.OBSIDIAN) {
            return;
        }
        if (event.getItems().isEmpty()) {
            return; // nothing came out of it, so nothing is shaken loose either
        }
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int n = ObsidianDrop.shards(cfg, random.nextDouble() * 100.0, random.nextDouble());
        if (n <= 0) {
            return;
        }
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        at.getWorld().dropItemNaturally(at, items.shard(n));
        player.sendMessage(ColorText.translate("&b" + cfg.shardName + (n > 1 ? " x" + n : "") + " &7breaks loose from the obsidian."));
    }
}

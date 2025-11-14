package com.daniel.treecapitator.listener;

import com.daniel.treecapitator.TreeCapitatorPlugin;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * TreeCapListener final: incluye durabilidad vanilla sin usar Enchantment.DURABILITY constante.
 */
public class TreeCapListener implements Listener {

    private final TreeCapitatorPlugin plugin;
    private final Random random = new Random();

    private static final BlockFace[] NEIGHBORS = new BlockFace[]{
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final Map<Material, Integer> MAX_DURABILITY = Map.ofEntries(
            Map.entry(Material.WOODEN_AXE, 59),
            Map.entry(Material.GOLDEN_AXE, 32),
            Map.entry(Material.STONE_AXE, 131),
            Map.entry(Material.IRON_AXE, 250),
            Map.entry(Material.DIAMOND_AXE, 1561),
            Map.entry(Material.NETHERITE_AXE, 2031)
    );

    public TreeCapListener(TreeCapitatorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.isGlobalEnabled()) return;

        Block base = e.getBlock();
        Player p = e.getPlayer();

        if (!isLog(base.getType())) return;

        boolean requireAxe = plugin.getConfig().getBoolean("require-axe", true);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (requireAxe && !isAxe(hand)) return;

        if (p.hasMetadata("TreeCapitatorDisabled") && p.getMetadata("TreeCapitatorDisabled").get(0).asBoolean())
            return;

        e.setCancelled(true);

        int maxBlocks = plugin.getConfig().getInt("max-blocks", 250);
        Set<Block> connected = collectConnectedLogs(base, maxBlocks);

        if (connected.isEmpty()) {
            base.breakNaturally(hand);
            return;
        }

        boolean replantIfSapling = plugin.getConfig().getBoolean("replant-if-sapling-drop", true);
        int delayPer = Math.max(0, plugin.getConfig().getInt("delay-ticks-per-block", 2));

        List<Block> list = new ArrayList<>(connected);
        list.sort(Comparator.comparingInt(Block::getY));

        boolean anySaplingDrop = false;
        for (Block block : list) {
            for (ItemStack drop : block.getDrops(hand)) {
                if (isSapling(drop.getType())) {
                    anySaplingDrop = true;
                    break;
                }
            }
            if (anySaplingDrop) break;
        }

        final boolean anySapling = anySaplingDrop;
        final boolean replantFinal = replantIfSapling;

        AtomicBoolean stopFlag = new AtomicBoolean(false);

        int idx = 0;
        for (Block block : list) {
            final Block toBreak = block;
            final int delay = idx * delayPer;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (stopFlag.get()) return;
                if (!isLog(toBreak.getType())) return;

                ItemStack currentHand = p.getInventory().getItemInMainHand();
                if (requireAxe && !isAxe(currentHand)) {
                    stopFlag.set(true);
                    return;
                }

                toBreak.breakNaturally(currentHand);
                applyDurabilityPerBlock(currentHand, p, stopFlag);

            }, delay);

            idx++;
        }

        int finalDelay = Math.max(1, idx) * delayPer + 1;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (replantFinal && anySapling) {
                tryReplantAtBase(base.getLocation());
            }
        }, finalDelay);
    }

    // Durabilidad estilo vanilla: 1 por bloque, aplicando probabilidad Unbreaking
    private void applyDurabilityPerBlock(ItemStack hand, Player p, AtomicBoolean stopFlag) {
        if (hand == null) return;
        Material mat = hand.getType();
        if (!MAX_DURABILITY.containsKey(mat)) return;

        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        // Obtener nivel de Unbreaking de forma robusta (no usar Enchantment.DURABILITY constante)
        int unbreakingLevel = 0;
        Enchantment unbreakingEnch = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (unbreakingEnch != null) {
            unbreakingLevel = hand.getEnchantmentLevel(unbreakingEnch);
        } else {
            // Fallback: recorrer encantamientos del item y buscar uno que contenga "UNBREAKING"/"unbreaking"
            Map<Enchantment, Integer> ench = hand.getEnchantments();
            for (Map.Entry<Enchantment, Integer> entry : ench.entrySet()) {
                NamespacedKey key = entry.getKey().getKey();
                if (key != null && key.getKey().toLowerCase(Locale.ROOT).contains("unbreaking")) {
                    unbreakingLevel = entry.getValue();
                    break;
                }
            }
        }

        // probabilidad de aplicar daño = 1 / (unbreakingLevel + 1)
        boolean apply = random.nextInt(unbreakingLevel + 1) == 0;
        if (!apply) return;

        int current = damageable.getDamage();
        int newDamage = current + 1;

        int max = MAX_DURABILITY.get(mat);

        if (newDamage >= max) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            p.sendMessage("§cTu herramienta se ha roto.");
            stopFlag.set(true);
        } else {
            damageable.setDamage(newDamage);
            hand.setItemMeta(damageable);
        }
    }

    private Set<Block> collectConnectedLogs(Block start, int max) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty() && visited.size() < max) {
            Block cur = stack.pop();
            if (visited.contains(cur)) continue;
            if (!isLog(cur.getType())) continue;

            visited.add(cur);

            for (BlockFace face : NEIGHBORS) {
                Block nb = cur.getRelative(face);
                if (!visited.contains(nb) && isLog(nb.getType())) stack.push(nb);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    Block nb = cur.getRelative(dx, 0, dz);
                    if (!visited.contains(nb) && isLog(nb.getType())) stack.push(nb);

                    Block nbUp = cur.getRelative(dx, 1, dz);
                    if (!visited.contains(nbUp) && isLog(nbUp.getType())) stack.push(nbUp);
                }
            }
        }

        return visited;
    }

    private boolean isLog(Material m) {
        return m != null && m.name().contains("LOG");
    }

    private boolean isSapling(Material m) {
        return m != null && (m.name().endsWith("_SAPLING") || m == Material.MANGROVE_PROPAGULE);
    }

    private boolean isAxe(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_AXE");
    }

    private void tryReplantAtBase(Location loc) {
        Block ground = loc.clone().subtract(0, 1, 0).getBlock();
        Block at = loc.getBlock();

        if (!(ground.getType() == Material.DIRT ||
                ground.getType() == Material.GRASS_BLOCK ||
                ground.getType() == Material.PODZOL))
            return;

        if (!at.isEmpty() && at.getType() != Material.AIR) return;

        Material sapling = detectSaplingForLocation(loc);
        at.setType(sapling);
    }

    private Material detectSaplingForLocation(Location loc) {
        for (int dy = 0; dy <= 4; dy++) {
            Material m = loc.getBlock().getRelative(0, dy, 0).getType();
            String name = m.name();

            if (name.contains("OAK")) return Material.OAK_SAPLING;
            if (name.contains("BIRCH")) return Material.BIRCH_SAPLING;
            if (name.contains("SPRUCE")) return Material.SPRUCE_SAPLING;
            if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
            if (name.contains("ACACIA")) return Material.ACACIA_SAPLING;
            if (name.contains("DARK_OAK")) return Material.DARK_OAK_SAPLING;
            if (name.contains("MANGROVE")) return Material.MANGROVE_PROPAGULE;
            if (name.contains("CHERRY")) return Material.CHERRY_SAPLING;
        }
        return Material.OAK_SAPLING;
    }
}

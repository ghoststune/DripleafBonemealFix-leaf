package com.ghosttune.dripleafbonemealfix;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.SmallDripleaf;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 拦截「骨粉 + 小滴水叶」的崩溃路径。
 *
 * 崩溃形态：玩家/发射器用骨粉点一个 half=UPPER(TOP) 的小滴水叶，且其正下方不是
 * half=LOWER(BOTTOM) 的小滴水叶。此时 vanilla 的 performBonemeal 会递归向下取
 * HALF 属性，对水/空气等无 HALF 属性的方块抛 IllegalArgumentException。
 *
 * 策略：
 *  - half=BOTTOM            -> 安全（不递归）。simulate-growth 时取消并模拟生长，否则放行给 vanilla。
 *  - half=TOP 且下方是 BOTTOM -> 合法一对。simulate-growth 时从下方生长并消耗，否则放行。
 *  - half=TOP 且下方不是 BOTTOM -> 崩溃形态。始终取消（无论是否 simulate-growth），不消耗骨粉。
 */
public final class DripleafListener implements Listener {

    private final DripleafBonemealFix plugin;

    public DripleafListener(DripleafBonemealFix plugin) {
        this.plugin = plugin;
    }

    // ---- 玩家右键方块（骨粉） ----
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (plugin.getConfig().getBoolean("debug-interact", false)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block cb = event.getClickedBlock();
            plugin.getLogger().info("[interact-debug] player=" + event.getPlayer().getName()
                    + " block=" + (cb == null ? "null" : cb.getType() + " at " + pos(cb))
                    + " item=" + (event.getItem() == null ? "AIR" : event.getItem().getType()));
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.hasBlock()) return;

        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.SMALL_DRIPLEAF) return;
        if (!isHoldingBoneMeal(event)) return;
        if (!(clicked.getBlockData() instanceof SmallDripleaf dripleaf)) return;

        Bisected.Half half = dripleaf.getHalf();
        BlockFace facing = dripleaf.getFacing();
        boolean simulate = plugin.getConfig().getBoolean("simulate-growth", true);

        if (half == Bisected.Half.BOTTOM) {
            // 安全：下半，无递归
            if (simulate) {
                event.setCancelled(true);
                growBigDripleaf(clicked.getLocation(), facing);
                consumeBoneMeal(event);
            }
            // simulate=false：放行给 vanilla
        } else {
            // half == TOP
            Block below = clicked.getRelative(BlockFace.DOWN);
            boolean belowIsBottom = below.getType() == Material.SMALL_DRIPLEAF
                    && below.getBlockData() instanceof SmallDripleaf bd
                    && bd.getHalf() == Bisected.Half.BOTTOM;
            if (belowIsBottom) {
                // 合法一对：从下方（LOWER）位置生长
                if (simulate) {
                    event.setCancelled(true);
                    growBigDripleaf(below.getLocation(), facing);
                    consumeBoneMeal(event);
                }
                // simulate=false：放行给 vanilla
            } else {
                // 崩溃形态：始终取消，不消耗骨粉（与 vanilla 异常先于 shrink 一致）
                event.setCancelled(true);
                if (plugin.getConfig().getBoolean("log-triggers", true)) {
                    plugin.getLogger().warning("已拦截骨粉崩溃：小滴水叶[TOP] at " + pos(clicked)
                            + "，下方=" + below.getType() + "，玩家=" + event.getPlayer().getName());
                }
            }
        }
    }

    // ---- 调试：方块物理事件（谁在碰滴水叶） ----
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!plugin.getConfig().getBoolean("debug-physics", false)) return;
        Block b = event.getBlock();
        Material t = b.getType();
        if (t != Material.SMALL_DRIPLEAF && t != Material.BIG_DRIPLEAF
                && t != Material.BIG_DRIPLEAF_STEM) return;
        String half = (b.getBlockData() instanceof Bisected bi) ? bi.getHalf().name() : "-";
        plugin.getLogger().info("[physics-debug] " + t + " at " + pos(b) + " half=" + half);
    }

    // ---- 发射器发射骨粉 ----
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        BlockFace facing = (dispenser.getBlockData() instanceof Directional d0) ? d0.getFacing() : null;
        Block target = (facing != null) ? dispenser.getRelative(facing) : null;
        boolean debug = plugin.getConfig().getBoolean("debug-dispense", false);
        if (debug) {
            plugin.getLogger().info("[dispense-debug] item=" + event.getItem().getType()
                    + " at " + pos(dispenser)
                    + " facing=" + facing
                    + " target=" + (target == null ? "?" : target.getType()
                        + " data=" + target.getBlockData().getAsString()));
        }
        if (!plugin.getConfig().getBoolean("guard-dispenser", true)) return;
        if (event.getItem().getType() != Material.BONE_MEAL) return;
        if (target == null) return;
        if (target.getType() != Material.SMALL_DRIPLEAF) return;
        if (!(target.getBlockData() instanceof SmallDripleaf dripleaf)) return;
        if (dripleaf.getHalf() != Bisected.Half.TOP) return;

        Block below = target.getRelative(BlockFace.DOWN);
        boolean belowIsBottom = below.getType() == Material.SMALL_DRIPLEAF
                && below.getBlockData() instanceof SmallDripleaf bd
                && bd.getHalf() == Bisected.Half.BOTTOM;
        if (belowIsBottom) return;

        // 崩溃形态：取消发射
        event.setCancelled(true);
        if (debug) plugin.getLogger().info("[dispense-debug] CANCELLED target=" + pos(target));
        if (plugin.getConfig().getBoolean("log-triggers", true)) {
            plugin.getLogger().warning("已拦截发射器骨粉崩溃：小滴水叶[TOP] at " + pos(target)
                    + "，下方=" + below.getType() + "，发射器 at " + pos(dispenser));
        }
    }

    // ================= 辅助 =================

    private boolean isHoldingBoneMeal(PlayerInteractEvent event) {
        if (event.hasItem() && event.getItem().getType() == Material.BONE_MEAL) return true;
        Player p = event.getPlayer();
        return p.getInventory().getItemInMainHand().getType() == Material.BONE_MEAL
                || p.getInventory().getItemInOffHand().getType() == Material.BONE_MEAL;
    }

    /**
     * 消耗 1 个骨粉（从正在使用的那只手持有的骨粉）。
     * 创造/旁观模式不消耗。
     */
    private void consumeBoneMeal(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;
        ItemStack item = event.getItem();
        if (item.getType() != Material.BONE_MEAL) {
            // 兜底：主手 -> 副手
            item = player.getInventory().getItemInMainHand();
            if (item.getType() != Material.BONE_MEAL) {
                item = player.getInventory().getItemInOffHand();
                if (item.getType() != Material.BONE_MEAL) return;
            }
        }
        item.setAmount(item.getAmount() - 1);
    }

    /**
     * 安全地模拟 vanilla 的 BigDripleafBlock.placeWithRandomHeight。
     *
     * @param base   生长基点（LOWER 小滴水叶的位置）
     * @param facing 保持滴水叶朝向
     */
    private void growBigDripleaf(Location base, BlockFace facing) {
        World world = base.getWorld();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int baseY = base.getBlockY();

        // height ∈ {2,3,4}，与 vanilla Mth.nextInt(random,2,5) 一致
        int maxSteps = ThreadLocalRandom.current().nextInt(2, 5);

        // 从 base 向上走「可替换」方块，最多 maxSteps 步
        int j = 0;
        for (int i = 0; i < maxSteps; i++) {
            int y = baseY + i;
            if (y < world.getMinHeight() || y > world.getMaxHeight()) break;
            Block b = world.getBlockAt(x, y, z);
            if (isReplaceable(b)) {
                j++;
            } else {
                break;
            }
        }
        if (j <= 0) return;

        int topY = baseY + j - 1;
        for (int y = baseY; y <= topY; y++) {
            Block b = world.getBlockAt(x, y, z);
            boolean wet = isWet(b);
            Material mat = (y < topY) ? Material.BIG_DRIPLEAF_STEM : Material.BIG_DRIPLEAF;
            BlockData data = mat.createBlockData();
            if (data instanceof Directional d) d.setFacing(facing);
            if (data instanceof Waterlogged w) w.setWaterlogged(wet);
            b.setBlockData(data);
        }
        world.playSound(base, Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
    }

    /** 对应 vanilla 的 state.canBeReplaced() || is(WATER) || is(SMALL_DRIPLEAF) */
    private boolean isReplaceable(Block b) {
        Material m = b.getType();
        if (m == Material.SMALL_DRIPLEAF) return true;
        if (m.isAir()) return true;
        if (m == Material.WATER) return true;
        return b.isReplaceable();
    }

    /** 该位置是否有水（用于设置 waterlogged），对应 vanilla 的 fluidState.isSource() */
    private boolean isWet(Block b) {
        if (b.getType() == Material.WATER) return true;
        if (b.getBlockData() instanceof Waterlogged w) return w.isWaterlogged();
        return false;
    }

    private String pos(Block b) {
        return b.getWorld().getName() + " " + b.getX() + "," + b.getY() + "," + b.getZ();
    }
}

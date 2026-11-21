package com.ghosttune.dripleafbonemealfix;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DripleafBonemealFix
 *
 * 修复 Minecraft 1.21.11 中「骨粉 + 滴水叶」触发的服务端异常：
 *
 *   java.lang.IllegalArgumentException: Cannot get property EnumProperty{name=half, ...}
 *       as it does not exist in Block{minecraft:water}
 *     at net.minecraft.world.level.block.state.StateHolder.getValue(...)
 *     at net.minecraft.world.level.block.SmallDripleafBlock.performBonemeal(...)
 *
 * 根因：SmallDripleafBlock.performBonemeal 对 half=UPPER 的滴水叶会递归向下取下方方块的
 * HALF 属性；当下方不是小滴水叶（例如水、空气、泥土）时，对该方块执行 getValue(HALF)
 * 抛出 IllegalArgumentException。该异常在数据包处理中被抑制（"suppressing error"），
 * 服务端不崩，但骨粉操作失败、日志刷屏，并且与 1.21.11 的滴水叶复制漏洞相关。
 *
 * 本插件在事件层拦截玩家/发射器对「崩溃形态」小滴水叶的骨粉操作，
 * 默认同时安全地模拟大滴水叶生长（simulate-growth=true），彻底封死该路径。
 */
public final class DripleafBonemealFix extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        DripleafListener listener = new DripleafListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("DripleafBonemealFix enabled. simulate-growth="
                + getConfig().getBoolean("simulate-growth", true)
                + ", guard-dispenser="
                + getConfig().getBoolean("guard-dispenser", true)
                + ", log-triggers="
                + getConfig().getBoolean("log-triggers", true)
                + ", debug-dispense="
                + getConfig().getBoolean("debug-dispense", false)
                + ", debug-physics="
                + getConfig().getBoolean("debug-physics", false));
    }

    @Override
    public void onDisable() {
        // 无需清理
    }
}

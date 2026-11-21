# DripleafBonemealFix-leaf
滴水叶骨粉修复（DripleafBonemealFix），本插件为修复leaf端因为调略器导致的浮空滴水叶，在使用骨粉后会出错的问题
修 Minecraft **1.21.11** 的一个leaf端 bug：拿骨粉点「小滴水叶的上半片」、而它下面不是下半片（常见于水下、或下半片被挖掉/冲掉）时，服务端会抛

```
IllegalArgumentException: Cannot get property ... half ... as it does not exist in Block{minecraft:water}
Failed to handle packet ServerboundUseItemOnPacket@..., suppressing error
```

服务端不崩，但这次骨粉白费，而且每点一次日志刷一大段栈，很快把日志淹掉。

本插件在事件层把这条路径拦下来，默认还会**安全地长出大滴水叶**，手感跟原版一致。

## 怎么装

1. 把 `DripleafBonemealFix-1.0.0.jar` 丢进服务器的 `plugins/` 目录
2. 重启服务器（或新开服）
3. 控制台看到下面这行就成功了：

```
[DripleafBonemealFix] DripleafBonemealFix enabled. simulate-growth=true, guard-dispenser=true, log-triggers=true
```

无前置插件，无需改配置文件，无需 ProtocolLib。

**要求**：Minecraft 1.21.11 的 Paper / Leaf / Purpur 系服务端，Java 21 以上（Leaf 请用 Java 25）。

## 配置

首次启动后会有 `plugins/DripleafBonemealFix/config.yml`：

| 配置项 | 默认 | 作用 |
|---|---|---|
| `simulate-growth` | `true` | 是否由插件接管所有「小滴水叶 + 骨粉」并安全生长。`false` = 只拦会崩的那种，正常情况交回原版 |
| `guard-dispenser` | `true` | 是否也拦发射器打出的骨粉 |
| `log-triggers` | `true` | 每次拦截是否写一条警告日志 |
| `debug-dispense` | `false` | 排查用：打印每次发射器投掷 |
| `debug-physics` | `false` | 排查用：打印触碰滴水叶的方块更新 |
| `debug-interact` | `false` | 排查用：打印每次右键方块 |

改完配置执行 `/plugman reload DripleafBonemealFix`，或直接重启服务器。

## 拦截时日志长什么样

```
[DripleafBonemealFix] 已拦截骨粉崩溃：小滴水叶[TOP] at world 100,64,200，下方=WATER，玩家=Steve
```

`下方=` 就是「本该是下半片、实际却是它」的那个方块。看到这条说明插件干活了，不是报错。

## 常见问题

**Q：装上之后滴水叶还能用骨粉长大吗？**
能。默认 `simulate-growth: true`，正常的一对滴水叶点骨粉会照常长成大滴水叶（2~4 格高），朝向和水淹状态都会保留。

**Q：会不会影响别的骨粉用法？**
不会。只处理 `small_dripleaf`，小麦、树苗、作物等一律不碰。

**Q：不想要「接管生长」，只想别刷日志？**
把 `simulate-growth` 改成 `false`，就只在会崩溃的那一种情况下拦截，其他交回原版。

**Q：以后官方修了怎么办？**
直接把插件删掉即可，它不改服务端任何文件。

## 卸载

删掉 `plugins/DripleafBonemealFix-1.0.0.jar` 和 `plugins/DripleafBonemealFix/` 目录，重启服务器。


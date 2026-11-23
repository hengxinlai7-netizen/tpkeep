传送保持补丁 (Teleport Keep)
=============================

跨维度传送后，**重新把客户端与服务端的状态对上** —— 药水效果图标和创造飞行（`/fly`）
不会再「传送一次就没了」。

现象
----

用 `/home`、`/back`、`/tpa` 之类的指令跨维度传送之后，常见两种表现：

- 身上的药水效果**图标消失**了，但其实服务端还在正常生效；
- 创造模式的飞行被关掉，落地之后要重新 `/fly`。

**关键点：服务端状态从来没有丢失，丢的是客户端的同步。**

所以「检查服务端状态（`hasEffect` / `mayfly`）再决定要不要处理」这类写法修不好它 ——
检查的时候服务端永远是对的。

根因
----

FTB Essentials 等模组的 `/home` 走的是 `ServerPlayer#teleportTo(ServerLevel, …)`，
而原版 `ServerPlayer#changeDimension` 还会额外发送两个包：

| 包 | 作用 |
| --- | --- |
| `ClientboundPlayerAbilitiesPacket` | 飞行能力（`mayfly` / `flying`） |
| `ClientboundUpdateMobEffectPacket` | 药水效果 |

`teleportTo` 这两个包都不发。客户端在 `ClientPacketListener#handleRespawn`
里重建 `LocalPlayer` 时，`GameType#updatePlayerAbilities` 会把 `mayfly`/`flying` 重置掉，
效果列表也没有被重新下发 —— 于是「看起来」Buff 和飞行都丢了。

实现
----

`KeepHandler` 无条件地模仿 `changeDimension` 的行为：

1. **缓存**：每 10 tick 记录一次该玩家的全部 `MobEffectInstance` 以及 `mayfly`/`flying`。
   传送事件触发时（`EntityTravelToDimensionEvent`）再补缓存一次，因为那时候快照可能已经太晚。
2. **到达后**：对缓存里的每个效果补发 `ClientboundUpdateMobEffectPacket`
   （**只影响客户端视图，不叠层、不改服务端时长**），并按缓存把 `mayfly`/`flying`
   写回后调用 `onUpdateAbilities()`。
3. **重试**：客户端在到达后要过几 tick 才完成 `handleRespawn` / `setLocalMode`，
   所以按 `retryTicks` 连续补发若干 tick，避免「补早了又被覆盖」。

配置
----

`config/tpkeep-common.toml`

```toml
[tpkeep]
	enabled = true       # 总开关
	keepEffects = true   # 保留药水效果
	keepFlight = true    # 保留创造飞行(/fly 给的 mayfly)
	retryTicks = 20      # 到达后重复恢复的 tick 数
	debugLog = false     # 每次恢复打一行日志
```

- `keepEffects` 只补发同步包，不会让效果叠层，也不会改变服务端剩余时长。
- `keepFlight` 只写回 `mayfly`/`flying` 两个布尔值，不改变游戏模式。
- 有些模组会在到达后几 tick 才清理状态，`retryTicks` 就是为这种情况留的余量。

兼容性
------

- **只在服务端的 Forge 事件里工作**（`ServerPlayer`），没有 mixin、没有客户端注入。
- 不修改任何其它模组的数据，只补发原版同步包。
- Minecraft 1.20.1 / Forge（47.2.20 编译，建议 47.2.0+）。

构建
----

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
$env:GRADLE_USER_HOME = '<workspace>\.gradle-home'
cd teleport-keep
& <workspace>\.tools\gradle-8.5\bin\gradle.bat build --no-daemon --offline
```

产物：`build/libs/tpkeep-1.0.3.jar`

基本信息
--------

| 项 | 值 |
| --- | --- |
| modId | `tpkeep` |
| 显示名 | 传送保持补丁 (Teleport Keep) |
| 版本 | 1.0.3 |
| 环境 | Minecraft 1.20.1 / Forge |
| 硬依赖 | 无（除 Forge / Minecraft） |
| 许可 | MIT |

许可
----

MIT License，见 [LICENSE](LICENSE)。

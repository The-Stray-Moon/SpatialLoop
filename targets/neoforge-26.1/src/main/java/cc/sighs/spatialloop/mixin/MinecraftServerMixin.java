package cc.sighs.spatialloop.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 出生点区块不再常驻——26.1 起这件事由原版自己完成了，所以这里没有注入点。
 *
 * <p>1.21.1 的原版会在 {@code prepareLevels} 里用 {@code TicketType.START} 把出生点周围
 * （{@code spawn_chunk_radius} 游戏规则 + 1）永久保持加载，所以本 mixin 在准备阶段结束后
 * {@code removeRegionTicket} 释放它。26.1 重新设计了票据系统：
 *
 * <ul>
 *   <li>{@code TicketType.START} 已被删除，{@code spawn_chunk_radius} 游戏规则也被删除
 *       （见 {@code GameRuleRegistryFix} 里的数据迁移），{@code prepareLevels()} 现在只做
 *       「激活存档票据 + 等待初始区块加载完成」，不再给出生点加票据；</li>
 *   <li>出生点附近的加载改由 {@code PrepareSpawnTask} 用 {@code PLAYER_SPAWN}（半径 3、超时 20 tick）
 *       和 {@code SPAWN_SEARCH}（半径 0、超时 1 tick）这两种短命票据完成，它们不带
 *       {@code FLAG_PERSIST}，不会写进 {@code TicketStorage}，玩家进入世界后自然过期。</li>
 * </ul>
 *
 * <p>因此「出生区常驻」这个隐患在 26.1 已经不存在，不需要也不能再释放票据：注释保留在此是为了
 * 说明 {@code MinecraftServerMixin} 为什么是空的（它仍留在 {@code spatialloop.mixins.json} 里，
 * 若确认后续版本也不需要，可以连同配置项一起删除）。
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
}

package net.rwhps.plugin.allyrequest

import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.util.Time
import java.util.concurrent.ConcurrentHashMap

/**
 * 一条待决的结盟请求。
 *
 * 对应结盟脚本 `tti` 邀请语义: 发起方 ( [initiatorIndex] ) 邀请
 * 目标玩家 ( [targetIndex] ), 目标在 [expireAt] 前回复 `.y`/`.n`,
 * 超时默认拒绝。
 */
data class AllyRequest(
    val initiatorIndex: Int,
    val targetIndex: Int,
    val expireAt: Long,
) {
    fun isExpired(now: Long): Boolean = now >= expireAt
}

/**
 * 结盟请求服务。
 *
 * 与结盟脚本 `sendRequest(a, b, true)` / `tta` 对应:
 * - 请求以「目标玩家 index」为 key 存储, 同一目标同一时刻只允许一个待决请求
 *   ( `createRequest` 返回 null 表示目标已有待决请求, 他人无法再发起 )
 * - 同意方向: 被邀请方加入发起方队伍 ( 对应脚本 `setTeamInGame(x, Number(w.s))` )
 * - 落队逻辑自包含实现, 不依赖其他插件 ( 参考 TeamChange 的写法 )
 */
object AllyRequestService {
    /** 请求有效期: 30 秒 */
    const val REQUEST_TIMEOUT_MILLIS = 30_000L

    /** 待决请求: key = 目标玩家 index ( internal 便于测试注入过期请求 ) */
    internal val pending = ConcurrentHashMap<Int, AllyRequest>()

    /**
     * 发起一条结盟请求。
     *
     * @return 新请求; 若目标已有待决请求则返回 null
     */
    fun createRequest(initiatorIndex: Int, targetIndex: Int): AllyRequest? {
        val request = AllyRequest(initiatorIndex, targetIndex, Time.concurrentMillis() + REQUEST_TIMEOUT_MILLIS)
        val existing = pending.putIfAbsent(targetIndex, request)
        return if (existing == null) request else null
    }

    /**
     * 查询目标玩家的待决请求。
     *
     * 惰性过期: 若请求已超时, 直接清除并返回 null ( 视为默认拒绝 )。
     */
    fun findByTarget(targetIndex: Int): AllyRequest? {
        val request = pending[targetIndex] ?: return null
        if (request.isExpired(Time.concurrentMillis())) {
            pending.remove(targetIndex, request)
            return null
        }
        return request
    }

    /** 移除目标玩家的待决请求 */
    fun removeRequest(targetIndex: Int): AllyRequest? = pending.remove(targetIndex)

    /** 当前全部待决请求的目标 index */
    fun pendingTargets(): Set<Int> = pending.keys.toSet()

    fun clear() = pending.clear()

    /**
     * 将被邀请方 [target] 移动到 [newTeam]。
     *
     * 大厅直接写字段 ( 由 TEAM_LIST/115 定期同步 ); 开局后需要全量存档同步
     * ( SYNC/35 `allPlayerSync` ) 才能让客户端应用新的同盟关系。
     */
    fun applyTeamChange(
        target: PlayerHess,
        newTeam: Int,
        roomStarted: Boolean,
        sync: () -> Unit,
    ) {
        target.team = newTeam
        if (roomStarted) {
            sync()
        }
    }
}

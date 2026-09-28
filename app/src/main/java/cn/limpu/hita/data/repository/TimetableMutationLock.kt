package cn.limpu.hita.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表刷新落库与用户「采用源端」共用的互斥锁。
 *
 * 刷新在源端课程回归后会清掉「缺失则删除」的待确认项，但采用动作如果先把决策复制出来，
 * 清理就作废不了这份副本，确认仍会把刚回来的课删掉。两边的读决策、改决策、写课表必须包在同一段里。
 * 手动 new 与 Hilt 注入共用同一把锁，避免成绩提醒等旁路再造一个仓库实例后各写各的。
 */
@Singleton
class TimetableMutationLock @Inject constructor() {

    inline fun <T> exclusive(block: () -> T): T = synchronized(lock) { block() }

    companion object {
        @PublishedApi
        internal val lock = Any()
    }
}

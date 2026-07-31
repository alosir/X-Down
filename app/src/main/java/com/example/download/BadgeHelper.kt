package com.example.download

import android.content.Context
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * 桌面图标数字角标管理器（与"签到大师"实现一致：纯 ShortcutBadger）。
 * 数字 = 下载队列中（下载中/暂停/失败）的任务数量；为 0 时移除角标。
 *
 * 注意：本 APP 所有通知渠道均已 setShowBadge(false)，
 * 使 ShortcutBadger 写入的角标成为唯一角标来源，不被通知覆盖。
 */
object BadgeHelper {

    fun applyBadgeCount(context: Context, count: Int) {
        try {
            if (count > 0) {
                ShortcutBadger.applyCount(context, count)
            } else {
                ShortcutBadger.removeCount(context)
            }
        } catch (e: Throwable) {
            // 部分桌面不支持或异常，静默忽略，避免崩溃
            e.printStackTrace()
        }
    }

    fun removeBadge(context: Context) {
        try {
            ShortcutBadger.removeCount(context)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

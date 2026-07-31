package com.example.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import me.leolin.shortcutbadger.ShortcutBadger

/**
 * 桌面角标助手：针对华为 EMUI / 鸿蒙系统做多路尝试。
 * 1. ShortcutBadger（覆盖 EMUI 及多数第三方桌面）
 * 2. 华为 launcher provider 的 change_badge 调用
 * 3. 华为 launcher provider 的 insert 调用（部分鸿蒙版本只支持此方式）
 * 此外下载进度通知会携带 setNumber，供基于通知的角标机制使用。
 */
object BadgeHelper {

    private val HUAWEI_BADGE_URI: Uri = Uri.parse("content://com.huawei.android.launcher.settings/badge/")

    fun applyBadgeCount(context: Context, count: Int) {
        val launcherClass = getLauncherClassName(context)

        // 方式 1：ShortcutBadger
        try {
            if (count > 0) {
                ShortcutBadger.applyCount(context, count)
            } else {
                ShortcutBadger.removeCount(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (launcherClass != null) {
            // 方式 2：华为 change_badge call
            try {
                val extra = Bundle().apply {
                    putString("package", context.packageName)
                    putString("class", launcherClass)
                    putInt("badgenumber", count)
                }
                context.contentResolver.call(HUAWEI_BADGE_URI, "change_badge", null, extra)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 方式 3：华为 provider insert（部分鸿蒙版本）
            try {
                val values = ContentValues().apply {
                    put("package", context.packageName)
                    put("class", launcherClass)
                    put("badgenumber", count)
                }
                context.contentResolver.insert(HUAWEI_BADGE_URI, values)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getLauncherClassName(context: Context): String? {
        return try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className
        } catch (e: Exception) {
            null
        }
    }
}

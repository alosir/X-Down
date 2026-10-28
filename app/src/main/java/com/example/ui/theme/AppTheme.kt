package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用主题系统。
 * 每个预设提供浅色 / 深色两套 ColorScheme，随系统自动适配。
 * 主题选择持久化到 SharedPreferences，启动时恢复。
 */
enum class ThemePreset(val id: String) {
    SYSTEM("system"),
    X_BLACK("x_black"),
    FACEBOOK("facebook"),
    WHATSAPP("whatsapp"),
    REDDIT("reddit"),
    TITANIUM("titanium"),
    MEITUAN("meituan"),
    COFFEE("coffee"),
    TIFFANY("tiffany"),
    SAKURA("sakura"),
    LAVENDER("lavender"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): ThemePreset = values().find { it.id == id } ?: SYSTEM
    }
}

object ThemeController {

    data class Config(val preset: ThemePreset, val customColor: Color?)

    private const val PREFS = "app_prefs"
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_CUSTOM_COLOR = "custom_theme_color"

    private val _config = MutableStateFlow(Config(ThemePreset.SYSTEM, null))
    val config: StateFlow<Config> = _config.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val preset = ThemePreset.fromId(p.getString(KEY_PRESET, ThemePreset.SYSTEM.id))
        val customColor = if (preset == ThemePreset.CUSTOM) {
            Color(p.getInt(KEY_CUSTOM_COLOR, 0xFF1D9BF0.toInt()).toLong() and 0xFFFFFFFFL)
        } else null
        _config.value = Config(preset, customColor)
    }

    fun setPreset(context: Context, preset: ThemePreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRESET, preset.id)
            .apply()
        _config.value = Config(preset, _config.value.customColor)
    }

    fun setCustomColor(context: Context, color: Color) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CUSTOM_COLOR, color.toArgb())
            .putString(KEY_PRESET, ThemePreset.CUSTOM.id)
            .apply()
        _config.value = Config(ThemePreset.CUSTOM, color)
    }
}

/** 根据预设/自定义色构建对应明暗 ColorScheme */
fun colorSchemeFor(config: ThemeController.Config, dark: Boolean): ColorScheme {
    val c = config.customColor
    return when (config.preset) {
        ThemePreset.SYSTEM -> if (dark) DarkColorScheme else LightColorScheme
        ThemePreset.X_BLACK -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFE7E9EA), secondary = Color(0xFF9AA0A6), tertiary = Color(0xFF71767B)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF000000), secondary = Color(0xFF536471), tertiary = Color(0xFF9AA0A6)
            )
        }
        ThemePreset.FACEBOOK -> if (dark) {
            darkColorScheme(primary = Color(0xFFA8C7FA), secondary = Color(0xFF7FA8E8), tertiary = Color(0xFF6B97DD))
        } else {
            lightColorScheme(primary = Color(0xFF1877F2), secondary = Color(0xFF5B96E8), tertiary = Color(0xFF3B82D6))
        }
        ThemePreset.WHATSAPP -> if (dark) {
            darkColorScheme(primary = Color(0xFF7BE0A0), secondary = Color(0xFF54C982), tertiary = Color(0xFF3BB56B))
        } else {
            lightColorScheme(primary = Color(0xFF25D366), secondary = Color(0xFF1DA851), tertiary = Color(0xFF128C7E))
        }
        ThemePreset.REDDIT -> if (dark) {
            darkColorScheme(primary = Color(0xFFFFA385), secondary = Color(0xFFFC8465), tertiary = Color(0xFFE86A4A))
        } else {
            lightColorScheme(primary = Color(0xFFFF4500), secondary = Color(0xFFD93A00), tertiary = Color(0xFFBC2F00))
        }
        ThemePreset.TITANIUM -> if (dark) {
            darkColorScheme(primary = Color(0xFFA9B7C9), secondary = Color(0xFF8A99AD), tertiary = Color(0xFF6E7E94))
        } else {
            lightColorScheme(primary = Color(0xFF64748B), secondary = Color(0xFF475569), tertiary = Color(0xFF334155))
        }
        ThemePreset.MEITUAN -> if (dark) {
            darkColorScheme(primary = Color(0xFFFCC763), secondary = Color(0xFFF7B32B), tertiary = Color(0xFFE69C0E))
        } else {
            lightColorScheme(primary = Color(0xFFF6A200), secondary = Color(0xFFD68A00), tertiary = Color(0xFFB87400))
        }
        ThemePreset.COFFEE -> if (dark) {
            darkColorScheme(primary = Color(0xFFB08968), secondary = Color(0xFF96714F), tertiary = Color(0xFF7C5A3B))
        } else {
            lightColorScheme(primary = Color(0xFF6F4E37), secondary = Color(0xFF5A3E2B), tertiary = Color(0xFF4A3222))
        }
        ThemePreset.TIFFANY -> if (dark) {
            darkColorScheme(primary = Color(0xFF63E0DC), secondary = Color(0xFF3FCCC7), tertiary = Color(0xFF2AA8A3))
        } else {
            lightColorScheme(primary = Color(0xFF0ABAB5), secondary = Color(0xFF089B97), tertiary = Color(0xFF067C79))
        }
        ThemePreset.SAKURA -> if (dark) {
            darkColorScheme(primary = Color(0xFFFBB6D0), secondary = Color(0xFFF793B3), tertiary = Color(0xFFEE7299))
        } else {
            lightColorScheme(primary = Color(0xFFF783AC), secondary = Color(0xFFE95F8F), tertiary = Color(0xFFD63F75))
        }
        ThemePreset.LAVENDER -> if (dark) {
            darkColorScheme(primary = Color(0xFFB3A8EA), secondary = Color(0xFF9A8CE0), tertiary = Color(0xFF7F6FD4))
        } else {
            lightColorScheme(primary = Color(0xFF7C6BD1), secondary = Color(0xFF5F4FB8), tertiary = Color(0xFF4A3C9C))
        }
        ThemePreset.CUSTOM -> {
            val seed = c ?: Color(0xFF1D9BF0)
            buildFromSeed(seed, dark)
        }
    }
}

/** 自定义取色：由种子色派生明暗两套方案（HSL 偏移派生 secondary/tertiary） */
private fun buildFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val (h, s, v) = hsv

    fun shifted(dh: Float, svFactor: Float, vvDelta: Float): Color {
        val nh = ((h + dh) % 360f + 360f) % 360f
        val ns = (s * svFactor).coerceIn(0.25f, 0.95f)
        val nv = (v + vvDelta).coerceIn(0.35f, 0.95f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(nh, ns, nv)).toLong() and 0xFFFFFFFFL)
    }

    val primary = seed
    val secondary = shifted(28f, 0.92f, if (dark) 0.02f else -0.06f)
    val tertiary = shifted(-38f, 0.85f, if (dark) 0.05f else -0.12f)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF00332B),
            secondary = secondary,
            tertiary = tertiary
        )
    } else {
        lightColorScheme(primary = primary, secondary = secondary, tertiary = tertiary)
    }
}

package ua.echecker.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Localized text for code outside Compose (formatters, widgets, notifications), so it stays unit-testable. */
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String

    /** Locale of the resources in use, for weekday names in dates. */
    val locale: Locale
}

/** Reads the app's resources at call time, so a language change applies without recreating it. */
class ResourceStrings(private val context: Context) : Strings {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
    override fun plural(id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)
    override val locale: Locale get() = context.resources.configuration.locales[0]
}

val Context.strings: Strings get() = ResourceStrings(this)

package ua.echecker.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale

/** The app's real resources in a fixed language (Robolectric's default locale is en-US). Robolectric tests only. */
object TestStrings {
    fun forLocale(tag: String): Strings {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return ResourceStrings(base.createConfigurationContext(config))
    }

    val uk: Strings get() = forLocale("uk")
    val en: Strings get() = forLocale("en")
}

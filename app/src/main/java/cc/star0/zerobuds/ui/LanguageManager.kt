package cc.star0.zerobuds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

object LanguageManager {
    var currentLang by mutableStateOf(detectLanguage())
        private set

    private fun detectLanguage(): String {
        val lang = Locale.getDefault().language
        return if (lang.startsWith("zh")) "chs" else "eng"
    }

    fun setLanguage(lang: String) {
        currentLang = lang
    }

    fun initFromPrefs(prefsLang: String?) {
        currentLang = prefsLang ?: detectLanguage()
    }
}

@Composable
fun str(key: String): String {
    val context = LocalContext.current
    val prefix = LanguageManager.currentLang
    val resId = context.resources.getIdentifier("${prefix}_$key", "string", context.packageName)
    return if (resId != 0) context.getString(resId) else key
}

@Composable
fun str(key: String, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val prefix = LanguageManager.currentLang
    val resId = context.resources.getIdentifier("${prefix}_$key", "string", context.packageName)
    return if (resId != 0) context.getString(resId, *formatArgs) else key
}

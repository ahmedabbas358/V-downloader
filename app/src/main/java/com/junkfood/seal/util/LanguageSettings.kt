package com.junkfood.seal.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageSettings {
    
    /**
     * هذه الدالة تقوم بتغيير لغة التطبيق فوراً وتدعم جميع إصدارات أندرويد
     * يتم استدعاؤها بمجرد قيام المستخدم باختيار لغة من القائمة.
     */
    fun applyLanguage(languageTag: String) {
        val localeList = if (languageTag.isEmpty() || languageTag == "system") {
            // العودة إلى لغة النظام الافتراضية
            LocaleListCompat.getEmptyLocaleList()
        } else {
            // تطبيق اللغة المحددة (مثل: "ar", "en", "es")
            LocaleListCompat.forLanguageTags(languageTag)
        }
        
        // تحديث إعدادات التطبيق باللغة الجديدة
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

package com.comai.digitalactivity

import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.AppCategoryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryManagerTest {

    @Test
    fun testKnownAppCategorization() {
        assertEquals(AppCategory.ENTERTAINMENT, AppCategoryManager.categorizeApp("com.google.android.youtube"))
        assertEquals(AppCategory.SOCIAL, AppCategoryManager.categorizeApp("com.instagram.android"))
        assertEquals(AppCategory.COMMUNICATION, AppCategoryManager.categorizeApp("com.whatsapp"))
        assertEquals(AppCategory.BROWSER, AppCategoryManager.categorizeApp("com.android.chrome"))
        assertEquals(AppCategory.PRODUCTIVITY, AppCategoryManager.categorizeApp("com.google.android.gm"))
        assertEquals(AppCategory.EDUCATION, AppCategoryManager.categorizeApp("com.duolingo"))
        assertEquals(AppCategory.DEVELOPMENT, AppCategoryManager.categorizeApp("com.github.android"))
        assertEquals(AppCategory.UTILITIES, AppCategoryManager.categorizeApp("com.android.settings"))
    }

    @Test
    fun testKeywordHeuristicsCategorization() {
        assertEquals(AppCategory.GAMING, AppCategoryManager.categorizeApp("com.example.supergame"))
        assertEquals(AppCategory.BROWSER, AppCategoryManager.categorizeApp("org.custom.webbrowser"))
        assertEquals(AppCategory.DEVELOPMENT, AppCategoryManager.categorizeApp("io.github.devterminal"))
        assertEquals(AppCategory.EDUCATION, AppCategoryManager.categorizeApp("com.test.studyguide"))
        assertEquals(AppCategory.PRODUCTIVITY, AppCategoryManager.categorizeApp("com.test.quicknotes"))
        assertEquals(AppCategory.OTHER, AppCategoryManager.categorizeApp("com.xyz.unknownpkg123"))
    }
}

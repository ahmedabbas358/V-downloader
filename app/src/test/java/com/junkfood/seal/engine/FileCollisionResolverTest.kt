package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.resilience.FileCollisionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCollisionResolverTest {

    @Test
    fun testGenericTitleDetection() {
        // Generic social media titles MUST be detected as generic
        assertTrue(FileCollisionResolver.isGenericTitle("Video by ahmed"))
        assertTrue(FileCollisionResolver.isGenericTitle("Photo by 1q.ml"))
        assertTrue(FileCollisionResolver.isGenericTitle("Reel by creator_99"))
        assertTrue(FileCollisionResolver.isGenericTitle("TikTok video by user12345"))
        assertTrue(FileCollisionResolver.isGenericTitle("TikTok video #7123456789"))
        assertTrue(FileCollisionResolver.isGenericTitle("instagram post by nature"))
        assertTrue(FileCollisionResolver.isGenericTitle("video"))
        assertTrue(FileCollisionResolver.isGenericTitle("reel"))
        assertTrue(FileCollisionResolver.isGenericTitle("track_01"))
        assertTrue(FileCollisionResolver.isGenericTitle("track 5"))
        assertTrue(FileCollisionResolver.isGenericTitle("https://instagram.com/reel/Cx123abc/"))

        // Real specific video titles MUST NOT be detected as generic
        assertFalse(FileCollisionResolver.isGenericTitle("Learn Kotlin Coroutines in 2026"))
        assertFalse(FileCollisionResolver.isGenericTitle("Full Stack Android Architecture Guide"))
        assertFalse(FileCollisionResolver.isGenericTitle("سورة الكهف كاملة بصوت الشيخ مشاري العفاسي"))
    }

    @Test
    fun testVideoIdExtraction() {
        // YouTube URLs
        assertEquals("dQw4w9WgXcQ", FileCollisionResolver.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", FileCollisionResolver.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abcdef"))
        assertEquals("dQw4w9WgXcQ", FileCollisionResolver.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))

        // Instagram URLs
        assertEquals("C_8hK9sM123", FileCollisionResolver.extractVideoId("https://www.instagram.com/reel/C_8hK9sM123/?igsh=MzRlODBiNWFlZA=="))
        assertEquals("C_8hK9sM123", FileCollisionResolver.extractVideoId("https://www.instagram.com/p/C_8hK9sM123/"))
        assertEquals("C_8hK9sM123", FileCollisionResolver.extractVideoId("https://www.instagram.com/tv/C_8hK9sM123/"))

        // TikTok URLs
        assertEquals("7123456789012345678", FileCollisionResolver.extractVideoId("https://www.tiktok.com/@user/video/7123456789012345678?is_from_webapp=1"))
        assertEquals("ZMeXYZ123", FileCollisionResolver.extractVideoId("https://vm.tiktok.com/ZMeXYZ123/"))

        // Twitter / X URLs
        assertEquals("1823456789012345678", FileCollisionResolver.extractVideoId("https://x.com/user/status/1823456789012345678?s=20"))

        // Facebook URLs
        assertEquals("123456789012345", FileCollisionResolver.extractVideoId("https://www.facebook.com/reel/123456789012345"))
        assertEquals("1AbCdEfGhI", FileCollisionResolver.extractVideoId("https://www.facebook.com/share/r/1AbCdEfGhI/?sfnsn=mo"))
        assertEquals("1ZyXwVuTsR", FileCollisionResolver.extractVideoId("https://www.facebook.com/share/v/1ZyXwVuTsR/"))

        // Threads & Reddit URLs
        assertEquals("C9xYz12345", FileCollisionResolver.extractVideoId("https://www.threads.net/@user/post/C9xYz12345"))
        assertEquals("1abcde9", FileCollisionResolver.extractVideoId("https://www.reddit.com/r/kotlin/comments/1abcde9/sample_post/"))
    }

    @Test
    fun testSocialUrlNormalizationStripsTrackingAndNormalizesMirrors() {
        val tiktok = com.junkfood.seal.util.SocialMediaUrlNormalizer.normalizeUrl(
            "https://www.tiktok.com/@user/video/7123456789012345678?_r=1&_t=8abc&sec_uid=xyz&lang=en"
        )
        assertEquals(
            "https://www.tiktok.com/@user/video/7123456789012345678?lang=en",
            tiktok
        )

        val vxTwitter = com.junkfood.seal.util.SocialMediaUrlNormalizer.normalizeUrl(
            "https://fxtwitter.com/user/status/1823456789012345678?s=20&t=foo"
        )
        assertEquals("https://x.com/user/status/1823456789012345678", vxTwitter)

        val threads = com.junkfood.seal.util.SocialMediaUrlNormalizer.normalizeUrl(
            "https://threads.com/@user/post/C9xYz12345?xmt=AQGz"
        )
        assertEquals("https://www.threads.net/@user/post/C9xYz12345", threads)
    }
}

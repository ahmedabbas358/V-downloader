package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.resilience.RetryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun testHttp429RetryDecision() {
        val failure = SubtitleFailure.Http429(retryAfterSeconds = 8L)
        val decision1 = RetryPolicy.evaluate(failure, currentAttempt = 1)
        assertTrue(decision1.shouldRetry)
        assertTrue(decision1.delayMs >= 8000L)

        val decision3 = RetryPolicy.evaluate(failure, currentAttempt = 3)
        assertFalse(decision3.shouldRetry)
    }

    @Test
    fun testHttp403RetryDecision() {
        val failure = SubtitleFailure.Http403()
        val decision1 = RetryPolicy.evaluate(failure, currentAttempt = 1)
        assertTrue(decision1.shouldRetry)
        assertTrue(decision1.delayMs >= 1500L)
    }

    @Test
    fun testPoTokenRetryDecision() {
        val failure = SubtitleFailure.PoTokenRequired("vid_123")
        val decision1 = RetryPolicy.evaluate(failure, currentAttempt = 1)
        assertTrue(decision1.shouldRetry)

        val decision2 = RetryPolicy.evaluate(failure, currentAttempt = 2)
        assertFalse(decision2.shouldRetry)
    }

    @Test
    fun testPermanentFailuresNeverRetry() {
        assertFalse(RetryPolicy.evaluate(SubtitleFailure.NoSubtitles, 1).shouldRetry)
        assertFalse(RetryPolicy.evaluate(SubtitleFailure.PrivateVideo, 1).shouldRetry)
        assertFalse(RetryPolicy.evaluate(SubtitleFailure.AgeRestricted, 1).shouldRetry)
        assertFalse(RetryPolicy.evaluate(SubtitleFailure.GeoRestricted, 1).shouldRetry)
        assertFalse(RetryPolicy.evaluate(SubtitleFailure.Canceled, 1).shouldRetry)
    }
}

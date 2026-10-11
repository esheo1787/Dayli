package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {

    private val day = 24 * 60 * 60 * 1000L
    private val installed = 1_000_000_000L

    @Test
    fun asks_whenAllConditionsMet() {
        assertTrue(ReviewPrompt.shouldAsk(installed, installed + 3 * day, itemCount = 3, lastAskedMs = null))
    }

    @Test
    fun waits_atLeastThreeDaysAfterInstall() {
        assertFalse(ReviewPrompt.shouldAsk(installed, installed + 3 * day - 1, itemCount = 10, lastAskedMs = null))
    }

    @Test
    fun needs_atLeastThreeItems() {
        assertFalse(ReviewPrompt.shouldAsk(installed, installed + 30 * day, itemCount = 2, lastAskedMs = null))
    }

    @Test
    fun asksAgain_onlyAfterNinetyDays() {
        val now = installed + 200 * day
        assertFalse(ReviewPrompt.shouldAsk(installed, now, itemCount = 5, lastAskedMs = now - 89 * day))
        assertTrue(ReviewPrompt.shouldAsk(installed, now, itemCount = 5, lastAskedMs = now - 90 * day))
    }
}

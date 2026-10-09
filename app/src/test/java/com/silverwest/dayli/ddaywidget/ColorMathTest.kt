package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {

    @Test
    fun hsvToHex_primaryColors() {
        assertEquals("#FF0000", ColorMath.hsvToHex(0f, 1f, 1f))
        assertEquals("#00FF00", ColorMath.hsvToHex(120f, 1f, 1f))
        assertEquals("#0000FF", ColorMath.hsvToHex(240f, 1f, 1f))
        assertEquals("#FFFFFF", ColorMath.hsvToHex(77f, 0f, 1f))
        assertEquals("#000000", ColorMath.hsvToHex(200f, 0.8f, 0f))
        assertEquals("#FF0000", ColorMath.hsvToHex(360f, 1f, 1f))
    }

    @Test
    fun hexToHsv_roundTrips() {
        listOf("#4A7390", "#A9533F", "#6B5891", "#45423F", "#F8C8DC").forEach { hex ->
            val hsv = ColorMath.hexToHsv(hex)!!
            assertEquals(hex, ColorMath.hsvToHex(hsv[0], hsv[1], hsv[2]))
        }
        assertNull(ColorMath.hexToHsv("nope"))
    }

    @Test
    fun wheelPick_centerIsWhite_topIsRed_rightIsYellowGreen() {
        val r = 100f
        val (_, s0) = ColorMath.wheelPick(100f, 100f, r)
        assertEquals(0f, s0, 0.001f)
        val (hTop, sTop) = ColorMath.wheelPick(100f, 0f, r)
        assertEquals(0f, hTop, 0.5f)
        assertEquals(1f, sTop, 0.001f)
        val (hRight, _) = ColorMath.wheelPick(200f, 100f, r)
        assertEquals(90f, hRight, 0.5f)
        val (hLeft, _) = ColorMath.wheelPick(0f, 100f, r)
        assertEquals(270f, hLeft, 0.5f)
        // 원 밖을 눌러도 채도는 1로 제한
        val (_, sOut) = ColorMath.wheelPick(300f, 100f, r)
        assertTrue(abs(sOut - 1f) < 0.001f)
    }
}

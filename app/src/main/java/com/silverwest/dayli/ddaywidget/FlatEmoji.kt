package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size

/**
 * 플랫 이모지 (Microsoft Fluent Emoji Flat, MIT License)
 * assets/emoji_flat/<코드포인트_소문자_밑줄구분>.webp — FE0F(이모지 표현 선택자)는 파일명에서 제외
 * 데이터는 계속 표준 이모지 문자열로 저장하고, 표시할 때만 이미지로 바꿈 → 설정 전환·백업 호환
 */
object FlatEmoji {

    private const val DIR = "emoji_flat"

    @Volatile private var available: Set<String>? = null
    // 72px 이미지 1장 ≈ 20KB → 약 600장 (피커 한 카테고리를 넘겨도 재디코딩이 적게)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** 이모지 → 에셋 파일명 (없으면 null 아님, 존재 여부는 bitmap()에서 확인) */
    internal fun fileName(emoji: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFE0F) continue
            if (sb.isNotEmpty()) sb.append('_')
            sb.append(Integer.toHexString(cp))
        }
        return "$sb.webp"
    }

    private fun availableFiles(context: Context): Set<String> {
        available?.let { return it }
        val files = try {
            context.assets.list(DIR)?.toHashSet() ?: emptySet()
        } catch (e: Exception) { emptySet() }
        available = files
        return files
    }

    fun has(context: Context, emoji: String): Boolean =
        emoji.isNotEmpty() && fileName(emoji) in availableFiles(context)

    /** 플랫 이미지 (없는 이모지면 null → 호출부에서 기본 이모지 텍스트로 대체) */
    fun bitmap(context: Context, emoji: String): Bitmap? {
        if (!has(context, emoji)) return null
        val name = fileName(emoji)
        cache.get(name)?.let { return it }
        return try {
            context.assets.open("$DIR/$name").use { BitmapFactory.decodeStream(it) }
                ?.also { cache.put(name, it) }
        } catch (e: Exception) { null }
    }

    /** 현재 설정에서 이 이모지를 플랫 이미지로 그려야 하면 비트맵 반환 */
    fun bitmapIfEnabled(context: Context, emoji: String): Bitmap? =
        if (DdaySettings.isFlatEmojiEnabled(context)) bitmap(context, emoji) else null
}

/** 앱 전체 이모지 스타일 (MainActivity에서 설정값으로 제공, 설정 변경 시 즉시 재구성) */
val LocalFlatEmoji = compositionLocalOf { false }

/**
 * 이모지 표시 공통 컴포넌트: 플랫 스타일이면 이미지, 아니면(또는 플랫 이미지가 없으면) 기기 이모지 텍스트
 */
@Composable
fun EmojiText(
    emoji: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val flat = LocalFlatEmoji.current
    val bitmap = remember(emoji, flat) {
        if (flat) FlatEmoji.bitmap(context, emoji)?.asImageBitmap() else null
    }
    if (bitmap != null) {
        // 텍스트 이모지와 시각적 크기를 맞춤 (이모지 글리프는 글자 크기보다 약간 큼)
        val sizeDp = with(LocalDensity.current) { (fontSize.value * 1.15f).sp.toDp() }
        Image(
            bitmap = bitmap,
            contentDescription = emoji,
            filterQuality = FilterQuality.Medium,
            modifier = modifier.size(sizeDp)
        )
    } else {
        Text(text = emoji, fontSize = fontSize, modifier = modifier)
    }
}

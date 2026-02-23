package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DdayShareHelper {

    /**
     * 텍스트 공유 — D-Day: "🎉 여행 D-3!" / 투두: 체크리스트 포함
     */
    fun shareText(context: Context, item: DdayItem) {
        val text = if (item.isTodo()) buildTodoText(item) else buildDdayText(item)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "공유"))
    }

    /**
     * 날짜별 D-Day 묶음 텍스트 공유 (캘린더 뷰용)
     */
    fun shareDateText(context: Context, year: Int, month: Int, day: Int, items: List<DdayItem>) {
        val text = buildString {
            append("📅 ${year}년 ${month + 1}월 ${day}일 (D-Day ${items.size}개)")
            items.forEach { item ->
                val check = if (item.isChecked) "☑" else "□"
                val emoji = item.getEmoji()
                val dday = item.date?.let { calculateDday(it) } ?: ""
                append("\n$check $emoji ${item.title} - $dday")
            }
            append("\n- Dayli")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "공유"))
    }

    private fun buildDdayText(item: DdayItem): String {
        val emoji = item.getEmoji()
        val ddayText = item.date?.let { calculateDday(it) } ?: ""
        val timeText = item.getTimeString()
        return buildString {
            append("$emoji ${item.title} $ddayText")
            if (timeText != null) append(" ($timeText)")
            append("!")
            append("\n- Dayli")
        }
    }

    private fun buildTodoText(item: DdayItem): String {
        val emoji = item.getEmoji()
        val subTasks = item.getSubTaskList()
        val completed = subTasks.count { it.isChecked }
        return buildString {
            append("$emoji ${item.title}")
            if (subTasks.isNotEmpty()) append(" ($completed/${subTasks.size})")
            subTasks.forEach { sub ->
                val check = if (sub.isChecked) "☑" else "□"
                append("\n$check ${sub.title}")
            }
            append("\n- Dayli")
        }
    }

    /**
     * 이미지 공유: 앱 컬러 배경에 D-Day 정보 표시된 카드 이미지 생성
     */
    fun shareImage(context: Context, item: DdayItem) {
        val bitmap = if (item.isTodo()) createTodoShareImage(item) else createDdayShareImage(item)
        val file = saveBitmapToCache(context, bitmap)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "D-Day 이미지 공유"))
    }

    private fun createDdayShareImage(item: DdayItem): Bitmap {
        val width = 1080
        val height = 720

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경색: 아이템 컬러
        val bgColor = item.getColorLong().toInt()
        val bgPaint = Paint().apply { color = bgColor }
        val cornerRadius = 48f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, bgPaint)

        // 배경 밝기 판단 → 텍스트 색 결정
        val r = android.graphics.Color.red(bgColor) / 255f
        val g = android.graphics.Color.green(bgColor) / 255f
        val b = android.graphics.Color.blue(bgColor) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val textColor = if (luminance > 0.5f) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        val subTextColor = if (luminance > 0.5f) 0xFF555555.toInt() else 0xFFCCCCCC.toInt()

        // 이모지
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 96f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(item.getEmoji(), width / 2f, 160f, emojiPaint)

        // D-Day 텍스트 (큰 글씨)
        val ddayText = item.date?.let { calculateDday(it) } ?: ""
        val ddayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 120f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(ddayText, width / 2f, 320f, ddayPaint)

        // 제목
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 56f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val titleText = if (item.title.length > 20) item.title.take(20) + "..." else item.title
        canvas.drawText(titleText, width / 2f, 430f, titlePaint)

        // 날짜 + 시간
        val dateText = buildString {
            item.date?.let {
                append(SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it))
            }
            item.getTimeString()?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
        }
        if (dateText.isNotEmpty()) {
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = 40f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(dateText, width / 2f, 510f, datePaint)
        }

        // 하단 앱 이름
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dayli", width / 2f, height - 50f, appPaint)

        return bitmap
    }

    /**
     * 날짜별 D-Day 묶음 이미지 공유 (캘린더 뷰용)
     */
    fun shareDateItems(context: Context, year: Int, month: Int, day: Int, items: List<DdayItem>) {
        val bitmap = createDateShareImage(year, month, day, items)
        val file = saveBitmapToCache(context, bitmap)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "D-Day 이미지 공유"))
    }

    private fun createDateShareImage(year: Int, month: Int, day: Int, items: List<DdayItem>): Bitmap {
        val width = 1080
        val maxDisplay = 10
        val displayItems = items.take(maxDisplay)
        val remaining = items.size - maxDisplay

        // 높이: 헤더(180) + 항목(항목당 80) + 외 N개(60) + 워터마크(80) + 여백
        val listHeight = displayItems.size * 80
        val extraLine = if (remaining > 0) 60 else 0
        val height = (240 + listHeight + extraLine + 100).coerceIn(500, 1600)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경: 첫 아이템 컬러 또는 기본 색
        val bgColor = if (items.isNotEmpty()) items.first().getColorLong().toInt() else 0xFF5C6BC0.toInt()
        val bgPaint = Paint().apply { color = bgColor }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 48f, 48f, bgPaint)

        // 텍스트 색 결정
        val r = android.graphics.Color.red(bgColor) / 255f
        val g = android.graphics.Color.green(bgColor) / 255f
        val b = android.graphics.Color.blue(bgColor) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val textColor = if (luminance > 0.5f) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        val subTextColor = if (luminance > 0.5f) 0xFF555555.toInt() else 0xFFCCCCCC.toInt()

        // 상단: 날짜 (2026년 2월 26일)
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 56f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${year}년 ${month + 1}월 ${day}일", width / 2f, 90f, datePaint)

        // 항목 수
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("D-Day ${items.size}개", width / 2f, 140f, countPaint)

        // 구분선
        val linePaint = Paint().apply {
            color = subTextColor
            alpha = 80
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 170f, width - 80f, 170f, linePaint)

        // 항목 목록: 완료여부 + 이모지 + 제목 + D-Day
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 42f }
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 42f }
        val ddayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        var y = 240f
        displayItems.forEach { item ->
            val isChecked = item.isChecked
            // 완료 여부 체크박스
            checkPaint.color = if (isChecked) subTextColor else textColor
            canvas.drawText(if (isChecked) "☑" else "☐", 80f, y, checkPaint)
            // 이모지
            canvas.drawText(item.getEmoji(), 140f, y, emojiPaint)
            // 제목 (완료 시 취소선 + 흐린 색)
            titlePaint.color = if (isChecked) subTextColor else textColor
            titlePaint.flags = if (isChecked) {
                titlePaint.flags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                titlePaint.flags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            val title = if (item.title.length > 14) item.title.take(14) + "..." else item.title
            canvas.drawText(title, 210f, y, titlePaint)
            // D-Day 텍스트
            ddayPaint.color = if (isChecked) subTextColor else textColor
            val dday = item.date?.let { calculateDday(it) } ?: ""
            canvas.drawText(dday, width - 80f, y, ddayPaint)
            y += 80f
        }

        // "외 N개"
        if (remaining > 0) {
            val morePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("외 ${remaining}개", width / 2f, y, morePaint)
        }

        // 워터마크
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dayli", width / 2f, height - 40f, appPaint)

        return bitmap
    }

    private fun createTodoShareImage(item: DdayItem): Bitmap {
        val width = 1080
        val subTasks = item.getSubTaskList()
        val maxDisplay = 10
        val displayTasks = subTasks.take(maxDisplay)
        val remaining = subTasks.size - maxDisplay
        val completedCount = subTasks.count { it.isChecked }
        val totalCount = subTasks.size

        // 높이 계산: 헤더(200) + 체크리스트(항목당 70) + 외 N개(60) + 진행률(80) + 워터마크(80) + 여백
        val listHeight = displayTasks.size * 70
        val extraLine = if (remaining > 0) 60 else 0
        val progressLine = if (totalCount > 0) 80 else 0
        val height = (280 + listHeight + extraLine + progressLine + 100).coerceIn(600, 1600)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경
        val bgColor = item.getColorLong().toInt()
        val bgPaint = Paint().apply { color = bgColor }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 48f, 48f, bgPaint)

        // 텍스트 색 결정
        val r = android.graphics.Color.red(bgColor) / 255f
        val g = android.graphics.Color.green(bgColor) / 255f
        val b = android.graphics.Color.blue(bgColor) / 255f
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val textColor = if (luminance > 0.5f) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        val subTextColor = if (luminance > 0.5f) 0xFF555555.toInt() else 0xFFCCCCCC.toInt()
        val checkColor = if (luminance > 0.5f) 0xFF4CAF50.toInt() else 0xFF81C784.toInt()

        // 상단: 이모지 + 제목
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 72f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
        }
        val titleText = if (item.title.length > 18) item.title.take(18) + "..." else item.title
        canvas.drawText(item.getEmoji(), 80f, 110f, emojiPaint)
        canvas.drawText(titleText, 170f, 110f, titlePaint)

        // 구분선
        val linePaint = Paint().apply {
            color = subTextColor
            alpha = 80
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 150f, width - 80f, 150f, linePaint)

        // 체크리스트 항목
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f
        }
        var y = 210f
        displayTasks.forEach { subTask ->
            val checkbox = if (subTask.isChecked) "☑" else "☐"
            // 체크박스
            itemPaint.color = if (subTask.isChecked) checkColor else subTextColor
            canvas.drawText(checkbox, 100f, y, itemPaint)
            // 텍스트
            itemPaint.color = if (subTask.isChecked) subTextColor else textColor
            itemPaint.typeface = Typeface.DEFAULT
            val taskText = if (subTask.title.length > 22) subTask.title.take(22) + "..." else subTask.title
            canvas.drawText(taskText, 170f, y, itemPaint)
            y += 70f
        }

        // "외 N개" 표시
        if (remaining > 0) {
            val morePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = 36f
            }
            canvas.drawText("외 ${remaining}개", 170f, y, morePaint)
            y += 60f
        }

        // 진행률
        if (totalCount > 0) {
            y += 10f
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$completedCount / $totalCount 완료", width / 2f, y, progressPaint)

            // 프로그레스 바
            y += 25f
            val barLeft = 200f
            val barRight = width - 200f
            val barHeight = 16f
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            // 배경 바
            barPaint.color = subTextColor
            barPaint.alpha = 60
            canvas.drawRoundRect(RectF(barLeft, y, barRight, y + barHeight), 8f, 8f, barPaint)
            // 진행 바
            val progress = completedCount.toFloat() / totalCount
            barPaint.color = checkColor
            barPaint.alpha = 255
            val progressRight = barLeft + (barRight - barLeft) * progress
            canvas.drawRoundRect(RectF(barLeft, y, progressRight, y + barHeight), 8f, 8f, barPaint)
        }

        // 워터마크
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dayli", width / 2f, height - 40f, appPaint)

        return bitmap
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val cacheDir = File(context.cacheDir, "shared_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "dday_share.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}

package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object GeminiParser {

    // Firebase AI Logic 경유 — API 키가 APK에 포함되지 않음 (google-services.json 기반)
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.6-flash")
    }

    data class ParsedEvent(
        val title: String? = null,
        val date: String? = null,
        val timeHour: Int? = null,
        val timeMinute: Int? = null,
        val repeatType: String? = null,
        val subTasks: List<String> = emptyList(),
        val emoji: String? = null,
        val memo: String? = null,
        val groupName: String? = null,
        val groupEmoji: String? = null,
        val deadline: Boolean = false
    )

    // 이미지 1장당 전송 픽셀 상한 (글자가 뭉개지지 않으면서 토큰 비용 제한)
    private const val MAX_IMAGE_PIXELS = 4_000_000
    // 세로가 가로의 이 배수보다 길면 스크롤 캡처로 보고 여러 장으로 나눔 (통째로 줄이면 글자가 안 읽힘)
    private const val TALL_RATIO = 2.5f
    private const val MAX_SLICES = 6

    /**
     * 갤러리 이미지를 AI 전송용 Bitmap 목록으로 로드
     * - 사진: EXIF 회전 반영, 큰 이미지는 2의 배수로 축소 → 1장
     * - 긴 스크롤 캡처: 가로 해상도를 유지한 채 위에서부터 겹치게 잘라 최대 6장
     */
    suspend fun loadImagesForAi(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext emptyList()
            val rotation = readExifRotation(context, uri)

            if (rotation == 0 && h > w * TALL_RATIO) {
                decodeSlices(context, uri, w, h)
            } else {
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(w.toLong() * h) }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@withContext emptyList()
                listOf(if (rotation != 0) rotate(bitmap, rotation) else bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiParser", "이미지 로드 실패", e)
            emptyList()
        }
    }

    private fun sampleSizeFor(pixels: Long): Int {
        var sample = 1
        while (pixels / (sample.toLong() * sample) > MAX_IMAGE_PIXELS) sample *= 2
        return sample
    }

    @Suppress("DEPRECATION")  // newInstance(InputStream, Boolean)는 API 31에서 deprecated지만 minSdk 24 지원 위해 사용
    private fun decodeSlices(context: Context, uri: Uri, w: Int, h: Int): List<Bitmap> {
        val overlap = w / 10  // 경계에 걸친 항목이 잘리지 않도록 겹침
        val sliceH = maxOf(w * 2, (h + overlap * (MAX_SLICES - 1)) / MAX_SLICES + 1)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(w.toLong() * sliceH) }
        val decoder = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapRegionDecoder.newInstance(it, false)
        } ?: return emptyList()
        try {
            val slices = mutableListOf<Bitmap>()
            var top = 0
            while (top < h && slices.size < MAX_SLICES) {
                val bottom = minOf(h, top + sliceH)
                decoder.decodeRegion(android.graphics.Rect(0, top, w, bottom), opts)?.let { slices.add(it) }
                if (bottom == h) break
                top = bottom - overlap
            }
            return slices
        } finally {
            decoder.recycle()
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (android.media.ExifInterface(stream).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
            )) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) { 0 }  // PNG 등 EXIF 없는 형식

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    /** UI 테스트 전용: 설정하면 실제 Gemini 호출 대신 이 결과를 사용 (앱 코드에서는 항상 null) */
    @androidx.annotation.VisibleForTesting
    internal var parseOverrideForTest: (suspend (text: String, images: List<Bitmap>) -> List<ParsedEvent>)? = null

    /** 테스트 전용: 설정하면 Gemini 대신 이 함수가 응답 글자를 돌려주거나 실패를 던짐 (오류 전달 경로 검증용) */
    @androidx.annotation.VisibleForTesting
    internal var modelCallOverrideForTest: (suspend () -> String)? = null

    suspend fun parse(
        context: Context,
        text: String,
        images: List<Bitmap> = emptyList(),
        existingGroups: List<String> = emptyList()
    ): List<ParsedEvent> {
        parseOverrideForTest?.let { fake -> return fake(text, images).map { normalizeDeadline(it) } }
        DdaySettings.recordAiCall(context)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sourceSection = if (images.isNotEmpty()) {
            val extra = if (text.isNotBlank()) "\n사용자 추가 설명 (이미지보다 우선 적용):\n$text\n" else ""
            val multi = if (images.size > 1) {
                "\n첨부 이미지 ${images.size}장은 긴 캡처 한 장을 위에서부터 순서대로 나눈 것입니다. 경계에서 겹쳐 두 장에 모두 보이는 항목은 한 번만 추출하세요.\n"
            } else ""
            """
첨부한 이미지(캡처)에서 일정/이벤트 정보를 추출하세요.$multi
이미지에 보이는 과목명, 교수명, 활동명 등 글자는 이미지에 적힌 그대로 옮겨 적으세요 (임의 교정, 띄어쓰기 변경, 축약 금지).
오늘 날짜: $today
$extra"""
        } else {
            """
다음 텍스트에서 일정/이벤트 정보를 추출하세요.
오늘 날짜: $today

텍스트:
$text
"""
        }
        val groupList = if (existingGroups.isEmpty()) "(없음)" else existingGroups.joinToString(", ") { "\"$it\"" }
        val prompt = """
$sourceSection

반드시 아래 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

여러 개의 개별 일정(각각 다른 날짜/시간을 가진 항목)이면 배열로 반환:
[
  {"title": "제목1", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null, "groupName": null, "groupEmoji": null, "deadline": false},
  {"title": "제목2", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null, "groupName": null, "groupEmoji": null, "deadline": false}
]

단일 일정이면 객체 1개로 반환:
{"title": "제목", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null, "groupName": null, "groupEmoji": null, "deadline": false}

그룹 규칙 (groupName):
- 사용자의 기존 그룹 목록: $groupList
- 일정이 기존 그룹 중 하나에 명확히 어울리면 groupName에 그 그룹 이름을 목록에 적힌 글자 그대로 넣기
- 학교 수업 관련 일정(퀴즈, 과제, 레포트, 중간·기말고사, 수업, 강의, 휴강, 보강, 수강신청, LMS 학습활동 등)은 반드시 그룹에 넣기:
  기존 그룹 중 학교/학업/공부/수업/대학/과목 성격의 그룹이 있으면 그 이름을 그대로 사용, 없으면 groupName "학교", groupEmoji "🏫"
- 학교 수업 판단 기준: 과목명·교수명·학기·주차·중간/기말고사·LMS 화면 등 학교 맥락이 있을 때만
- 자격증·국가고시·어학시험(노무사, 공무원, 토익, 한국사 등)이나 학교 맥락이 없는 시험은 학교 일정이 아님 → 기존 그룹 중 어울리는 것(예: 시험, 자격증)이 있을 때만 사용, 없으면 groupName null
- 기존 그룹을 사용할 때 groupEmoji는 null
- 어울리는 그룹이 없는 일반 일정은 groupName null (학교 일정 외에는 새 그룹을 만들지 말 것)
- 한 번에 추출한 학교 일정들은 모두 같은 groupName 사용

마감 규칙 (deadline):
- "제출마감", "마감", "기한", "까지" 등 마감 시각을 나타내는 일정이면 deadline true, 아니면 false
- 마감 날짜/시각은 원문에 적힌 값 그대로 date/timeHour/timeMinute에 넣기 (자정 보정은 앱에서 처리)

LMS 학습활동 목록 규칙 (과목명·교수명·활동명·제출마감이 반복되는 목록 캡처):
- 각 활동을 별도 일정으로 추출 (아래 교시 묶음 규칙은 적용하지 않음)
- title: 활동명에 과목명이 이미 들어 있으면 활동명 그대로, 없으면 "과목명 활동명" 형식. 예: "회계정보시스템 5주차 퀴즈", "생활법률 5주차 퀴즈"
- 활동 유형(퀴즈/과제/시험)과 교수명은 memo에 "유형 | 교수명" 형식으로. 예: "퀴즈 | 이원주 교수"
- D-3, D-10 같은 남은 일수 표시와 탭 이름(진행중/종료된/예정된 학습활동)은 무시
- emoji: 퀴즈·시험→📝, 과제·레포트→📄

규칙:
- 날짜가 "다음 주 월요일" 같은 상대 표현이면 오늘 기준으로 계산
- 시간 정보가 없으면 timeHour, timeMinute는 null
- 반복 정보가 없으면 repeatType은 "NONE"
- 제목은 핵심만 간결하게 추출
- 텍스트가 레시피/요리법이면: 요리 이름을 title로, 재료 목록을 subTasks로 반환
- 텍스트가 할 일 목록이면: 대표 제목을 title로, 각 항목을 subTasks로 반환
- 텍스트가 쇼핑/장보기 목록이면: "쇼핑" 등을 title로, 각 품목을 subTasks로 반환
- 쇼핑 목록 파싱 시: 제품명만 추출. 가격, 쇼핑몰명, 버튼 텍스트, 광고 문구는 모두 제외
- 제품명에서 [브랜드명] 같은 대괄호 접두사는 유지하되 간결하게 정리
- 단순 일정이면 subTasks는 빈 배열 []

시험/수업 일정 파싱 규칙 (표 형태 OCR 텍스트):
- 텍스트가 시험 시간표/수업 일정표처럼 보이면 아래 규칙을 반드시 적용
- 핵심 원칙: 같은 교시(같은 시험 시간대)에 속하는 과목들은 하나의 D-Day로 묶기
- 분리 단위는 "과목"이 아니라 "교시/시간대". 교시가 다르면 별도 D-Day, 같은 교시면 하나의 D-Day
- 같은 교시의 여러 과목 → title에 쉼표로 합침. 예: "노동법(1), 노동법(2)"
- "A, B 중 1과목" 같은 선택 과목 표현은 그대로 유지. 예: "경제학원론, 경영학개론 중 1과목"
- 하이픈(-), 가운데점(·), 점(.)으로 연결된 텍스트는 분리하지 말고 하나의 과목명으로 유지
- 시험 시간 파싱: "09:30~10:50", "09:30-10:50" 형식에서 시작 시간만 추출하여 timeHour/timeMinute에 설정
- 과목명이 아닌 텍스트(배점, 문항수, 입실시간, 유의사항, 표 헤더)는 title에 절대 포함하지 않음
- 날짜 전파: 과목에 날짜가 명시되지 않으면 직전에 나온 날짜를 사용
- memo 형식: "교시 | 시간범위 | 시험시간 | 문항수" 순서로, 있는 정보만 " | "로 연결 (없는 항목은 생략)
- 부가 정보(계산기 사용 가능, 과목별 문항수 등)도 memo에 포함
- 예시 입력: "5.25(토) 1교시 입실09:00 09:30~10:50(80분) (1)노동법(1) (2)노동법(2) 과목별40문항 2교시 입실11:10 11:20~13:20(120분) (3)민법 (4)사회보험법 경제학원론,경영학개론 중 1과목"
  → 결과: [
    {"title":"노동법(1), 노동법(2)","date":"2026-05-25","timeHour":9,"timeMinute":30,"emoji":"📝","memo":"1교시 | 09:30~10:50 | 80분 | 과목별 40문항"},
    {"title":"민법, 사회보험법, 경제학원론·경영학개론 중 1과목","date":"2026-05-25","timeHour":11,"timeMinute":20,"emoji":"📝","memo":"2교시 | 11:20~13:20 | 120분"}
  ]
- emoji는 모든 시험 과목에 📝 사용
이모지 선택 규칙:
- emoji는 반드시 title 내용에 가장 구체적으로 어울리는 이모지 1개를 선택
- 📌 같은 범용 이모지는 정말 어울리는 것이 하나도 없을 때만 최후의 수단으로 사용
- 복수 일정 배열에서도 각 항목마다 개별적으로 어울리는 이모지 선택 (전부 같은 이모지로 통일 금지, 단 시험 시간표는 예외로 모두 📝)
- 아래 예시를 참고하되, 목록에 없는 주제면 가장 잘 어울리는 이모지를 직접 선택:
  시험/공부→📝/📚, 치과→🦷, 병원/진료→🏥, 예방접종/주사→💉, 약 복용→💊,
  미용실/헤어→💇, 네일→💅, 생일→🎂, 기념일/데이트→💕, 결혼식→💍, 돌잔치→👶,
  여행→✈️, 캠핑→🏕️, 등산→⛰️, 바다/수영→🏖️, 영화→🎬, 콘서트/공연→🎤, 전시→🖼️,
  운동/헬스→💪, 러닝/마라톤→🏃, 축구→⚽, 야구→⚾, 요가→🧘,
  회의→💼, 면접→👔, 출장→🧳, 마감/제출→⏰, 발표→📢,
  월급/급여→💰, 카드결제/납부→💳, 공과금/청구서→🧾, 세금→🏛️, 적금/저축→🏦, 보험→🛡️,
  이사→📦, 택배/배송→🚚, 청소→🧹, 빨래→🧺, 장보기/쇼핑→🛍️, 레시피/요리→🍳,
  자동차/정비→🚗, 주유→⛽, 반려동물→🐶/🐱, 화분/식물→🪴,
  약속/모임→🤝, 회식/술자리→🍻, 식사/맛집→🍽️, 카페→☕,
  개강/개학→🏫, 졸업→🎓, 수강신청→🖥️, 자격증→📜, 독서→📖,
  예매/티켓→🎫, 구독 결제→🔄, 휴가/연차→🌴, 명절→🌕, 크리스마스→🎄, 새해→🎆
- 대화/채팅/문자 내용에서 약속이 감지되면: 제목은 "약속" 또는 약속 목적, 장소 정보는 memo에 입력
- memo는 장소, 주소, 참고사항 등 부가 정보가 있을 때만 사용. 없으면 null
""".trimIndent()

        // 실패하면 원래 예외를 그대로 던짐 → 화면에서 AiErrors가 원인(지출 상한·요청 제한·네트워크)별로 안내
        try {
            val responseText = modelCallOverrideForTest?.invoke() ?: run {
                val response = if (images.isNotEmpty()) {
                    model.generateContent(content {
                        images.forEach { image(it) }
                        text(prompt)
                    })
                } else {
                    model.generateContent(prompt)
                }
                response.text ?: throw IllegalStateException("응답이 비어있습니다")
            }
            return parseResponse(responseText).map { normalizeDeadline(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GeminiParser", "Gemini 호출 실패: ${e.javaClass.simpleName}", e)
            throw e
        }
    }

    /**
     * "10-13 00:00 마감"은 사실상 12일 자정까지라는 뜻 → 전날 23:59로 보정해 D-Day가 하루 밀리지 않게 함
     */
    internal fun normalizeDeadline(event: ParsedEvent): ParsedEvent {
        if (!event.deadline || event.timeHour != 0 || (event.timeMinute ?: 0) != 0) return event
        val dateStr = event.date ?: return event
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val parsed = try { format.parse(dateStr) } catch (_: Exception) { null } ?: return event
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, -1)
        }
        return event.copy(date = format.format(cal.time), timeHour = 23, timeMinute = 59)
    }

    /**
     * AI가 제안한 그룹 이름을 기존 그룹과 대조. 공백/대소문자만 다르면 기존 그룹 철자를 그대로 사용
     * @return (최종 그룹 이름, 새로 만드는 그룹인지)
     */
    internal fun resolveGroupName(suggested: String?, existingGroups: List<String>): Pair<String?, Boolean> {
        val name = suggested?.trim()?.takeIf { it.isNotEmpty() } ?: return null to false
        existingGroups.firstOrNull { it == name }?.let { return it to false }
        val key = name.replace(" ", "").lowercase()
        existingGroups.firstOrNull { it.replace(" ", "").lowercase() == key }?.let { return it to false }
        return name to true
    }

    internal fun parseResponse(text: String): List<ParsedEvent> {
        val trimmed = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        // JSON 배열인지 확인
        val arrayMatch = Regex("""\[[\s\S]*\]""").find(trimmed)
        if (arrayMatch != null) {
            try {
                val arr = JSONArray(arrayMatch.value)
                val results = mutableListOf<ParsedEvent>()
                for (i in 0 until arr.length()) {
                    val event = parseJsonObject(arr.getJSONObject(i))
                    if (event.title != null) results.add(event)
                }
                if (results.isNotEmpty()) return results
            } catch (_: Exception) {}
        }

        // 단일 JSON 객체
        val objMatch = Regex("""\{[\s\S]*\}""").find(trimmed)
        if (objMatch != null) {
            try {
                val event = parseJsonObject(JSONObject(objMatch.value))
                return listOf(event)
            } catch (_: Exception) {}
        }

        return listOf(ParsedEvent())
    }

    private fun parseJsonObject(json: JSONObject): ParsedEvent {
        val subTaskList = mutableListOf<String>()
        if (json.has("subTasks") && !json.isNull("subTasks")) {
            val arr = json.optJSONArray("subTasks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotBlank()) subTaskList.add(item)
            }
        }
        return ParsedEvent(
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
            date = json.optString("date").takeIf { it.isNotBlank() && it != "null" },
            timeHour = if (json.has("timeHour") && !json.isNull("timeHour")) json.optInt("timeHour", -1).takeIf { it in 0..23 } else null,
            timeMinute = if (json.has("timeMinute") && !json.isNull("timeMinute")) json.optInt("timeMinute", -1).takeIf { it in 0..59 } else null,
            repeatType = json.optString("repeatType").takeIf {
                it in listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")
            },
            subTasks = subTaskList,
            emoji = json.optString("emoji").takeIf { it.isNotBlank() && it != "null" },
            memo = json.optString("memo").takeIf { it.isNotBlank() && it != "null" },
            groupName = json.optString("groupName").takeIf { it.isNotBlank() && it != "null" },
            groupEmoji = json.optString("groupEmoji").takeIf { it.isNotBlank() && it != "null" },
            deadline = json.optBoolean("deadline", false)
        )
    }
}

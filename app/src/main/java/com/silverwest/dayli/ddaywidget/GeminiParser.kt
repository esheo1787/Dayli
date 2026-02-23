package com.silverwest.dayli.ddaywidget

import com.google.ai.client.generativeai.GenerativeModel
import com.silverwest.dayli.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object GeminiParser {

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    data class ParsedEvent(
        val title: String? = null,
        val date: String? = null,
        val timeHour: Int? = null,
        val timeMinute: Int? = null,
        val repeatType: String? = null,
        val subTasks: List<String> = emptyList(),
        val emoji: String? = null,
        val memo: String? = null
    )

    suspend fun parse(text: String): List<ParsedEvent> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prompt = """
다음 텍스트에서 일정/이벤트 정보를 추출하세요.
오늘 날짜: $today

텍스트:
$text

반드시 아래 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

여러 개의 개별 일정(각각 다른 날짜/시간을 가진 항목)이면 배열로 반환:
[
  {"title": "제목1", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null},
  {"title": "제목2", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null}
]

단일 일정이면 객체 1개로 반환:
{"title": "제목", "date": "yyyy-MM-dd", "timeHour": null, "timeMinute": null, "repeatType": "NONE", "subTasks": [], "emoji": "📌", "memo": null}

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
- emoji는 내용에 가장 어울리는 이모지 1개를 추천 (예: 레시피→🍳, 쇼핑→🛍️, 운동→💪, 공부→📚, 면접→👔, 여행→✈️, 병원→🏥, 생일→🎂, 회의→💼, 시험→📝, 약속→🤝)
- 대화/채팅/문자 내용에서 약속이 감지되면: 제목은 "약속" 또는 약속 목적, 장소 정보는 memo에 입력
- memo는 장소, 주소, 참고사항 등 부가 정보가 있을 때만 사용. 없으면 null
""".trimIndent()

        try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw Exception("응답이 비어있습니다")
            return parseResponse(responseText)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "알 수 없는 오류"
            when {
                "404" in msg || "NOT_FOUND" in msg -> throw Exception("네트워크 오류. 잠시 후 다시 시도해주세요.")
                "429" in msg || "RESOURCE_EXHAUSTED" in msg -> throw Exception("요청 한도 초과. 잠시 후 다시 시도해주세요.")
                "403" in msg -> throw Exception("API 인증 오류")
                else -> throw Exception("분석 실패. 네트워크를 확인해주세요.")
            }
        }
    }

    private fun parseResponse(text: String): List<ParsedEvent> {
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
            memo = json.optString("memo").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}

package com.silverwest.dayli.ddaywidget

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 실제 Gemini 호출로 이미지 자동 입력을 확인하는 수동 테스트 (호출 1회 = 소액 과금)
 *
 * 평소 테스트 실행에서는 건너뜀. 아래 인자를 모두 줄 때만 실행:
 *  - firebaseAppCheckDebugSecret: Firebase 콘솔 App Check에 등록한 디버그 토큰 (Firebase SDK가 이 인자를 읽음)
 *  - liveImagePath: 기기에 복사한 테스트 이미지 경로 (개인정보 포함 캡처는 저장소에 넣지 않음)
 *
 * adb shell am instrument -w -e firebaseAppCheckDebugSecret <토큰> -e liveImagePath /data/local/tmp/x.png \
 *   -e class com.silverwest.dayli.ddaywidget.GeminiLiveImageTest com.silverwest.dayli.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class GeminiLiveImageTest {

    @Test
    fun parseLmsScreenshot() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("liveImagePath")
        assumeTrue("liveImagePath 인자 없음 → 건너뜀", path != null && args.getString("firebaseAppCheckDebugSecret") != null)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // /data/local/tmp는 앱이 직접 못 읽을 수 있어 캐시로 복사
        val local = File(context.cacheDir, "live_input.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cat $path").use { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    local.outputStream().use { input.copyTo(it) }
                }
            }

        val images = GeminiParser.loadImagesForAi(context, Uri.fromFile(local))
        check(images.isNotEmpty()) { "이미지 로드 실패" }

        // liveGroups 인자로 사용자의 기존 그룹 목록을 흉내 (예: "대학교,기념일")
        val groups = args.getString("liveGroups")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf("기념일", "여행")
        val events = GeminiParser.parse(context, "", images, existingGroups = groups)
            .map { it.copy(groupName = GeminiParser.resolveGroupName(it.groupName, groups).first) }

        // 결과는 logcat(태그 GeminiLive)로 확인
        Log.i("GeminiLive", "groups=$groups images=${images.size} events=${events.size}")
        events.forEach {
            Log.i(
                "GeminiLive",
                "${it.emoji} | ${it.title} | ${it.date} ${it.timeHour}:${it.timeMinute} | group=${it.groupName} ${it.groupEmoji} | memo=${it.memo}"
            )
        }
        check(events.isNotEmpty() && events.all { it.title != null }) { "일정 추출 실패" }
    }
}

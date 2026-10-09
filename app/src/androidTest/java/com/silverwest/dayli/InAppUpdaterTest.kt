package com.silverwest.dayli

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.silverwest.dayli.ddaywidget.DdaySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 앱 내 업데이트 흐름을 Play의 FakeAppUpdateManager로 재현
 * (실제 Play 스토어 없이: 업데이트 있음 → 확인 창 → 다운로드 → 재시작 안내 → 설치)
 */
@RunWith(AndroidJUnit4::class)
class InAppUpdaterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("dday_widget_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            var ok = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ok = condition() }
            if (ok) return
            Thread.sleep(100)
        }
        throw AssertionError("시간 내 조건 충족 안 됨")
    }

    @Test
    fun updateAvailable_showsPrompt_downloads_thenAsksRestart_andInstalls() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var fake: FakeAppUpdateManager
            lateinit var updater: InAppUpdater
            var readyToInstall = false
            scenario.onActivity { activity ->
                fake = FakeAppUpdateManager(activity).apply { setUpdateAvailable(999) }
                updater = InAppUpdater(activity, fake) { readyToInstall = true }
                updater.checkForUpdate()
            }
            // Play의 "업데이트하시겠습니까?" 창
            waitFor { fake.isConfirmationDialogVisible }

            scenario.onActivity {
                fake.userAcceptsUpdate()
                fake.downloadStarts()
                fake.downloadCompletes()
            }
            // 다운로드 완료 → 앱의 "재시작" 다이얼로그를 띄우라는 콜백
            waitFor { readyToInstall }

            scenario.onActivity { updater.completeUpdate() }
            waitFor { fake.isInstallSplashScreenVisible }
            scenario.onActivity { updater.release() }
        }
    }

    @Test
    fun declinedVersion_notAskedAgain_butNewerVersionIs() {
        DdaySettings.recordUpdateDeclined(context, 999, System.currentTimeMillis())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var fake: FakeAppUpdateManager
            scenario.onActivity { activity ->
                fake = FakeAppUpdateManager(activity).apply { setUpdateAvailable(999) }
                InAppUpdater(activity, fake) {}.also { it.checkForUpdate() }
            }
            Thread.sleep(1_000)
            var visible = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync { visible = fake.isConfirmationDialogVisible }
            assertFalse("'나중에' 누른 버전은 3일간 다시 묻지 않음", visible)

            // 더 새 버전이 나오면 바로 다시 물어봄
            lateinit var fake2: FakeAppUpdateManager
            scenario.onActivity { activity ->
                fake2 = FakeAppUpdateManager(activity).apply { setUpdateAvailable(1000) }
                InAppUpdater(activity, fake2) {}.also { it.checkForUpdate() }
            }
            waitFor { fake2.isConfirmationDialogVisible }
            assertTrue(true)
        }
    }
}

package com.silverwest.dayli

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.silverwest.dayli.ddaywidget.DdaySettings

/**
 * Google Play 앱 내 업데이트 (유연한 업데이트)
 * - 새 버전이 있으면 Play의 "업데이트하시겠습니까?" 창 표시 → 수락 시 백그라운드 다운로드
 * - 다운로드 완료되면 onReadyToInstall 호출 → 앱에서 "재시작" 다이얼로그 표시 후 completeUpdate()
 * - "나중에"를 누른 버전은 며칠간 다시 묻지 않음
 * - Play 스토어 설치본에서만 동작 (debug/사이드로드 설치는 조회 실패 → 조용히 무시)
 */
class InAppUpdater(
    private val activity: ComponentActivity,
    // 테스트에서는 Play의 FakeAppUpdateManager를 넣어 업데이트 흐름을 재현
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
    private val onReadyToInstall: () -> Unit
) {
    // registry에 직접 등록 (lifecycle 시점 제약 없음, release()에서 해제)
    // 화면 회전으로 Activity가 재생성돼도 같은 키로 다시 등록되어 결과를 받음
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.activityResultRegistry.register(
            "dayli_in_app_update", ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                // 사용자가 "나중에"/뒤로가기 → 이 버전은 당분간 다시 묻지 않음
                // (화면 회전 등으로 Activity가 재생성돼도 버전을 잃지 않도록 prefs에 보관된 값 사용)
                DdaySettings.recordUpdateDeclined(
                    activity, DdaySettings.getPendingUpdateVersion(activity), System.currentTimeMillis()
                )
            }
        }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) onReadyToInstall()
    }

    /** onCreate에서 1회 호출 */
    fun checkForUpdate() {
        manager.registerListener(installListener)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                // 조회가 끝나기 전에 화면이 닫혔으면(launcher 해제됨) 띄우지 않음 → 다음 실행 때 다시 확인
                if (!isActive()) return@addOnSuccessListener
                if (shouldPrompt(info)) {
                    DdaySettings.setPendingUpdateVersion(activity, info.availableVersionCode())
                    try {
                        manager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    } catch (e: IllegalStateException) {
                        android.util.Log.w("InAppUpdater", "업데이트 창 표시 실패", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.d("InAppUpdater", "업데이트 확인 불가 (Play 설치본 아님 등): ${e.message}")
            }
    }

    /** onResume에서 호출: 다른 화면 갔다 오는 사이 다운로드가 끝났으면 재시작 안내 */
    fun onResume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (isActive() && info.installStatus() == InstallStatus.DOWNLOADED) onReadyToInstall()
        }
    }

    /** 다운로드된 업데이트 설치 (앱이 재시작됨) */
    fun completeUpdate() {
        manager.completeUpdate()
    }

    /** onDestroy에서 호출 */
    fun release() {
        manager.unregisterListener(installListener)
        launcher.unregister()
    }

    private fun isActive(): Boolean =
        !activity.isFinishing && !activity.isDestroyed &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun shouldPrompt(info: AppUpdateInfo): Boolean {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return false
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return false
        return !DdaySettings.isUpdateDeclinedRecently(
            activity, info.availableVersionCode(), System.currentTimeMillis()
        )
    }
}

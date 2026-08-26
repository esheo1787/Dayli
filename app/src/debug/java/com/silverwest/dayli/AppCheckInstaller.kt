package com.silverwest.dayli

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug 빌드용 App Check — 디버그 토큰 방식.
 * 최초 실행 시 logcat에 "Enter this debug secret ..." 토큰이 출력됨.
 * → Firebase 콘솔 > App Check > 앱 > 디버그 토큰 관리에 등록해야 호출 허용됨.
 */
fun installAppCheckProvider() {
    Firebase.appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
}

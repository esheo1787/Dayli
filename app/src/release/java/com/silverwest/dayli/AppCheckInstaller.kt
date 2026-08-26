package com.silverwest.dayli

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release 빌드용 App Check — Play Integrity 방식.
 * Play 스토어에서 설치된 정품 앱만 Firebase AI Logic 호출 가능.
 * 사전 조건: Firebase 콘솔 > App Check에서 앱에 Play Integrity 프로바이더 등록.
 */
fun installAppCheckProvider() {
    Firebase.appCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
}

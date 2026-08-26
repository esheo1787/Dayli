package com.silverwest.dayli

import android.app.Application

/**
 * 앱 전역 초기화.
 * Firebase App Check 프로바이더 설치 — AI Logic 호출 시 정품 앱 검증에 필요.
 * (debug/release 소스셋의 AppCheckInstaller가 각각 Debug/PlayIntegrity 프로바이더를 설치)
 */
class DayliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installAppCheckProvider()
    }
}

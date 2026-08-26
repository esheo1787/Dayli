# Firebase AI Logic 이전 가이드

2026-08 프로젝트 정지 인시던트(APK 내장 Gemini API 키 유출·도용) 재발 방지를 위한 마이그레이션.

## 배경

- 기존: `local.properties`의 `GEMINI_API_KEY` → `BuildConfig` → APK에 키가 그대로 포함 → 디컴파일로 추출 가능
- 변경: Firebase AI Logic 경유. 앱에는 `google-services.json`(Firebase 클라이언트 설정)만 포함되고, Gemini 호출은 Firebase 프록시를 통해 이뤄짐. App Check를 켜면 정품 앱만 호출 가능.

## 코드 변경 (완료됨)

- [x] 루트/앱 gradle에 `com.google.gms.google-services` 플러그인 추가
- [x] `firebase-bom:34.18.0` + `firebase-ai` 의존성 추가, 구 `generativeai:0.9.0` 제거
- [x] `GeminiParser.kt`: `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")`
- [x] `BuildConfig.GEMINI_API_KEY` 제거 (APK에 키 미포함)
- [x] `app/google-services.json` placeholder 생성 (빌드 통과용) + `.gitignore` 등록

## 남은 작업 — Firebase 콘솔 (사용자 직접, 5~10분)

1. https://console.firebase.google.com 접속
2. **프로젝트 추가** — 새 Firebase 프로젝트 생성 (정지된 gen-lang-client-0217993938과 무관한 새 프로젝트 권장)
   - 이름 예: `Dayli`
3. 프로젝트 열기 → 왼쪽 메뉴 **빌드 > AI Logic** (또는 "Firebase AI Logic") → **시작하기**
   - API 선택 화면에서 **Gemini Developer API** 선택 (무료 시작 가능)
4. **프로젝트 설정(⚙️) > 일반 > 내 앱 > Android 앱 추가**
   - 패키지 이름: `com.silverwest.dayli`
   - 디버그 서명 인증서 SHA-1(선택): 나중에 App Check 쓸 때 추가 가능
5. **google-services.json 다운로드** → `MyApplication/app/google-services.json` 에 덮어쓰기 (placeholder 교체)
6. Android Studio에서 Gradle Sync → 빌드 → AI 자동 입력 기능 테스트

## 이후 강화 (권장, 별도 작업)

- **App Check (Play Integrity)** 활성화: 정품 스토어 설치 앱만 API 호출 가능 → 키 추출/도용 원천 차단
  - `firebase-appcheck-playintegrity` 의존성 + 콘솔에서 App Check 등록 → 시행(enforce)
- Firebase 콘솔 **AI Logic 사용량** 대시보드에서 호출량 모니터링
- 기존 `DdaySettings.recordAiCall()` 월별 카운터는 그대로 유지됨

## 참고

- 구 프로젝트(gen-lang-client-0217993938) 이의신청 결과와 무관하게 새 Firebase 프로젝트로 AI 기능 복구 가능
- `local.properties`의 `GEMINI_API_KEY` 항목은 더 이상 사용되지 않음 — 구 키는 복구 후 Cloud Console에서 반드시 삭제할 것

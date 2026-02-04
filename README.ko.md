<div align="center">

[English](README.md) | **한국어**

# 📅 Dayli

**D-Day 카운트다운 & To-Do 체크리스트 — 홈 화면에서 항상 확인하세요**

*잊지 않으려면, 항상 보이게.*

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

</div>

---

## 📱 스크린샷

<div align="center">
<table>
  <tr>
    <td align="center"><b>D-Day 탭</b></td>
    <td align="center"><b>To-Do 탭</b></td>
    <td align="center"><b>다크 모드</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/screenshot_dday.jpg" width="250"/></td>
    <td><img src="screenshots/screenshot_todo.jpg" width="250"/></td>
    <td><img src="screenshots/screenshot_dark.jpg" width="250"/></td>
  </tr>
  <tr>
    <td align="center"><b>혼합 위젯</b></td>
    <td align="center"><b>개별 위젯</b></td>
    <td align="center"><b>템플릿 관리</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/widget_mixed.jpg" width="250"/></td>
    <td><img src="screenshots/widget_separate.jpg" width="250"/></td>
    <td><img src="screenshots/screenshot_template.jpg" width="250"/></td>
  </tr>
</table>
</div>

---

## ✨ 주요 기능

### D-Day 카운트다운
- 중요한 날짜를 등록하고 남은 일수 확인 (D-3~D-2 파란색 / D-1, D-Day, D+N 빨간색)
- 그룹별 분류, 그룹 드래그 순서 변경
- 그룹 관리: 이름 변경, 삭제, 그룹별 이모지 설정
- 임박순/여유순 정렬
- 반복 일정: 매일/매주(요일 선택)/매월/매년
- 미리 표시 일수 설정 (예: 2주 전, 1달 전부터 표시)
- 체크한 반복 항목은 자동 숨김 → 다음 발생일에 자동 복귀
- D-1, D-Day 푸시 알림 (알림 시간, 소리, 진동 설정 가능)

### To-Do 체크리스트
- 하위 항목까지 세분화해서 관리
- 하위 항목 전체 완료 시 자동 체크
- 템플릿 저장 & 불러오기
- 진행률 한눈에 확인 (2/5 등)
- 드래그로 순서 변경
- 내 순서 / 미완료순 / 최근 추가 정렬
- 반복 일정: 매일/매주(요일 선택)/매월/매년

### 홈 화면 위젯
- **혼합 위젯** — D-Day와 To-Do를 함께 표시
- **D-Day 전용 위젯** — 날짜 추적에 집중
- **To-Do 전용 위젯** — 빠른 할 일 체크
- 위젯에서 바로 체크 가능
- 그룹 접기/펼치기 지원
- 글씨 크기, 배경 투명도 커스터마이징

### 커스터마이징
- 시스템 이모지 전체 사용 가능
- 14가지 파스텔 색상 팔레트
- 테마 모드: 시스템 설정 / 라이트 / 다크
- 앱 & 위젯 글씨 크기 조절 (작게 / 보통 / 크게)
- 아이템 배경, 아이콘 배경, 위젯 배경 투명도 개별 설정
- 당겨서 새로고침 (Pull-to-Refresh)
- UI 상태 저장: 마지막 탭, 섹션 펼침/접힘, 하위 체크리스트 펼침 상태

---

## 🛠 기술 스택

| 분류 | 기술 |
|------|------|
| **언어** | Kotlin |
| **UI 프레임워크** | Jetpack Compose |
| **아키텍처** | MVVM |
| **로컬 DB** | Room |
| **위젯** | RemoteViews + AppWidgetProvider |
| **비동기 처리** | Kotlin Coroutines + LiveData |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |

---

## 🏗 아키텍처

```
com.silverwest.dayli
├── MainActivity.kt                  # 앱 진입점
├── ui/theme/                        # 앱 테마 (Color, Theme, Type)
└── ddaywidget/
    ├── DdayScreen.kt               # 메인 화면 (D-Day + To-Do 탭)
    ├── DdayListItem.kt             # 리스트 아이템 컴포저블
    ├── AddEditBottomSheet.kt       # 추가/수정 바텀시트
    ├── SettingsScreen.kt           # 설정 화면
    ├── DdayItem.kt                 # Room 엔티티
    ├── DdayDao.kt                  # Room DAO
    ├── DdayDatabase.kt             # Room 데이터베이스 (v15, 15개 마이그레이션)
    ├── DdayViewModel.kt            # ViewModel
    ├── DdaySettings.kt             # SharedPreferences 헬퍼
    ├── DdayWidgetProvider.kt       # 혼합 위젯 프로바이더
    ├── DdayOnlyWidgetProvider.kt   # D-Day 전용 위젯 프로바이더
    ├── TodoOnlyWidgetProvider.kt   # To-Do 전용 위젯 프로바이더
    ├── NotificationHelper.kt       # 알림 채널 & 전달
    ├── NotificationScheduler.kt    # 알림 스케줄링
    ├── NotificationReceiver.kt     # 브로드캐스트 리시버
    ├── BootReceiver.kt             # 부팅 시 알림 재등록
    ├── EmojiPickerDialog.kt        # 시스템 이모지 피커
    ├── TodoTemplate.kt             # 템플릿 엔티티 & DAO
    └── ...                         # Enum, 컨버터, 유틸리티
```

**MVVM 패턴**을 따르며 ViewModel이 Room DAO를 직접 호출합니다. Room 데이터베이스로 로컬 데이터를 관리하고, LiveData로 앱과 위젯 간 반응형 데이터 업데이트를 구현했습니다.

---

## 🔑 주요 기술적 도전

### 위젯 ↔ 앱 양방향 동기화
앱과 홈 화면 위젯 간 양방향 동기화를 구현했습니다. 앱에서의 변경이 위젯에 즉시 반영되고, 위젯에서의 체크박스 조작이 `AppWidgetManager.notifyAppWidgetViewDataChanged()`를 통해 실시간으로 앱 데이터베이스에 반영됩니다.

### 위젯 내 접기/펼치기 그룹
`RemoteViews`는 Compose에 비해 레이아웃 기능이 제한적이지만, 그 안에서 커스텀 접기/펼치기 그룹 헤더를 구현했습니다. 위젯 업데이트 간에도 펼침/접힘 상태가 유지되도록 관리했습니다.

### 시스템 이모지 피커 통합
`androidx.emoji2:emoji2-emojipicker`를 활용하여 성별 및 피부색 변형을 포함한 모든 시스템 이모지에 접근할 수 있도록 구현했습니다.

### 템플릿 시스템
자주 사용하는 체크리스트 구조를 저장하고 한 번의 탭으로 빠르게 재생성할 수 있는 To-Do 템플릿 저장/불러오기 시스템을 설계했습니다.

---

## 📦 빌드 & 실행

```bash
# 레포지토리 클론
git clone https://github.com/esheo1787/Dayli.git

# Android Studio에서 열기
# 에뮬레이터 또는 실기기에서 빌드 및 실행 (min SDK 24)
```

**요구사항:**
- Android Studio Ladybug 이상
- JDK 17+
- Android SDK 35

---

## 🗺 로드맵

- [x] D-Day & To-Do 핵심 기능
- [x] 홈 화면 위젯 (혼합, D-Day, To-Do)
- [x] 다크모드 & 테마 선택
- [x] 시스템 이모지 피커
- [x] 템플릿 시스템
- [x] 반복 일정 (매일, 매주, 매월, 매년)
- [x] 그룹 관리 & 드래그 순서 변경
- [x] 푸시 알림 (시간/소리/진동 설정)
- [ ] Google Play 스토어 출시
- [ ] 테마 팩 (Clean, Mono)
- [ ] 배너 광고 연동
- [ ] 캘린더 연동
- [ ] 시간 기반 스케줄링
- [ ] 클라우드 백업 & 동기화

---

## 📄 개인정보처리방침

Dayli는 개인정보를 수집하지 않습니다. 모든 데이터는 사용자의 기기에만 저장됩니다.

[개인정보처리방침 보기](https://esheo1787.github.io/Dayli/privacy-policy.html)

---

## 📬 연락처

- **개발자:** silverwest
- **이메일:** heunseo1787@gmail.com

---

<div align="center">

Made with 💛 by silverwest

</div>

# blank mobile

폰 위에서 직접 동작하는 에이전트. 화면을 보고 탭·스와이프·입력으로 기기를 조작하고, 전화를 걸고, 앱을 실행합니다. 데스크톱 [blank](https://github.com/purplemacaron/blank)와 이름·철학만 공유하는 독립 프로젝트입니다.

- 로컬 우선: 계정 없음, 세션·키는 기기에만 저장
- 실제 프로바이더만: OpenAI 호환(LAN Ollama/LM Studio 포함), Anthropic, Gemini
- 승인 기반 안전: 기기 조작·전화는 기본적으로 사용자 확인을 거침
- 한국어 기본 UI

## 빌드

요구사항: Android SDK (platform 36, build-tools 36.0.0), JDK 17+ (Android Studio 번들 JBR 권장).

```sh
# local.properties에 sdk.dir 설정 후
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/
./gradlew test               # JVM 단위 테스트
```

minSdk 30 (Android 11+). 배포는 APK 사이드로딩입니다.

## 상태

M0 스캐폴드 — 채팅 셸과 초록 브랜딩. 에이전트 기능은 M1(채팅 코어) → M2(모바일 유즈) → M3(전화·앱 조작) 순서로 추가됩니다.
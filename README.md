# MBT 키보드

갤럭시용 커스텀 안드로이드 키보드 (Kotlin, InputMethodService)

## 빌드
```
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## 설치 후 설정
1. 앱 실행 → "키보드 활성화" → MBT 키보드 ON
2. "키보드 선택" → MBT 키보드

## 진행 단계
- [x] 1. 프로젝트 세팅 + 빈 IME
- [x] 2. 한글 조합 엔진
- [x] 3. 두벌식·영어 자판
- [ ] 4. 단모음 자판
- [ ] 5. 숫자·기호·이모지
- [ ] 6. 테마·키 크기
- [ ] 7. 클립보드 히스토리
- [ ] 8. 서명 APK

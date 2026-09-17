# SuperGram

A modern Telegram client for Android, built entirely through the terminal/CLI
(Gradle CLI + Android SDK tools — no IDE required).

## Tech stack

| Layer         | Technology                                                    |
|---------------|--------------------------------------------------------------|
| Language      | Kotlin                                                        |
| UI            | Jetpack Compose + Material 3                                  |
| Async         | Coroutines + StateFlow/SharedFlow                             |
| Core          | Telegram TDLib (prebuilt JNI/AAR, no C++ build)              |
| Architecture  | Clean Architecture / MVVM (domain / data / presentation)      |

## Project structure

```
supergram/
├── settings.gradle.kts          # Module & repository configuration
├── build.gradle.kts             # Root plugin declarations
├── gradle/libs.versions.toml     # Centralized dependency catalog
├── local.properties             # (NOT committed) SDK path + Telegram API keys
└── app/
    ├── build.gradle.kts         # App module: Compose, TDLib, BuildConfig wiring
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/supergram/app/
            ├── SuperGramApplication.kt      # Boots the TDLib client
            ├── MainActivity.kt              # Compose entry point
            ├── core/telegram/               # TelegramClientManager (TDLib lifecycle,
            │                                #   SharedFlow updates, auth state machine)
            ├── data/repository/             # TDLib-backed repository implementation
            ├── domain/                      # Pure Kotlin: models, contracts, use cases
            ├── di/                          # Manual dependency container
            └── presentation/                # Compose UI + ViewModels (MVVM)
```

## Sensitive data

Telegram API credentials are **never hardcoded**. They live in
`local.properties` (git-ignored) and are exported via `BuildConfig`:

```properties
TELEGRAM_API_ID=your_api_id
TELEGRAM_API_HASH=your_api_hash
```

## Build (CLI)

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## TDLib dependency

The project uses the prebuilt `io.github.tdlib-android:core` AAR, which bundles
`libtdjni.so` for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` — no native
toolchain or C++ compilation is required.

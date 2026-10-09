# What to wear — заметки для Claude

Android-приложение: погода (усреднение нескольких моделей) + подбор одежды из гардероба пользователя.
Обзор, структура и алгоритм — в [README.md](README.md).

## Как работать с пользователем

- Пишет и читает по-русски; не Android-разработчик. Решения по проекту принимать самому, спрашивать только важное.
- Цикл на шаг: сделать → собрать → тесты → проверить на эмуляторе (скриншоты) → коротко отчитаться.
- Коммиты — только когда пользователь попросит.

## GitHub и релизы (как в Tik Tak)

- CI: `.github/workflows/build.yml`. Push в любую ветку — тесты + APK-артефакт; push в `main` — релиз `build-N`,
  который приложение находит само (`app/.../update/AppUpdater.kt`) и предлагает установить.
- `versionCode` = `github.run_number` (`-PbuildNumber`); локальные сборки — код 1, без автообновления.
- Подпись: ключ `F:\AndroidDev\keys\what-to-wear.jks`, значения для Secrets — в `what-to-wear-github-secrets.txt` там же.
  Ключ, `applicationId = ru.whattowear.app` и `updateRepo` в `app/build.gradle.kts` у выпущенного приложения не менять.

## Локальная среда (Windows)

- JDK 17 и Android SDK — в `F:\AndroidDev` (`jdk-17*`, `sdk`, AVD в `F:\AndroidDev\avd`). Системный JDK 26 для Gradle не подходит.
- Сборка: задать `JAVA_HOME` на `F:\AndroidDev\jdk-17*`, затем
  `gradlew :domain:test :data:testDebugUnitTest assembleDebug` (и `assembleRelease` — проверка R8).
- Эмулятор телефона: `F:/AndroidDev/sdk/emulator/emulator.exe -avd lp_api34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
  (с `ANDROID_SDK_ROOT=F:/AndroidDev/sdk ANDROID_AVD_HOME=F:/AndroidDev/avd`). Планшет — `adb shell wm size 2560x1600; wm density 280`.
- В Git Bash для adb ставить `MSYS_NO_PATHCONV=1`. `adb input text` не умеет кириллицу (город искать как «Moscow»).
  Координаты тапов — в пикселях устройства, скриншоты показываются уменьшенными.
- Эмулятор не отдаёт геолокацию — проверять через ручной выбор города.
- `adb root` даёт `sqlite3 /data/data/ru.whattowear.app/databases/what_to_wear.db` (быстро наполнить гардероб).
- Утреннее уведомление вручную: найти id задачи в `dumpsys jobscheduler`, затем
  `cmd jobscheduler run -f -n androidx.work.systemjobscheduler ru.whattowear.app <id>`.

## Важное

- Схема Room v1, `exportSchema` в `data/schemas`. Изменение схемы — новая версия и явная миграция, без destructive.
- Glance: данные наблюдаются Flow внутри `provideContent`; после обновления APK виджет перерисовывает `AppUpdatedReceiver`.
- Задачи Play Services (геолокация) могут не завершаться — всё под тайм-аутами, флаги «обновляется» сбрасываются в `finally`.
- Стек закреплён: AGP 9.4.1 (встроенный Kotlin), Kotlin 2.4.20, Gradle 9.8.0, compileSdk 37.

# Чи є світло · echecker

**[English](#english) · [Українська](#українська)**

<p align="center">
  <img src="docs/screenshots/app_on.png" width="260" alt="App — power is on (queue 3.1)">
  &nbsp;
  <img src="docs/screenshots/app_off.png" width="260" alt="App — power is off (queue 1.2)">
  &nbsp;
  <img src="docs/screenshots/app_history.png" width="260" alt="App — schedule history">
</p>

---

## English

**echecker** («Чи є світло», *"Is there power?"*) is an Android app with a home-screen widget that tells you whether your
building has electricity right now, according to the scheduled power outage plan for the **Zaporizhzhia region**.

You pick your outage queue once (1.1 … 6.2). After that the app and the widget show:

- **the current status:** *Power is on* or *No power*;
- **when it changes next:** "turns off at 22:00" or "turns on at 22:30", with a live countdown;
- **a 24-hour timeline** for today with a "now" marker;
- **tomorrow's schedule** once it is published;
- **history:** past days' schedules for your queue, with total hours without power.

Schedules come from the public Telegram channel [t.me/zoe_alarm](https://t.me/zoe_alarm). The app reads its public web
preview, so you don't need a Telegram account, an API key or a server. Data refreshes in the background every 30 minutes,
whenever the app is opened, and when you tap **Оновити** (Refresh). Everything is cached locally, so the app and the
widget also work offline.

### Screenshots

The app on a Pixel 9 using the real schedule for 12.11. Queue **3.1** has power and queue **1.2** is in an outage.

| | Power is on (queue 3.1) | No power (queue 1.2) |
|---|:---:|:---:|
| **App** | <img src="docs/screenshots/app_on.png" width="260"> | <img src="docs/screenshots/app_off.png" width="260"> |

### Widget

The widget can be resized, and its layout adapts to the size:

- **2×1:** the status, the next change and a countdown.
- **4×1:** the same, plus a status icon.
- **4×2:** the same, plus a "NOW" label and a timeline of today's outages.

Tapping the widget opens the app.

| Size | Power is on (queue 3.1) | No power (queue 1.2) |
|---|:---:|:---:|
| **2×1** | <img src="docs/screenshots/widget_2x1_on.png" width="190"> | <img src="docs/screenshots/widget_2x1_off.png" width="190"> |
| **4×1** | <img src="docs/screenshots/widget_4x1_on.png" width="380"> | <img src="docs/screenshots/widget_4x1_off.png" width="380"> |
| **4×2** | <img src="docs/screenshots/widget_4x2_on.png" width="380"> | <img src="docs/screenshots/widget_4x2_off.png" width="380"> |

### Tech stack

Kotlin · Jetpack Compose (UI) · Jetpack Glance (widget) · WorkManager · Room · DataStore · OkHttp · Jsoup.
Requires Android 8.0+ (minSdk 26, targetSdk 36).

### Build

```bash
./gradlew assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew installDebug       # install on a connected device / emulator
./gradlew testDebugUnitTest  # unit tests (parser, status, sync)
```

Requires JDK 17 and the Android SDK.

### Limitations

- The app shows **scheduled** outages only. It cannot detect actual ones.
- It does not recognise cancellations ("скасовано") or posts that only partly update a schedule.
- If Telegram changes the layout of its web preview, `ChannelFetcher` has to be updated.

---

## Українська

**Чи є світло** (echecker) — це Android-застосунок із віджетом для домашнього екрана. Він показує, чи є зараз світло у
вашому будинку, за графіком погодинних відключень **Запорізької області**.

Один раз оберіть свою чергу (1.1 … 6.2), і далі застосунок та віджет показуватимуть:

- **поточний стан:** «Світло є» або «Світла немає»;
- **коли буде наступна зміна:** «Вимкнуть о 22:00» чи «Увімкнуть о 22:30», із живим зворотним відліком;
- **шкалу на 24 години** на сьогодні з позначкою «зараз»;
- **графік на завтра**, коли його опублікують;
- **історію:** графіки вашої черги за минулі дні та загальну кількість годин без світла.

Графіки беруться з публічного Telegram-каналу [t.me/zoe_alarm](https://t.me/zoe_alarm). Застосунок читає його
публічну веб-версію, тому не потрібні ні акаунт у Telegram, ні API-ключ, ні сервер. Дані оновлюються у фоні кожні
30 хвилин, щоразу під час відкриття застосунку і після натискання кнопки **Оновити**. Усе зберігається локально, тож
застосунок і віджет працюють навіть без інтернету.

### Скріншоти

Застосунок на Pixel 9 з реальним графіком на 12.11. У черзі **3.1** світло є, у черзі **1.2** — відключення.

| | Світло є (черга 3.1) | Світла немає (черга 1.2) |
|---|:---:|:---:|
| **Застосунок** | <img src="docs/screenshots/app_on.png" width="260"> | <img src="docs/screenshots/app_off.png" width="260"> |
| **Історія** | | <img src="docs/screenshots/app_history.png" width="260"> |

### Віджет

Розмір віджета можна змінювати, і вміст підлаштовується під нього:

- **2×1:** стан, час наступної зміни та відлік.
- **4×1:** те саме плюс іконка стану.
- **4×2:** те саме плюс підпис «ЗАРАЗ» і шкала сьогоднішніх відключень.

Натискання на віджет відкриває застосунок.

| Розмір | Світло є (черга 3.1) | Світла немає (черга 1.2) |
|---|:---:|:---:|
| **2×1** | <img src="docs/screenshots/widget_2x1_on.png" width="190"> | <img src="docs/screenshots/widget_2x1_off.png" width="190"> |
| **4×1** | <img src="docs/screenshots/widget_4x1_on.png" width="380"> | <img src="docs/screenshots/widget_4x1_off.png" width="380"> |
| **4×2** | <img src="docs/screenshots/widget_4x2_on.png" width="380"> | <img src="docs/screenshots/widget_4x2_off.png" width="380"> |

### Технології

Kotlin · Jetpack Compose (UI) · Jetpack Glance (віджет) · WorkManager · Room · DataStore · OkHttp · Jsoup.
Потрібен Android 8.0+ (minSdk 26, targetSdk 36).

### Збірка

```bash
./gradlew assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew installDebug       # встановити на підключений пристрій / емулятор
./gradlew testDebugUnitTest  # юніт-тести (парсер, статус, синхронізація)
```

Потрібні JDK 17 та Android SDK.

### Обмеження

- Застосунок показує лише **заплановані** відключення. Фактичних він не бачить.
- Скасування («скасовано») та пости, які лише частково оновлюють графік, не розпізнаються.
- Якщо Telegram змінить верстку веб-версії каналу, доведеться оновити `ChannelFetcher`.

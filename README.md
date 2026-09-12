# Solar Monitor

A tiny Android app for solar inverters that report to **ShineMonitor** (Eybond).
It shows your live output, today's curve, and your energy history in a clean
screen or two, using the same account you already have.

It replaces the vendor's **SmartClient** app:

| | SmartClient | Solar Monitor |
|---|---|---|
| Download size | over 65 MB | under 100 KB |
| Made by | Eybond, a Chinese manufacturer; closed source | open source, MIT |
| Talks to | the vendor's servers | only `web.shinemonitor.com`, over HTTPS |

No third-party libraries, no analytics, and the only permission is Internet.

> **Unofficial.** Not affiliated with or endorsed by Eybond or ShineMonitor.
> It reads your own plant through your own account and never changes anything
> on the inverter.

## What it shows

- **Dashboard**: live power and percent of your plant's rated size; today, this
  month, this year and lifetime energy, with today's peak, the average per day
  and per month, and the install date. Today's curve with a "now" marker.
  Tap the curve to read the power at any time. Refreshes every minute.
- **History**: Day, Month, Year and Total views. Step with the arrows, tap the
  title to pick a date, or tap a bar to drill in.
- **Inverter details**: every value in the inverter's last report, such as
  string voltages, grid voltage and frequency, and temperatures.
- Works offline with the last data it saw, and tells you how old that data is.

## What it stores on your phone

Only in the app's private storage, never backed up, wiped by **Log out**:

- your ShineMonitor username and the SHA-1 hash of your password (the
  ShineMonitor protocol signs in with this hash, so treat it as a password);
- the session token, valid for 5 days;
- the last responses, so screens open instantly.

## Requirements

- Android 8.0 or newer.
- A ShineMonitor account, the one you use on `shinemonitor.com` or in SmartClient.

## Install

Download `SolarMonitor-x.y.apk` from the Releases page (or the copy in this
repository) and open it on the phone, or run:

```bash
adb install -r SolarMonitor-1.0.apk
```

## Build

JDK 17 and an Android SDK (`ANDROID_HOME` or `local.properties`). The Gradle
wrapper fetches the rest.

```bash
./gradlew testDebugUnitTest   # 83 unit tests, no device needed
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Pushing a `v*` tag builds the APK in CI and attaches it to a GitHub release.
Release builds are debug-signed unless you add your own `keystore.properties`
(see `app/build.gradle.kts`).

## Project layout

```
app/src/main/kotlin/app/solarmonitor/
  api/     request signing, JSON parsing, HTTP client
  repo/    session handling, cache-then-network loading
  store/   preferences and file cache
  time/    periods and plant-timezone dates
  ui/      the four screens and the Canvas chart
app/src/test/                    JUnit tests for everything above the UI
docs/shinemonitor-api.md         how the ShineMonitor API works
.github/workflows/release.yml    tag -> APK release
```

## License

[MIT](LICENSE).

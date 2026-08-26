# Deepr 🔗

![./fastlane/metadata/android/en-US/images/featureGraphic.png](./fastlane/metadata/android/en-US/images/featureGraphic.png)

> Deepr is a native Android application designed to streamline the management and testing of links.
> It provides a simple and efficient way to store, organize, and open links.

[![GitHub Releases](https://img.shields.io/github/v/release/yogeshpaliyal/Deepr?style=for-the-badge)](https://github.com/yogeshpaliyal/Deepr/releases/latest)
[![Latest Master](https://img.shields.io/badge/Master-master?color=7885FF&label=Build&logo=android&style=for-the-badge)](https://github.com/yogeshpaliyal/Deepr/releases/download/latest-master/app-debug.apk)
[![Android Weekly](https://img.shields.io/badge/Android%20Weekly-%23685-2CA3E6.svg?style=for-the-badge)](http://androidweekly.net/issues/issue-685)    



[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-orange.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-purple.svg)](https://m3.material.io/)
## 🎩 🪄 Features

- Search
- Sort
- Open Counter
- Home Screen Shortcuts
- Import/Export links
- QR Code support: Generate and scan
- Organize links by tags
- Manage multiple profiles
- Save link by sharing from other app (eg: chrome, etc.)
- **Silent save option:** Save links from share sheet without opening the app
- Save links to markdown file in local storage. (can be used for obsidian)
- **Local network server:** Access and manage links from other devices on the same network

### 🤖 Local Server Automation (MacroDroid / Tasker / Intent)

You can automatically start, stop, or toggle the Deepr Local Network Server using Android Broadcast Intents from external automation apps like **MacroDroid**, **Tasker**, or via **ADB**. The server binds to all network interfaces, so it works on both **Wi-Fi** and **mobile data**.

- **Target Package**: `com.yogeshpaliyal.deepr`
- **Target Receiver Class**: `com.yogeshpaliyal.deepr.server.LocalServerReceiver`

#### Supported Intent Actions:

| Action | Intent Action String | Description |
|--------|----------------------|-------------|
| **Start Server** | `com.yogeshpaliyal.deepr.ACTION_START_SERVER` | Starts the local server using the saved port |
| **Stop Server** | `com.yogeshpaliyal.deepr.ACTION_STOP_SERVER` | Stops the running local server |
| **Toggle Server** | `com.yogeshpaliyal.deepr.ACTION_TOGGLE_SERVER` | Toggles the local server state (Starts if stopped, Stops if running) |

An optional integer extra `port` can be passed to override the saved port when starting.

#### How to configure in MacroDroid:
1. Add an Action -> **Applications** -> **Send Intent**.
2. Set **Target**: `Broadcast`
3. Set **Action**: `com.yogeshpaliyal.deepr.ACTION_TOGGLE_SERVER` (or `ACTION_START_SERVER` / `ACTION_STOP_SERVER`)
4. Set **Package Name**: `com.yogeshpaliyal.deepr`
5. Set **Class Name**: `com.yogeshpaliyal.deepr.server.LocalServerReceiver`

#### Command-line via ADB:
```bash
# Start server
adb shell am broadcast -a com.yogeshpaliyal.deepr.ACTION_START_SERVER -n com.yogeshpaliyal.deepr/.server.LocalServerReceiver

# Stop server
adb shell am broadcast -a com.yogeshpaliyal.deepr.ACTION_STOP_SERVER -n com.yogeshpaliyal.deepr/.server.LocalServerReceiver

# Toggle server
adb shell am broadcast -a com.yogeshpaliyal.deepr.ACTION_TOGGLE_SERVER -n com.yogeshpaliyal.deepr/.server.LocalServerReceiver
```

### Build Variant specific features

| Feature                           | GitHub Release | F-Droid | Play Store | Pro Version on Play Store |
|-----------------------------------|----------------|---------|------------|-----------|
| Firebase Analytics                | ❌              | ❌       | ✅          | ✅  |
| Google Drive Backup | ❌              | ❌       | ❌         | ✅  |
| Profile-specific themes | ❌              | ❌       | ❌          | ✅  |



## 🏗️ Tech Stack

The application is built using modern Android development practices and libraries:

- **UI:** Jetpack Compose
- **Navigation:** Jetpack Compose Navigation 3
- **ViewModel:** Android ViewModel
- **Database:** SQLDelight
- **Dependency Injection:** Koin
- **Asynchronous Operations:** Kotlin Coroutines
- **HTTP Client & Server:** Ktor

## 📲 Download

You can download from any of the sources mentioned below.  
All these sources support cross platform app updates. for eg: if you've installed app from F-Droid
then you can update the app from any of the sources.

- GitHub Release : [Download](https://github.com/yogeshpaliyal/Deepr/releases/latest)
- F-Droid : [Download](https://f-droid.org/packages/com.yogeshpaliyal.deepr/)
- Play Store: [Download](https://play.google.com/store/apps/details?id=com.yogeshpaliyal.deepr)
- Play Store (All features unlocked) : [Download](https://play.google.com/store/apps/details?id=com.yogeshpaliyal.deepr.pro)



## Special Thanks To

- [ARME](https://github.com/ALE-ARME) : For the unconditional commitment to quality assurance.
  

## Language  
[![Translation status](https://hosted.weblate.org/widget/deepr/horizontal-auto.svg)](https://hosted.weblate.org/engage/deepr/)  
Want to contribute to translations? [Contribute](https://hosted.weblate.org/engage/deepr/)


## Star History

<a href="https://www.star-history.com/#yogeshpaliyal/Deepr&type=date&legend=bottom-right">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=yogeshpaliyal/Deepr&type=date&theme=dark&legend=bottom-right" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=yogeshpaliyal/Deepr&type=date&legend=bottom-right" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=yogeshpaliyal/Deepr&type=date&legend=bottom-right" />
 </picture>
</a>


---

<div align="center">
  <strong>Made with ❤️ using Android & Jetpack Compose</strong>
  <br/>
  <sub>Star ⭐ this repository if you find it helpful!</sub>
</div>

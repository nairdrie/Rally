# 🎾 Rally: Wear OS Tennis Tracker

A modern, lightweight Wear OS application built to track tennis rallies, count racket hits, and log high-frequency biometric sensor data for post-match swing analysis. 

Built with **Kotlin** and **Jetpack Compose for Wear OS**, this app bypasses clunky UI frameworks to deliver a perfectly centered, native smartwatch experience with ultra-low-power background data batching.

## ✨ Features

* **Live Hit Detection:** Uses the watch's gyroscope to detect the exact moment of racket impact and automatically increments the rally counter.
* **Serve vs. Receive Logic:** Accurately calculates the hit sequence depending on who initiates the point.
* **Raw Sensor Logging:** Silently captures 50Hz Accelerometer and Gyroscope data in the background during a rally without freezing the UI or draining the battery.
* **Smart Storage:** Automatically batches sensor data into timestamped folders (`rally_YYYY-MM-DD_HH-mm-ss/sensor_data.csv`) for easy extraction.
* **Persistent History:** Saves previous rallies and durations locally on the watch so you can review your match later.
* **Modern Wear UI:** Utilizes pure Box layouts and Jetpack Compose to ensure timers and counters are perfectly aligned on circular screens (like the Pixel Watch 2).

## 🛠️ Tech Stack

* **Environment:** Android Studio
* **Language:** Kotlin
* **UI:** Jetpack Compose (Wear OS Material 3)
* **Concurrency:** Kotlin Coroutines (Dispatchers.IO for safe file writing)
* **Hardware APIs:** Android `SensorManager`, `Vibrator`

---

## 🚀 Getting Started (Development)

1. Clone this repository and open the project in **Android Studio**.
2. Enable **Developer Options** and **Wireless Debugging** on your Wear OS device (e.g., Pixel Watch 2).
3. Pair your watch to Android Studio over Wi-Fi.
4. Hit **Run** (`Shift + F10`) to build and install the APK directly to your wrist.

---

## 📊 Extracting Sensor Data

The

package com.example.rally.presentation

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

data class RallyRecord(
    val timestamp: String,
    val count: Int,
    val type: String,
    val duration: String,
    val csvFilePath: String? = null
)

class RallyViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val sharedPrefs = application.getSharedPreferences("rally_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var currentCount by mutableStateOf(0)
    var isRallyActive by mutableStateOf(false)
    var rallyType by mutableStateOf("") // "Serve" or "Receive"
    var elapsedTimeSeconds by mutableStateOf(0)
    val history = mutableStateListOf<RallyRecord>()

    private var timerJob: Job? = null
    private var loggingJob: Job? = null
    private var lastHitTime = 0L
    private val hitThreshold = 10.0f
    private val cooldownMs = 1200L

    // For file logging
    private val sensorDataQueue = ConcurrentLinkedQueue<String>()
    private var csvWriter: BufferedWriter? = null
    private var currentCsvPath: String? = null

    init {
        loadHistoryFromPrefs()
    }

    private fun loadHistoryFromPrefs() {
        val historyJson = sharedPrefs.getString("history", null)
        if (historyJson != null) {
            try {
                val type = object : TypeToken<List<RallyRecord>>() {}.type
                val savedHistory: List<RallyRecord> = gson.fromJson(historyJson, type)
                history.addAll(savedHistory)
            } catch (e: Exception) {
                android.util.Log.e("RallyViewModel", "Failed to load history", e)
            }
        }
    }

    private fun saveHistoryToPrefs() {
        val historyJson = gson.toJson(history.toList())
        sharedPrefs.edit().putString("history", historyJson).apply()
    }

    fun startRally(type: String) {
        rallyType = type
        currentCount = 0
        elapsedTimeSeconds = 0
        isRallyActive = true

        lastHitTime = System.currentTimeMillis() + 300L

        // Use a high-frequency registration for both sensors.
        // Live hit detection uses the gyro events as they come in.
        // The queue handles software-level batching for file writes.
        val samplingPeriodUs = 20000 // 50Hz
        sensorManager.registerListener(this, gyro, samplingPeriodUs)
        sensorManager.registerListener(this, accelerometer, samplingPeriodUs)

        vibrate(50)
        startTimer()
        startLogging()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive && isRallyActive) {
                elapsedTimeSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1000)
            }
        }
    }

    private fun startLogging() {
        loggingJob?.cancel()

        loggingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Generate a clean date-time string for the folder name
                val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                val folderName = "rally_${LocalDateTime.now().format(timeFormatter)}"

                // 2. Create the specific folder for this rally
                val baseDir = getApplication<Application>().getExternalFilesDir(null)
                val rallyDir = File(baseDir, folderName)
                if (!rallyDir.exists()) {
                    rallyDir.mkdirs()
                }

                // 3. Create the CSV inside that new folder
                val file = File(rallyDir, "sensor_data.csv")

                // Save the DIRECTORY path so we can delete the whole folder later if needed
                currentCsvPath = rallyDir.absolutePath

                // Initialize the writer
                csvWriter = BufferedWriter(FileWriter(file))
                csvWriter?.write("timestamp,sensor_type,x,y,z\n")
                csvWriter?.flush()

                android.util.Log.d("RallyApp", "SUCCESS: Folder and file created at ${file.absolutePath}")

                // The writing loop
                while (isActive && isRallyActive) {
                    delay(3000)
                    val writer = csvWriter ?: continue

                    val dataBatch = mutableListOf<String>()
                    while(sensorDataQueue.isNotEmpty()) {
                        sensorDataQueue.poll()?.let { dataBatch.add(it) }
                    }

                    if (dataBatch.isNotEmpty()) {
                        writer.write(dataBatch.joinToString(""))
                        writer.flush()
                        android.util.Log.d("RallyApp", "Wrote ${dataBatch.size} lines to CSV.")
                    }
                }
            } catch (e: Exception) {
                // Ignore the normal cancellation exception when we stop the rally
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("RallyApp", "CRITICAL FILE ERROR: ${e.message}", e)
                }
            }
        }
    }

    fun stopRally() {
        if (isRallyActive) {
            isRallyActive = false
            timerJob?.cancel()
            loggingJob?.cancel()

            sensorManager.unregisterListener(this)
            vibrate(100)

            if (currentCount > 0) {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val record = RallyRecord(
                    timestamp = LocalDateTime.now().format(formatter),
                    count = currentCount,
                    type = rallyType,
                    duration = formatDuration(elapsedTimeSeconds),
                    csvFilePath = currentCsvPath
                )
                history.add(0, record)
                saveHistoryToPrefs()
            }

            // Final flush and close with a try/catch
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val writer = csvWriter
                    val dataBatch = mutableListOf<String>()
                    while(sensorDataQueue.isNotEmpty()) {
                        sensorDataQueue.poll()?.let { dataBatch.add(it) }
                    }
                    if (dataBatch.isNotEmpty()) {
                        writer?.write(dataBatch.joinToString(""))
                    }
                    writer?.flush()
                    writer?.close()
                    csvWriter = null

                    android.util.Log.d("RallyApp", "SUCCESS: Closed and saved final CSV.")
                } catch (e: Exception) {
                    android.util.Log.e("RallyApp", "Error closing file: ${e.message}", e)
                }
            }
        }
    }

    fun deleteRecord(record: RallyRecord) {
        history.remove(record)
        saveHistoryToPrefs()
        record.csvFilePath?.let {
            viewModelScope.launch(Dispatchers.IO) {
                // deleteRecursively() wipes the folder and the CSV inside it
                File(it).deleteRecursively()
            }
        }
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    private fun vibrate(duration: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRallyActive) return

        val sensorType = event.sensor.type

        // 1. Live hit detection logic (remains unchanged)
        if (sensorType == Sensor.TYPE_GYROSCOPE) {
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
            )
            val currentTime = System.currentTimeMillis()

            if (magnitude > hitThreshold && currentTime - lastHitTime > cooldownMs) {
                if (rallyType == "Serve") {
                    if (currentCount == 0) currentCount = 1 else currentCount += 2
                } else { // Receive
                    if (currentCount == 0) currentCount = 2 else currentCount += 2
                }
                lastHitTime = currentTime
                vibrate(70)
            }
        }

        // 2. Queue data for logging
        val csvLine = "${event.timestamp},${sensorType},${event.values[0]},${event.values[1]},${event.values[2]}\n"
        sensorDataQueue.add(csvLine)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
        timerJob?.cancel()
        loggingJob?.cancel()
        csvWriter?.close()
    }
}
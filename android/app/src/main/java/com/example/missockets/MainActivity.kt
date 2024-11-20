package com.example.missockets

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import com.example.missockets.network.RetrofitClient
import com.example.missockets.network.Script
import com.example.missockets.network.ScriptStatus
import com.example.missockets.network.SocketManager
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    private val socket = SocketManager.getSocket()
    private val scriptLogs = mutableMapOf<String, MutableList<String>>() // Mapa para almacenar logs de scripts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fetchScripts()
        setupSocketListeners()
        socket.connect()
    }

    private fun fetchScripts() {
        RetrofitClient.apiService.getScripts().enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful) {
                    val scriptNames = response.body()
                    scriptNames?.let {
                        val scripts = it.map { name -> Script(name) }
                        displayScripts(scripts)
                    }
                } else {
                    Log.e("Error", "Error al obtener los scripts")
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                Log.e("Error", "Fallo en la llamada: ${t.message}")
            }
        })
    }

    private fun displayScripts(scripts: List<Script>) {
        val scriptsContainer = findViewById<LinearLayout>(R.id.scriptsContainer)
        scriptsContainer.removeAllViews()

        for (script in scripts) {
            val scriptLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }

            val scriptNameTextView = TextView(this).apply {
                text = script.name
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val startStopButton = Button(this).apply {
                text = "Iniciar"
                setOnClickListener {
                    val action = if (text == "Iniciar") "start" else "stop"
                    controlScript(script.name, action)
                    text = if (action == "start") "Detener" else "Iniciar"
                }
            }

            val logsStdoutButton = Button(this).apply {
                text = "Logs Salida"
                setOnClickListener {
                    val logs = scriptLogs[script.name]?.filter { it.startsWith("STDOUT") }
                        ?.joinToString("\n") ?: "Sin logs de salida"
                    showLogsActivity("Logs de salida - ${script.name}", script.name, logs)
                }
            }

            val logsStderrButton = Button(this).apply {
                text = "Logs Error"
                setOnClickListener {
                    val logs = scriptLogs[script.name]?.filter { it.startsWith("STDERR") }
                        ?.joinToString("\n") ?: "Sin logs de error"
                    showErrorLogsActivity("Logs de error - ${script.name}", script.name, logs)
                }
            }

            getScriptStatus(script.name) { status ->
                startStopButton.text = if (status == "running") "Detener" else "Iniciar"
            }

            scriptLayout.addView(scriptNameTextView)
            scriptLayout.addView(startStopButton)
            scriptLayout.addView(logsStdoutButton)
            scriptLayout.addView(logsStderrButton)

            scriptsContainer.addView(scriptLayout)
        }
    }

    private fun showErrorLogsActivity(title: String, scriptName: String, logs: String) {
        val intent = Intent(this, ErrorLogsActivity::class.java)
        intent.putExtra("logs", logs)
        intent.putExtra("scriptName", scriptName)
        startActivity(intent)
    }

    private fun controlScript(scriptName: String, action: String) {
        RetrofitClient.apiService.controlScript(scriptName, action).enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful) {
                    val message = response.body()?.get("message") ?: "Acción realizada"
                    Toast.makeText(this@MainActivity, "$message en $scriptName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Error en $action de $scriptName", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error de red: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getScriptStatus(scriptName: String, callback: (String) -> Unit) {
        RetrofitClient.apiService.getScriptStatus(scriptName).enqueue(object : Callback<ScriptStatus> {
            override fun onResponse(call: Call<ScriptStatus>, response: Response<ScriptStatus>) {
                if (response.isSuccessful) {
                    val scriptStatus = response.body()
                    val status = scriptStatus?.status ?: "stopped"
                    callback(status)
                } else {
                    Toast.makeText(this@MainActivity, "Error al obtener estado de $scriptName", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ScriptStatus>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error de red: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showLogsActivity(title: String, scriptName: String, logs: String) {
        val intent = Intent(this, LogsActivity::class.java)
        intent.putExtra("logs", logs)
        intent.putExtra("scriptName", scriptName)
        startActivity(intent)
    }

    private fun setupSocketListeners() {
        socket.on("scripts_update", onScripts)
        socket.on("log_stdout", onLogStdout)
        socket.on("log_stderr", onLogStderr)
    }

    private val onScripts = Emitter.Listener { args ->
        val jsonArray = args[0] as? JSONArray
        jsonArray?.let {
            val scriptNames = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                scriptNames.add(jsonArray.getString(i))
            }

            val scripts = scriptNames.map { name -> Script(name) }
            runOnUiThread {
                displayScripts(scripts)
            }
        }
    }

    private val onLogStdout = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val scriptName = it.getString("scriptName")
            val log = it.getString("log")
            runOnUiThread {
                scriptLogs.getOrPut(scriptName) { mutableListOf() }.add("STDOUT: $log")
            }
        }
    }

    private val onLogStderr = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val scriptName = it.getString("scriptName")
            val log = it.getString("errorLog")
            runOnUiThread {
                scriptLogs.getOrPut(scriptName) { mutableListOf() }.add("STDERR: $log")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.disconnect()
    }
}

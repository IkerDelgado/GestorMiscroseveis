package com.example.missockets

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import com.example.missockets.network.RetrofitClient
import com.example.missockets.network.ApiService
import com.example.missockets.network.SocketManager
import io.socket.emitter.Emitter
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LogsActivity : ComponentActivity() {
    private val socket = SocketManager.getSocket()
    private lateinit var logsTextView: TextView
    private lateinit var titleTextView: TextView
    private var scriptName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        titleTextView = findViewById(R.id.titulo)
        logsTextView = findViewById(R.id.logsTextView)
        scriptName = intent.getStringExtra("scriptName")

        // Muestra el título con el nombre del script
        titleTextView.text = "Logs de $scriptName"

        // Muestra los logs iniciales recibidos desde el Intent
        val initialLogs = intent.getStringExtra("logs") ?: "Sin logs disponibles"
        logsTextView.text = initialLogs

        // Llama a la API para obtener los logs adicionales
        fetchLogs()

        // Configura los listeners del socket solo para los logs de salida
        setupSocketListeners()

        // Configura el botón para regresar
        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    // Función para obtener los logs adicionales usando Retrofit
    private fun fetchLogs() {
        RetrofitClient.apiService.getScriptLogsActividad(scriptName ?: "").enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                if (response.isSuccessful) {
                    // Si la respuesta es exitosa, se obtienen los logs
                    val logs = response.body()?.get("logs") ?: "No se encontraron logs"
                    logsTextView.text = logs
                } else {
                    // Si la respuesta no es exitosa, mostrar error
                    Log.e("LogsActivity", "Error al obtener los logs desde la API")
                    logsTextView.text = "Error al obtener los logs desde la API."
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                // Si hay un error de red
                Log.e("LogsActivity", "Error en la llamada: ${t.message}")
                logsTextView.text = "Error de red al obtener los logs."
            }
        })
    }

    // Configura los listeners del socket para recibir logs en tiempo real
    private fun setupSocketListeners() {
        socket.on("log_stdout", onLogStdout)  // Escuchar los logs stdout

        if (!socket.connected()) {
            socket.connect()
        }
    }

    // Listener para logs stdout
    private val onLogStdout = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val receivedScriptName = it.getString("scriptName")
            val log = it.getString("log")
            if (receivedScriptName == scriptName) {
                runOnUiThread {
                    logsTextView.append("\nSTDOUT: $log")  // Añadimos los logs de salida
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.off("log_stdout", onLogStdout)  // Desconectamos el listener de logs stdout
        socket.disconnect()  // Desconectamos el socket cuando se destruye la actividad
    }
}

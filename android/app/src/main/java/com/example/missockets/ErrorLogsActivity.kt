package com.example.missockets

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.missockets.network.RetrofitClient
import com.example.missockets.network.ApiService
import com.example.missockets.R
import com.example.missockets.network.SocketManager
import io.socket.emitter.Emitter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import org.json.JSONObject

class ErrorLogsActivity : ComponentActivity() {
    private val socket = SocketManager.getSocket()
    private lateinit var logsTextView: TextView
    private lateinit var titleTextView: TextView
    private var scriptName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error_logs)

        titleTextView = findViewById(R.id.titleTextView)
        logsTextView = findViewById(R.id.logsTextView)

        // Obtiene el nombre del script
        scriptName = intent.getStringExtra("scriptName")

        // Muestra el título con el nombre del script
        titleTextView.text = "Logs de Error para: $scriptName"

        // Obtiene los logs de error desde la API cuando la actividad se inicia
        fetchErrorLogs()

        // Configura el botón de regresar
        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Configura el socket para escuchar solo logs de error
        setupSocketListeners()
    }

    private fun fetchErrorLogs() {
        scriptName?.let {
            RetrofitClient.apiService.getScriptErrorLogs(it).enqueue(object : Callback<Map<String, String>> {
                override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                    if (response.isSuccessful) {
                        // Si la respuesta es exitosa, se obtienen los logs de error
                        val errorLogs = response.body()?.get("logs") ?: "No se encontraron logs de error."
                        logsTextView.text = errorLogs
                    } else {
                        // Si la respuesta no es exitosa, mostramos un mensaje de error
                        Toast.makeText(applicationContext, "Error al obtener los logs de error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                    // Si hay un error de red
                    Toast.makeText(applicationContext, "Error de red al obtener los logs de error", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupSocketListeners() {
        socket.on("log_stderr", onLogStderr)  // Solo escuchamos log_stderr

        if (!socket.connected()) {
            socket.connect()
        }
    }

    // Listener para los logs de error (STDERR)
    private val onLogStderr = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val receivedScriptName = it.getString("scriptName")
            val log = it.getString("errorLog")

            if (receivedScriptName == scriptName) {
                runOnUiThread {
                    logsTextView.append("\nSTDERR: $log")  // Añadimos los logs de error
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.off("log_stderr", onLogStderr)  // Desconectamos el listener
    }
}

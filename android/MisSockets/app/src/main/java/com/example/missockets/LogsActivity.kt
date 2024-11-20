package com.example.missockets

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.missockets.MainActivity
import com.example.missockets.R
import com.example.missockets.network.SocketManager
import io.socket.emitter.Emitter
import org.json.JSONObject

class LogsActivity : ComponentActivity() {

    private val socket = SocketManager.getSocket() // Obtén la instancia del socket
    private lateinit var logsTextView: TextView
    private var scriptName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        // Inicializa el TextView para mostrar los logs
        logsTextView = findViewById(R.id.logsTextView)

        // Obtén el nombre del script desde el Intent
        scriptName = intent.getStringExtra("scriptName")

        // Muestra los logs iniciales (si los hay)
        val initialLogs = intent.getStringExtra("logs") ?: "Sin logs disponibles"
        logsTextView.text = initialLogs

        // Configura el botón de regresar
        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Regresa al MainActivity eliminando las actividades anteriores
            startActivity(intent)
        }

        // Configura el socket para escuchar logs en tiempo real
        setupSocketListeners()
    }

    private fun setupSocketListeners() {
        // Escucha logs stdout
        socket.on("log_stdout", onLogStdout)

        // Escucha logs stderr (opcional, si también quieres mostrarlos aquí)
        socket.on("log_stderr", onLogStderr)

        // Conéctate al socket si no está conectado
        if (!socket.connected()) {
            socket.connect()
        }
    }

    // Escucha los logs de stdout
    private val onLogStdout = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val receivedScriptName = it.getString("scriptName")
            val log = it.getString("log")

            // Asegúrate de mostrar solo los logs del script correspondiente
            if (receivedScriptName == scriptName) {
                runOnUiThread {
                    // Agrega el nuevo log de salida (STDOUT)
                    logsTextView.append("\nSTDOUT: $log")
                }
            }
        }
    }

    // Escucha los logs de stderr
    private val onLogStderr = Emitter.Listener { args ->
        val data = args[0] as? JSONObject
        data?.let {
            val receivedScriptName = it.getString("scriptName")
            val log = it.getString("log")

            // Asegúrate de mostrar solo los logs del script correspondiente
            if (receivedScriptName == scriptName) {
                runOnUiThread {
                    // Agrega el nuevo log de error (STDERR)
                    logsTextView.append("\nSTDERR: $log")
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Desconecta el listener del socket
        socket.off("log_stdout", onLogStdout)
        socket.off("log_stderr", onLogStderr)
    }
}

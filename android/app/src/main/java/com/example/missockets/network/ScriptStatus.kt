package com.example.missockets.network

data class ScriptStatus(
    val scriptName: String,
    val status: String,
    val stdoutLogs: List<String>,
    val stderrLogs: List<String>
)

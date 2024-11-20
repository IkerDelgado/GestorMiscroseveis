package com.example.missockets.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    // Obtener la lista de scripts disponibles
    @GET("/scripts")
    fun getScripts(): Call<List<String>>

    // Iniciar o detener un script
    @POST("/scripts/{scriptName}/{action}")
    fun controlScript(
        @Path("scriptName") scriptName: String,
        @Path("action") action: String
    ): Call<Map<String, String>> // La respuesta contiene el mensaje y el scriptName

    // Consultar el estado y los logs de un script específico
    @GET("/scripts/{scriptName}/status")
    fun getScriptStatus(
        @Path("scriptName") scriptName: String
    ): Call<ScriptStatus>

    // Obtener los logs guardados de un script específico
    @GET("/scripts/{scriptName}/logs")
    fun getScriptLogs(
        @Path("scriptName") scriptName: String
    ): Call<Map<String, String>> // Retorna los logs en un mapa con claves "stdout" y "stderr"
}

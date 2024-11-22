package com.example.missockets.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @GET("/scripts")
    fun getScripts(): Call<List<String>>

    @POST("/scripts/{scriptName}/{action}")
    fun controlScript(
        @Path("scriptName") scriptName: String,
        @Path("action") action: String
    ): Call<Map<String, String>>

    @GET("/scripts/{scriptName}/status")
    fun getScriptStatus(
        @Path("scriptName") scriptName: String
    ): Call<ScriptStatus>

    @GET("/scripts/{scriptName}/logsActividad")
    fun getScriptLogsActividad(
        @Path("scriptName") scriptName: String
    ): Call<Map<String, String>>
    @GET("/scripts/{scriptName}/logsError")
    fun getScriptErrorLogs(
        @Path("scriptName") scriptName: String
    ): Call<Map<String, String>>

}

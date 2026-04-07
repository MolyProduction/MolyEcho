package com.module.notelycompose.platform

import com.module.notelycompose.modelDownloader.ModelFormat

expect class Transcriber {
    // Incremented at the start of every initialize() call. ViewModels capture this value
    // right after initialize() returns and pass it back to finish(), so a stale finish()
    // from a previous ViewModel session cannot release a model that a newer session owns.
    val currentSessionToken: Long
    fun doesModelExists(modelFileName: String ): Boolean
    suspend fun initialize(modelFileName: String, modelFormat: ModelFormat = ModelFormat.GGML)
    suspend fun finish(sessionToken: Long)
    suspend fun stop()
    suspend fun start(
        filePath:String, language:String,
        onProgress : (Int) -> Unit,
        onNewSegment : (Long, Long,String) -> Unit,
        onComplete : () -> Unit,
        onError : () -> Unit
    )
    fun hasRecordingPermission(): Boolean
    suspend fun requestRecordingPermission():Boolean
    fun isValidModel(modelFileName: String): Boolean
    fun deleteModel(modelFileName: String): Boolean
    fun getModelFileSizeBytes(modelFileName: String): Long
    fun getAudioDurationSeconds(filePath: String): Int
}
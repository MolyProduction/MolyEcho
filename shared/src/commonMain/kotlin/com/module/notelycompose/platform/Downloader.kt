package com.module.notelycompose.platform

expect class Downloader {
    suspend fun startDownload(url: String, fileName: String)
    suspend fun hasRunningDownload(): Boolean
    suspend fun cancelDownload()
    suspend fun trackDownloadProgress(
        fileName: String,
        onProgressUpdated: (progress: Int, downloadedMB: String, totalMB: String) -> Unit,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onFailed: (String) -> Unit
    )

    /**
     * Posts the "model downloaded" notification. Called by the ViewModel once the
     * complete model is ready (single file, or ALL files of a multi-file model) —
     * not per file, so multi-file models don't notify prematurely.
     */
    fun notifyDownloadComplete()

    /** True if the active network is metered (mobile data) — used to warn before 1+ GB downloads. */
    fun isNetworkMetered(): Boolean
}

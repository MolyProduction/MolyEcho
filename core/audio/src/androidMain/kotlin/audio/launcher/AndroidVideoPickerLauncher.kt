package audio.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//class AndroidVideoPickerLauncher(
//    private val activity: ComponentActivity
//) {
//    fun launch(onResult: (Uri?) -> Unit) {
//        val launcher: ActivityResultLauncher<Intent> = activity.activityResultRegistry.register(
//            "pick_video",
//            ActivityResultContracts.StartActivityForResult()
//        ) { result ->
//            val uri = result.data?.data
//            if (result.resultCode == Activity.RESULT_OK && uri != null) {
//                // Add delay to allow WM lock to be released
//                activity.lifecycleScope.launch {
//                    delay(3000) // Small delay to let UI settle
//                    onResult(uri)
//                }
//            } else {
//                onResult(null)
//            }
//        }
//
//        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
//            type = "video/*"
//            addCategory(Intent.CATEGORY_OPENABLE)
//        }
//
//        launcher.launch(Intent.createChooser(intent, "Select Video File"))
//    }
//}
class AndroidVideoPickerLauncher(
    private val activity: ComponentActivity
) {
    private val launcher: ActivityResultLauncher<String>

    init {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            currentCallback?.invoke(uri)
            currentCallback = null
        }
    }

    private var currentCallback: ((Uri?) -> Unit)? = null

    fun launch(onResult: (Uri?) -> Unit) {
        currentCallback = onResult
        launcher.launch("video/*")
    }
}
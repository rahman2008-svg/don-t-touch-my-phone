package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun FrontCameraCapture(
    onPhotoCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(cameraProviderFuture) {
        try {
            // Wait a moment before capturing to allow the camera sensor to adjust exposure/focus
            delay(1000)

            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture
            )

            val photosDir = File(context.filesDir, "intruders").apply {
                if (!exists()) mkdirs()
            }
            val photoFile = File(photosDir, "intruder_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d("FrontCameraCapture", "Photo captured successfully: ${photoFile.absolutePath}")
                        onPhotoCaptured(photoFile.absolutePath)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("FrontCameraCapture", "Photo capture failed: ${exception.message}", exception)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("FrontCameraCapture", "Error configuring CameraX: ${e.message}", e)
        }
    }
}

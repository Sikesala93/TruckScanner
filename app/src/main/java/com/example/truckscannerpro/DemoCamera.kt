package com.example.truckscannerpro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import java.util.*

class DemoCamera(
    private val cameraHandler: Handler, private val textureView: TextureView? = null,
    private val onImageAvailableListener: ImageReader.OnImageAvailableListener,
) {

    private var cameraDevice: CameraDevice? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null

    private lateinit var imageReader: ImageReader
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraManager: CameraManager

    fun openCamera(context: Context) {
        Log.v("vlogger", "openCamera")
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        var camIds: Array<String> = emptyArray()

        camIds = cameraManager.cameraIdList

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        cameraManager.openCamera(camIds[0], stateCallback, cameraHandler)

        var cameraCharacteristic = cameraManager.getCameraCharacteristics(camIds[0])

        val camCharMap =
            cameraCharacteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        if (camCharMap == null) {
            Log.v("vlogger", "map == null")
        }
        val outputSizes = camCharMap?.getOutputSizes(ImageFormat.JPEG)?.asList()
        outputSizes?.forEach {
            Log.v("vlogger", "outputSizes $it}")
        }
        val largestRes = Collections.max(outputSizes, CompareByArea())
        Log.v("vlogger", "largest: ${largestRes.height}, ${largestRes.width}")
        imageReader =
            ImageReader.newInstance(432,320 , ImageFormat.JPEG, 1) //x largestRes.widthlargestRes.height

        imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler)
    }

    fun takePhoto() {
        Log.v("vlogger", "takePhoto")
        if (cameraDevice == null || imageReader == null) {
            Log.v("vlogger", "camera device or imagereader null")
        }
        cameraCaptureSession.stopRepeating()
        cameraCaptureSession.abortCaptures()

        try {
            cameraDevice!!.createCaptureSession(
                Collections.singletonList(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        if (cameraDevice == null) {
                            return
                        }
                        cameraCaptureSession = p0
                        doImageCapture()

                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {

                    }
                }, null
            )

        } catch (ex: CameraAccessException) {
            Log.v("vlogger", "Camera access exeption")
        }
    }

    private fun doImageCapture() {
        Log.v("vlogger", "doimagecapture")
        try {
            var captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            cameraCaptureSession.capture(captureBuilder.build(), captureCallback, null)
        } catch (ex: CameraAccessException) {
            Log.v("CameraApp", "doimagecapture, Camera access exeption")
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.v("vlogger", "stateCallback -> onOpened")
            cameraDevice = camera
            createPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.v("vlogger", "stateCallback -> onDisconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.v("vlogger", "stateCallback -> onError")
        }
    }

    private fun createPreview() {
        if (cameraDevice == null || textureView == null) {
            return
        }
        val texture = textureView.surfaceTexture
        texture!!.setDefaultBufferSize(320, 280)

        val surface = Surface(texture)

        cameraDevice!!.createCaptureSession(
            Collections.singletonList(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) {
                        return
                    }
                    cameraCaptureSession = session

                    previewRequestBuilder =
                        cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewRequestBuilder!!.addTarget(surface)

                    previewRequest = previewRequestBuilder!!.build()
                    cameraCaptureSession!!.setRepeatingRequest(
                        previewRequest!!,
                        captureCallback,
                        cameraHandler
                    )
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                }
            }, null
        )
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
    }

    fun shutDownCamera() {
        Log.v("vlogger", "shutDownCamera")
        imageReader?.close()
        cameraCaptureSession?.close()
        cameraDevice?.close()
    }
}
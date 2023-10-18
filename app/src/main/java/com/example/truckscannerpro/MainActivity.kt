package com.example.truckscannerpro

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.rotationMatrix
import com.example.truckscannerpro.databinding.ActivityMainBinding
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var firebaseStorage: FirebaseStorage = FirebaseStorage.getInstance()
    var storageRef: StorageReference = firebaseStorage.reference
    var gate: Boolean = true
    var gate2: Boolean = false

    //------------------BLE Gatt------------------------------------------------------------
    // Ottaa käyttöön vasta ku tarvitaan
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanCallback: ScanCallback by lazy {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                Log.v("vlogger", "ScanResult")
                super.onScanResult(callbackType, result)

                var bluetoothDevice = result?.device

                if (bluetoothDevice != null) {
                    connectToDevice(bluetoothDevice)
                }

                Log.v("vlogger", "onScanResult ${bluetoothDevice!!.name}")
            }
        }
    }

    private val bleGattCallback: BluetoothGattCallback by lazy {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                Log.v("vlogger", "ConnectionstateChange")

                if (newState == BluetoothProfile.STATE_CONNECTED && !gate2) {
                    gate2 = true
                    gatt?.discoverServices()
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED && gate2){
                    gate2 = false
                }


            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                Log.v("vlogger", "ServiceDiscovered")

                val service = gatt?.getService(SERVICE_UUID)
                val characteristics = service?.getCharacteristic(CHARACTERISTIC_UUID)

                gatt?.setCharacteristicNotification(characteristics, true)

            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                Log.v("vlogger", "${characteristic?.getIntValue(FORMAT_UINT16, 0)}")
                if (gate) {
                    gate = false

                    runOnUiThread {
                        demoCamera.takePhoto()
                    }

                }
            }
        }
    }

    // Nämä otettu ESP:n koodista
    val SERVICE_UUID = UUID.fromString("877a5e9a-74ae-4403-bfef-dc4c3fe2179f")
    val CHARACTERISTIC_UUID = UUID.fromString("040983c3-bbcd-4311-b270-4e530359ad27")

    fun connectToDevice(bluetoothDevice: BluetoothDevice) {
        bluetoothDevice.connectGatt(
            this,
            false,
            bleGattCallback,
            BluetoothDevice.TRANSPORT_LE
        ) // Raspia varten neljänneksi TRANSPORT_LE
    }

    val btRequestActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                binding.btStatusText.text = "BT Päällä"
            }
        }


    fun startBLEScan() {
        Log.v("vlogger", "startBLEScan()")
        var filter = ScanFilter.Builder().setDeviceName("R5ESP").build()
        var filters: MutableList<ScanFilter> = mutableListOf()
        filters.add(filter)
        Log.v("vlogger", "${filters}")
        var setting = ScanSettings.Builder().build()
        Log.v("vlogger", "${setting}")
        bluetoothAdapter.bluetoothLeScanner.startScan(filters, setting, bleScanCallback)
    }


    fun openBtActivity() {
        var intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        btRequestActivity.launch(intent)
    }

    //--------------------------------------------------------------------------------------BLE Gatt
    //-----------------------Camera2------------------------------------
    private val REQUIRED_PERMISSIONS = arrayOf("android.permission.CAMERA")
    private val REQUEST_CODE_PERMISSION = 1001

    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var demoCamera: DemoCamera
    private lateinit var texture: TextureView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        if (bluetoothAdapter.isEnabled) {
            binding.btStatusText.text = "BT Päällä"
        } else {
            binding.btStatusText.text = "BT Ei päällä"
            openBtActivity()
        }
        startBLEScan()
        binding.startButton.setOnClickListener {
            startBLEScan()
        }

        texture = binding.texture

        if (allPermissionsGranted()) {
            Log.v("vlogger", "Permission OK!")

        } else {
            Log.v("vlogger", "Ask permission!")
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSION)
        }
        button = binding.takePictureButton
        button.setOnClickListener {
            demoCamera.takePhoto()
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (allPermissionsGranted()) {
                Log.v("vlogger", "Permission OK!")
            } else {
                Toast.makeText(this, "No permission", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onPause() {
        Log.v("vlogger", "onPause")
        cameraThread.quitSafely()
        demoCamera.shutDownCamera()
        try {
            cameraThread.join()
        } catch (e: InterruptedException) {
            Log.e("vlogger", e.toString())
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.v("vlogger", "onResume")
        cameraThread = HandlerThread("CameraThread")
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)

        demoCamera = DemoCamera(cameraHandler, texture, onImageAvailableListener)

        if (texture.isAvailable) {
            startReview()
        } else {
            texture.surfaceTextureListener = surfaceTextureListener
        }


    }

    private fun startReview() {
        demoCamera.openCamera(this)
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {

            startReview()
        }

        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Log.v("vlogger", "onimageavalablelistener")
        val image = reader.acquireLatestImage()
        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)
        image.close()
        pictureReady(imageBytes)
    }

    private fun pictureReady(imageBytes: ByteArray) {
        val matrix: Matrix = rotationMatrix(0F)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        matrix.postRotate(270F)
        var rotatedImage: Bitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val imageTitle: String =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH).format(Date()) + ".jpg"
        val uri = bitmapToFile(rotatedImage, imageTitle)

        runOnUiThread {
            val imageView = findViewById<ImageView>(R.id.imageReview)
            imageView.setImageURI(uri)
        }
        demoCamera.shutDownCamera()
        startReview()

        val imagesRef: StorageReference = storageRef.child(imageTitle)
        imagesRef.putFile(uri).addOnSuccessListener {
            Log.v("vlogger", "Success")
            gate = true
        }.addOnFailureListener {
            Log.v("vlogger", "Fail")
            gate = true
        }

    }

    private fun bitmapToFile(bitmap: Bitmap, imageTitle: String): Uri {
        var file = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            imageTitle,
            "image of imageTitle"
        )
        return Uri.parse(file)
    }
}
package co.jatri.paymentscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import co.jatri.paymentscanner.databinding.ActivityScannerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class ScannerActivity : AppCompatActivity() {

    //Camera
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera

    //Barcode
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val barcodeScanner = BarcodeScanning.getClient(options)

    private lateinit var binding: ActivityScannerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleFlashVisibility()
        checkPermissionAndOpenCamera()
    }

    private fun handleFlashVisibility(){
        binding.flashIv.setOnClickListener {
            if (this::camera.isInitialized){
                if (camera.cameraInfo.torchState.value == TorchState.ON) {
                    setFlashOffIcon()
                    camera.cameraControl.enableTorch(false)
                } else {
                    setFlashOnIcon()
                    camera.cameraControl.enableTorch(true)
                }
            }
        }
    }

    private fun setFlashOffIcon() {
        binding.flashIv.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_flash_off,
                null
            )
        )
    }

    private fun setFlashOnIcon() {
        binding.flashIv.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_flash_on,
                null
            )
        )
    }

    private fun checkPermissionAndOpenCamera(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                AppConstants.CAMERA_ACTIVITY_CODE)
        } else openCamera()
    }

    private fun openCamera(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @SuppressLint("RestrictedApi")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setMaxResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            processImageProxy(imageProxy)
        }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview) //your preview
            .addUseCase(imageAnalysis) //if you are using imageAnalysis
            .build()
        try {
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                useCaseGroup
            )
            camera.cameraControl.enableTorch(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        // This scans the entire screen for barcodes
        imageProxy.image?.let { image ->
            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        if (barcode.format == Barcode.FORMAT_QR_CODE) {
                            camera.cameraControl.enableTorch(false)
                            cameraProvider.unbindAll()

                            handleScannerSuccess(barcode.rawValue?.trim())
                        }
                    }
                }
                .addOnFailureListener { Toast.makeText(this, "Scan Failed", Toast.LENGTH_SHORT).show() }
                .addOnCompleteListener {
                    image.close()
                    imageProxy.close()
                }
        }
    }

    private fun handleScannerSuccess(value: String?) {
        Toast.makeText(this,"Scan Success", Toast.LENGTH_SHORT).show()
        value?.let {
            Toast.makeText(this,"Scan Value: $it", Toast.LENGTH_SHORT).show()
        }
        openCamera()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AppConstants.CAMERA_ACTIVITY_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera()
                else {
                    Toast.makeText(this,"Check Camera Permission", Toast.LENGTH_SHORT).show()
                    checkPermissionAndOpenCamera()
                }
                return
            }
        }
    }

}
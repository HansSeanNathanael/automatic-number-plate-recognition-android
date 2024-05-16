package com.example.platenumberrecognition;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Size;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewViewCamera;
    private OverlayView overlayView;

    private PlateDetectionHelper plateDetectionHelper;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        this.requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            String message = isGranted ? "Camera permission granted" : "Camera permission rejected";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        this.requestPermissionLauncher.launch(Manifest.permission.CAMERA);

        this.previewViewCamera = (PreviewView) this.findViewById(R.id.previewViewCamera);
        this.overlayView = (OverlayView) this.findViewById(R.id.overlayView);

        try {
            this.plateDetectionHelper = new PlateDetectionHelper(this);
            this.cameraExecutor = Executors.newSingleThreadExecutor();
            this.startCamera();
        }
        catch (Exception e) {
            Toast.makeText(this, "Failed loading model", Toast.LENGTH_SHORT).show();
        }

    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {

            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(this.previewViewCamera.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                imageAnalysis.setAnalyzer(this.cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Nullable
                    @Override
                    public Size getDefaultTargetResolution() {
                        return ImageAnalysis.Analyzer.super.getDefaultTargetResolution();
                    }

                    @Override
                    public int getTargetCoordinateSystem() {
                        return ImageAnalysis.Analyzer.super.getTargetCoordinateSystem();
                    }

                    @Override
                    public void updateTransform(@Nullable Matrix matrix) {
                        ImageAnalysis.Analyzer.super.updateTransform(matrix);
                    }

                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {

                        Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888 );
                        bitmap.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());

                        int rotation = imageProxy.getImageInfo().getRotationDegrees();
                        imageProxy.close();

                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotation);

                        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        bitmap.recycle();

                        final List<BoundingBox> objects = MainActivity.this.plateDetectionHelper.process(rotatedBitmap);
                        rotatedBitmap.recycle();

                        MainActivity.this.overlayView.setBoundingBox(objects);
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalysis,
                    preview
                );

            } catch (Exception ignored) {
                Toast.makeText(this, "Gagal memunculkan kamera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cameraExecutor.shutdown();
    }
}
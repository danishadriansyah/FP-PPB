package com.example.roboflowscanner;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.MediaStore;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RoboflowScanner";
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int GALLERY_REQUEST_CODE = 102;

    // Konfigurasi API Roboflow
    private static final String ROBOFLOW_BASE_URL = "https://serverless.roboflow.com/";
    private static final String MODEL_ID = "avds-ppb-bismillah/4";
    private static final String API_KEY = "HPiZPq2rG8LF0iyXjYq1";
    private static final int TIMEOUT_SECONDS = 30;
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 2MB
    private static final int TARGET_IMAGE_QUALITY = 85;
    private static final int MAX_IMAGE_DIMENSION = 1280;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;

    // Komponen UI
    private PreviewView previewView;
    private ImageButton captureButton;
    private ImageButton galleryButton;
    private ImageButton flashButton;
    private TextView resultTextView;
    private ImageView resultImageView;
    private CardView resultCardView;
    private View progressBar;
    private ImageView appLogo;

    // Komponen Kamera
    private ImageCapture imageCapture;
    private Executor executor;
    private RoboflowApi roboflowApi;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService backgroundExecutor;
    private boolean isFlashOn = false;
    private ImageButton retakeButton;
    private static final int STORAGE_PERMISSION_CODE = 103;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeRetrofit();

        executor = ContextCompat.getMainExecutor(this);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.captureButton);
        retakeButton = findViewById(R.id.retakeButton);
        galleryButton = findViewById(R.id.galleryButton);
        flashButton = findViewById(R.id.flashButton);
        resultTextView = findViewById(R.id.resultTextView);
        resultImageView = findViewById(R.id.resultImageView);
        resultCardView = findViewById(R.id.resultCardView);
        progressBar = findViewById(R.id.progressBar);
        appLogo = findViewById(R.id.appLogo);

        resultCardView.setVisibility(View.GONE);
        retakeButton.setVisibility(View.GONE);

        captureButton.setOnClickListener(v -> {
            if (resultCardView.getVisibility() == View.VISIBLE) {
                toggleCameraMode(true);
            } else {
                takePicture();
            }
        });

        retakeButton.setOnClickListener(v -> {
            toggleCameraMode(true);
            showToast("Mengambil ulang foto...");
        });

        flashButton.setOnClickListener(v -> toggleFlash());

        galleryButton.setOnClickListener(v -> openGallery());

    }

    private void toggleCameraMode(boolean showCamera) {
        runOnUiThread(() -> {
            if (showCamera) {
                // Mode kamera
                previewView.setVisibility(View.VISIBLE);
                resultCardView.setVisibility(View.GONE);
                retakeButton.setVisibility(View.GONE);
                retakeButton.animate().alpha(0f).setDuration(200).start();
                captureButton.setImageResource(R.drawable.ic_capture);
            } else {
                // Mode hasil
                previewView.setVisibility(View.GONE);
                resultCardView.setVisibility(View.VISIBLE);
                retakeButton.setVisibility(View.VISIBLE);
                retakeButton.setAlpha(0f);
                retakeButton.animate().alpha(1f).setDuration(300).start();
                captureButton.setImageResource(R.drawable.ic_retake);
            }
        });
    }

    private void toggleFlash() {
        try {
            isFlashOn = !isFlashOn;
            flashButton.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);

            if (cameraProvider != null) {
                bindCameraUseCases(cameraProvider);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling flash", e);
            showToast("Gagal mengubah flash");
        }
    }

    private void openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) menggunakan permission baru
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                launchGalleryIntent();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        STORAGE_PERMISSION_CODE
                );
            }
        } else {
            // Android 6.0-12 (API 23-32)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                launchGalleryIntent();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_CODE
                );
            }
        }
    }
    private void launchGalleryIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchGalleryIntent();
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    showPermissionExplanationDialog();
                } else {
                    showToast("Izin penyimpanan diperlukan untuk membuka galeri");
                }
            }
        }
    }
    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Izin Diperlukan")
                .setMessage("Aplikasi membutuhkan izin untuk mengakses galeri agar dapat memilih gambar")
                .setPositiveButton("Setuju", (dialog, which) -> {
                    // Request permission again
                    openGallery();
                })
                .setNegativeButton("Tolak", (dialog, which) -> {
                    showToast("Fitur galeri tidak dapat digunakan tanpa izin");
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    showLoading(true);
                    File imageFile = new File(getCacheDir(), "gallery_image.jpg");
                    try (InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                         OutputStream outputStream = new FileOutputStream(imageFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                    processImageFromGallery(imageFile);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing gallery image", e);
                    showError("Failed to process image from gallery");
                    showLoading(false);
                }
            }
        }
    }

    private void processImageFromGallery(File imageFile) {
        backgroundExecutor.execute(() -> {
            try {
                File fileToUpload = maybeCompressImage(imageFile);
                sendToRoboflow(fileToUpload, imageFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error processing gallery image", e);
                runOnUiThread(() -> {
                    showError("Gagal memproses gambar");
                    showLoading(false);
                });
            }
        });
    }

    private void processDetectionResults(String jsonResponse, String imagePath) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (!jsonObject.has("predictions")) {
                showError("API Error: No 'predictions' key found.");
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            runOnUiThread(() -> {
                if (bitmap != null) {
                    resultImageView.setImageBitmap(bitmap);
                }
            });

            JSONArray predictions = jsonObject.getJSONArray("predictions");
            String resultsText = buildResultsText(predictions);

            runOnUiThread(() -> {
                resultTextView.setText(resultsText);
                toggleCameraMode(false);
            });

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error", e);
            showError("Failed to parse detection results.");
        } catch (Exception e) {
            Log.e(TAG, "Error processing results", e);
            showError("Error processing results");
        } finally {
            showLoading(false);
        }
    }

    private String buildResultsText(JSONArray predictions) throws JSONException {
        if (predictions.length() == 0) {
            return "Tidak ada objek yang terdeteksi dengan tingkat kepercayaan yang cukup.";
        }

        StringBuilder resultsBuilder = new StringBuilder();
        resultsBuilder.append("Objek Terdeteksi:\n\n");

        int validCount = 0;
        for (int i = 0; i < predictions.length(); i++) {
            JSONObject prediction = predictions.getJSONObject(i);
            double confidence = prediction.getDouble("confidence");

            if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                validCount++;
                String className = prediction.getString("class");
                resultsBuilder.append(String.format("%d. %s (Akurasi: %.1f%%)\n",
                        validCount,
                        className,
                        confidence * 100));
            }
        }

        resultsBuilder.append("\nTotal Objek: ").append(validCount);
        return resultsBuilder.toString();
    }

    private File maybeCompressImage(File originalFile) {
        if (originalFile.length() <= MAX_IMAGE_SIZE) {
            Log.d(TAG, "No compression needed, using original file.");
            return originalFile;
        }

        File compressedFile = new File(getCacheDir(), "compressed_" + originalFile.getName());
        Bitmap bitmap = null;
        try {
            bitmap = ImageUtils.decodeSampledBitmapFromFile(originalFile.getAbsolutePath(), MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
            if (bitmap == null) {
                throw new IOException("Failed to decode bitmap. ImageUtils returned null.");
            }

            try (OutputStream os = new FileOutputStream(compressedFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, TARGET_IMAGE_QUALITY, os);
                Log.d(TAG, "Image successfully compressed.");
                return compressedFile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Image compression failed, returning original file.", e);
            return originalFile;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void takePicture() {
        if (imageCapture == null) {
            showError("Camera not ready");
            return;
        }
        if (!isNetworkAvailable()) {
            showError("No internet connection");
            return;
        }

        showLoading(true);
        File photoFile = new File(getExternalFilesDir(null), "capture_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                executor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        backgroundExecutor.execute(() -> {
                            String originalImagePath = photoFile.getAbsolutePath();
                            File fileToUpload = maybeCompressImage(photoFile);
                            sendToRoboflow(fileToUpload, originalImagePath);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Image capture error", exception);
                        runOnUiThread(() -> {
                            showLoading(false);
                            showError("Capture failed: " + exception.getMessage());
                        });
                    }
                }
        );
    }

    private void sendToRoboflow(File fileToUpload, String originalPath) {

        if (fileToUpload == null || !fileToUpload.exists()) {
            showError("Image file is missing.");
            runOnUiThread(() -> showLoading(false));
            return;
        }

        MediaType mediaType = MediaType.parse("image/jpeg");
        RequestBody requestFile = RequestBody.create(mediaType, fileToUpload);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", fileToUpload.getName(), requestFile);
        Call<ResponseBody> call = roboflowApi.detectObjects(MODEL_ID, API_KEY, body);

        try {
            Response<ResponseBody> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();
                processDetectionResults(jsonResponse, originalPath);
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                showError("Server error: " + response.code() + " - " + errorBody);
            }
        } catch (IOException e) {
            Log.e(TAG, "Network request failed", e);
            showError("Network error: " + e.getMessage());
        } finally {
            if (fileToUpload.exists() && !fileToUpload.getAbsolutePath().equals(originalPath)) {
                if (!fileToUpload.delete()) {
                    Log.w(TAG, "Failed to delete compressed file: " + fileToUpload.getPath());
                }
            }
        }
    }

    private void initializeRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ROBOFLOW_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        roboflowApi = retrofit.create(RoboflowApi.class);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
                showError("Failed to start camera");
            }
        }, executor);
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);

        if (isFlashOn) {
            imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_ON);
        } else {
            imageCaptureBuilder.setFlashMode(ImageCapture.FLASH_MODE_OFF);
        }

        imageCapture = imageCaptureBuilder.build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases", e);
            showError("Could not start camera preview.");
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            captureButton.setEnabled(!show);
            galleryButton.setEnabled(!show);
            flashButton.setEnabled(!show);
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            resultTextView.setText("Error:\n" + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
package com.example.roboflowscanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class ResultActivity extends AppCompatActivity {

    private ImageView resultImageView;
    private TextView resultTextView;
    private static final String TAG = "ResultActivity";
    private String imagePath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Hasil Deteksi");
        }

        resultImageView = findViewById(R.id.resultImageView);
        resultTextView = findViewById(R.id.resultTextView);

        Intent intent = getIntent();
        imagePath = intent.getStringExtra("imagePath");
        // Terima satu blok teks yang berisi semua hasil
        String allResults = intent.getStringExtra("allResults");

        // Panggil fungsi untuk menampilkan teks
        displayResultText(allResults);

        // Panggil fungsi untuk memuat gambar
        loadImageEfficiently();
    }

    // Fungsi untuk memuat gambar (tidak ada perubahan di sini)
    private void loadImageEfficiently() {
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "Image path is null or empty.");
            Toast.makeText(this, "Gagal memuat gambar: path tidak valid.", Toast.LENGTH_SHORT).show();
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "File gambar tidak ditemukan di path: " + imagePath);
            Toast.makeText(this, "Gagal memuat gambar.", Toast.LENGTH_SHORT).show();
            return;
        }

        resultImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                resultImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int targetW = resultImageView.getWidth();
                int targetH = resultImageView.getHeight();

                if (targetW > 0 && targetH > 0) {
                    Bitmap bitmap = ImageUtils.decodeSampledBitmapFromFile(imagePath, targetW, targetH);
                    if (bitmap != null) {
                        resultImageView.setImageBitmap(bitmap);
                    } else {
                        Log.e(TAG, "Gagal mendekode bitmap, ImageUtils mengembalikan null.");
                        Toast.makeText(ResultActivity.this, "Gagal menampilkan gambar.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    // Fungsi ini sekarang hanya menerima satu string dan menampilkannya
    private void displayResultText(String allResults) {
        if (allResults != null && !allResults.isEmpty()) {
            resultTextView.setText(allResults);
        } else {
            // Fallback jika tidak ada teks yang diterima
            resultTextView.setText("Tidak ada hasil yang dapat ditampilkan.");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imagePath != null) {
            File file = new File(imagePath);
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG, "File gambar berhasil dihapus: " + imagePath);
                } else {
                    Log.w(TAG, "Gagal menghapus file gambar: " + imagePath);
                }
            }
        }
    }
}

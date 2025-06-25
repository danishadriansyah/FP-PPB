package com.example.roboflowscanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import java.io.IOException;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    /**
     * Mendecode gambar dari path file menjadi Bitmap dengan ukuran yang sudah disesuaikan
     * dan orientasi yang benar untuk mencegah OutOfMemoryError.
     *
     * @param path Path dari file gambar.
     * @param reqWidth Lebar yang diinginkan.
     * @param reqHeight Tinggi yang diinginkan.
     * @return Bitmap yang sudah di-downsample dan dirotasi, atau null jika terjadi error.
     */
    public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        Bitmap decodedBitmap = null;
        Bitmap rotatedBitmap = null;
        try {
            // Langkah 1: Decode dengan inJustDecodeBounds=true untuk memeriksa dimensi
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            // Langkah 2: Hitung inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Langkah 3: Decode bitmap dengan inSampleSize yang sudah diatur
            options.inJustDecodeBounds = false;
            decodedBitmap = BitmapFactory.decodeFile(path, options);

            if (decodedBitmap == null) {
                Log.e(TAG, "BitmapFactory.decodeFile mengembalikan null.");
                return null;
            }

            // Langkah 4: Baca orientasi EXIF dan putar gambar jika perlu
            rotatedBitmap = rotateImageIfRequired(decodedBitmap, path);
            return rotatedBitmap;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OutOfMemoryError saat memproses gambar: " + path, e);
            // Bersihkan bitmap yang mungkin sudah dibuat sebelum error
            if (decodedBitmap != null && !decodedBitmap.isRecycled()) {
                decodedBitmap.recycle();
            }
            if (rotatedBitmap != null && !rotatedBitmap.isRecycled()) {
                rotatedBitmap.recycle();
            }
            return null; // Kembalikan null karena memori tidak cukup
        } catch (Exception e) {
            Log.e(TAG, "Error umum saat memproses gambar: " + path, e);
            if (decodedBitmap != null && !decodedBitmap.isRecycled()) {
                decodedBitmap.recycle();
            }
            if (rotatedBitmap != null && !rotatedBitmap.isRecycled()) {
                rotatedBitmap.recycle();
            }
            return null;
        }
    }

    /**
     * Memeriksa data EXIF dari gambar dan memutarnya ke orientasi yang benar.
     *
     * @param img Bitmap yang akan diputar.
     * @param path Path file asli untuk membaca metadata EXIF.
     * @return Bitmap yang sudah diputar dengan benar.
     * @throws IOException Jika terjadi error saat membaca file.
     */
    private static Bitmap rotateImageIfRequired(Bitmap img, String path) throws IOException {
        ExifInterface ei = new ExifInterface(path);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img; // Tidak perlu rotasi
        }
    }

    /**
     * Fungsi utilitas untuk memutar Bitmap.
     *
     * @param source Bitmap sumber.
     * @param angle Sudut rotasi dalam derajat.
     * @return Bitmap baru yang sudah diputar.
     */
    private static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap newBitmap = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);

        // Recycle bitmap lama jika bitmap baru berhasil dibuat dan berbeda
        if (newBitmap != source) {
            source.recycle();
        }

        return newBitmap;
    }


    /**
     * Menghitung nilai inSampleSize yang optimal berdasarkan dimensi asli dan dimensi target.
     *
     * @param options Opsi BitmapFactory yang berisi dimensi asli gambar.
     * @param reqWidth Lebar target.
     * @param reqHeight Tinggi target.
     * @return Nilai inSampleSize yang optimal.
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}

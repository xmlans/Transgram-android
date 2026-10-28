package org.transgram.ocr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;

/** Decodes a photo for recognition: subsampled so the longer side is about maxSide, upright according to EXIF. */
public final class ImageLoader {
    private ImageLoader() {}

    public static Bitmap load(String path, int maxSide) throws IOException {
        final BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("not a readable image: " + path);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(path, o);
        if (bmp == null) throw new IOException("decode failed: " + path);
        final int degrees = exifDegrees(path);
        if (degrees != 0) {
            final Matrix m = new Matrix();
            m.postRotate(degrees);
            final Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }

    private static int exifDegrees(String path) {
        try {
            switch (new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}

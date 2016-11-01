package com.example.imageloaderdemo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;

/**
 * Created by Administrator on 2016/10/31.
 */
public class ImageResizer {

    private static final String TAG = "ImageResizer";

    public ImageResizer() {

    }

    public Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        /**
         * 当此参数设为true时
         * BitmapFactory只会解析图片的原始宽/高信息
         * 并不会去真正的加载图片
         */
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeResource(res, resId, options);

    }

    public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        /**
         * 当此参数设为true时
         * BitmapFactory只会解析图片的原始宽/高信息
         * 并不会去真正的加载图片
         */
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }

        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;

        Log.d(TAG, "origin, w = " + width + " h = " + height);

        int inSampleSize = 1;

        if (height > reqHeight || width > reqHeight) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }

        }

        Log.d(TAG, "sampleSize : " + inSampleSize);

        return inSampleSize;

    }

}

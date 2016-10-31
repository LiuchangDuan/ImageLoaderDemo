package com.example.imageloaderdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * Created by Administrator on 2016/10/31.
 */
public class ImageLoader {

    private Context mContext;

    private LruCache<String, Bitmap> mMemoryCache;

    public ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        // 当前进程的可用内存 单位为KB
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
    }

    /**
     * build a new instance of ImageLoader
     * @param context
     * @return a new instance of ImageLoader
     */
    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

}

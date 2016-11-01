package com.example.imageloaderdemo;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;

import libcore.io.DiskLruCache;

/**
 * Created by Administrator on 2016/10/31.
 */
public class ImageLoader {

    private static final String TAG = "ImageLoader";

    // 磁盘缓存的容量 50MB
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    private Context mContext;

    private LruCache<String, Bitmap> mMemoryCache;

    private DiskLruCache mDiskLruCache;

    public ImageLoader(Context context) {
        mContext = context.getApplicationContext();
        // 当前进程的可用内存 单位为KB (每个应用程序最高可用内存)
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                /**
                 * 第一个参数指定的是数据的缓存地址，
                 * 第二个参数指定当前应用程序的版本号，
                 * 第三个参数指定同一个key可以对应多少个缓存文件，基本都是传1，
                 * 第四个参数指定最多可以缓存多少字节的数据
                 * 
                 * 磁盘缓存在文件系统中的存储路径
                 * 应用的版本号
                 * 单个节点所对应的数据的个数
                 * 缓存的总大小
                 */
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    /**
     * 获取缓存地址
     * @param context
     * @param uniqueName
     * @return
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        // SD卡是否存在
        boolean externalStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        // 当SD卡存在或者SD卡不可被移除时
        if (externalStorageAvailable || !Environment.isExternalStorageRemovable()) {
            // /sdcard/Android/data/<application package>/cache
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            // /data/data/<application package>/cache
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return stats.getBlockSizeLong() * stats.getAvailableBlocksLong();
    }

}

package com.example.imageloaderdemo;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import libcore.io.DiskLruCache;

/**
 * Created by Administrator on 2016/10/31.
 */
public class ImageLoader {

    private static final String TAG = "ImageLoader";

    public static final int MESSAGE_POST_RESULT = 1;

    // 获取当前设备的CPU核心数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    // 核心线程数 为当前设备的CPU核心数加1
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;

    // 最大线程数 为当前设备的CPU核心数的2倍加1
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;

    // 线程闲置超时时长
    private static final long KEEP_ALIVE = 10L;

    private static final int TAG_KEY_URI = R.id.imageloader_uri;

    // 磁盘缓存的容量 50MB
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    private static final int IO_BUFFER_SIZE = 8 * 1024;

    private static final int DISK_CACHE_INDEX = 0;

    private boolean mIsDiskLruCacheCreated = false;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {

        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }

    };

    /**
     * public ThreadPoolExecutor(int corePoolSize,
     *                           int maximumPoolSize,
     *                           long keepAliveTime,
     *                           TimeUnit unit,
     *                           BlockingQueue<Runnable> workQueue,
     *                           ThreadFactory threadFactory)
     *
     *  @param corePoolSize 线程池的核心线程数
     *                      默认情况下，核心线程会在线程池中一直存活
     *                      即使它们处于闲置状态
     *
     *                      如果将ThreadPoolExecutor的allowCoreThreadTimeOut属性设置为true
     *                      那么闲置的核心线程在等待新任务到来时会有超时策略
     *                      这个时间间隔由keepAliveTime所指定
     *                      当等待时间超过keepAliveTime所指定的时长后
     *                      核心线程就会被终止
     *
     *  @param maximumPoolSize 线程池所能容纳的最大线程数
     *                         当活动线程数达到这个数值后
     *                         后续的新任务将会被阻塞
     *
     *  @param keepAliveTime 非核心线程闲置时的超时时长
     *                       超过这个时长，非核心线程就会被回收
     *
     *  @param unit 用于指定keepAliveTime参数的时间单位 这是一个枚举
     *
     *  @param workQueue 线程池中的任务队列
     *                   通过线程池的execute方法提交的Runnable对象会存储在这个参数中
     *
     *  由于3.0以上的版本AsyncTask是串行执行的
     *  无法实现并发的效果
     *
     *  故此处采用线程池
     *
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), sThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            if (uri.equals(result.uri)) {
                imageView.setImageBitmap(result.bitmap);
            } else {
                Log.w(TAG, "set image bitmap, but url has changed, ignored!");
            }
        }
    };

    private Context mContext;

    private ImageResizer mImaheResizer = new ImageResizer();

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
                mIsDiskLruCacheCreated = true;
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
     * load bitmap from memory cache or disk cache or network async
     * then bind imageView and bitmap.
     * NOTE THAT : should run in UI Thread
     * @param uri http url
     * @param imageView bitmap's bind object
     */
    public void bindBitmap(final String uri, final ImageView imageView) {
        bindBitmap(uri, imageView, 0, 0);
    }

    public void bindBitmap(final String uri, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);
        // 尝试从内存中读取图片
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };

        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);

    }

    /**
     * load bitmap from memory cache or disk cache or network
     * @param uri http url
     * @param reqWidth the width ImageView desired
     * @param reqHeight the height ImageView desired
     * @return bitmap, maybe null
     */
    public Bitmap loadBitmap(String uri, int reqWidth, int reqHeight) {
        // 首先尝试从内存缓存中读取图片
        Bitmap bitmap = loadBitmapFromMemCache(uri);
        if (bitmap != null) {
            Log.d(TAG, "loadBitmapFromMemCache, url : " + uri);
            return bitmap;
        }

        try {
            // 接着尝试从磁盘缓存中读取图片
            bitmap = loadBitmapFromDiskCache(uri, reqWidth, reqHeight);
            if (bitmap != null) {
                Log.d(TAG, "loadBitmapFromDisk, url : " + uri);
                return bitmap;
            }
            // 从网络中加载
            bitmap = loadBitmapFromHttp(uri, reqWidth, reqHeight);
            Log.d(TAG, "loadBitmapFromHttp, url : " + uri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitmap == null && !mIsDiskLruCacheCreated) {
            Log.w(TAG, "encounter error, DiskLruCache is not created.");
            bitmap = downloadBitmapFromUrl(uri);
        }

        return bitmap;

    }

    /**
     * 从内存缓存中读取图片
     * @param url
     * @return
     */
    private Bitmap loadBitmapFromMemCache(String url) {
        final String key = hashKeyFormUrl(url);
        Bitmap bitmap = getBitmapFromMemCache(key);
        return bitmap;
    }

    /**
     * 这个方法不能在主线程中调用，否则就抛出异常
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     * @throws IOException
     */
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException {
        // 通过检查当前线程的Looper是否为主线程的Looper来判断当前线程是否是主线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 如果是主线程就直接抛出异常中止程序
            throw new RuntimeException("can not visit network from UI Thread.");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        String key = hashKeyFormUrl(url);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            // 调用它的newOutputStream()方法来创建一个输出流
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadUrlToStream(url, outputStream)) {
                // 需要调用一下commit()方法进行提交才能使写入生效
                editor.commit();
            } else {
                // 调用abort()方法的话则表示放弃此次写入
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    /**
     * 从磁盘缓存中读取图片
     *
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     * @throws IOException
     */
    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight) throws IOException {
        // 通过检查当前线程的Looper是否为主线程的Looper来判断当前线程是否是主线程
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hashKeyFormUrl(url);
        DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
        if (snapShot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapShot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            bitmap = mImaheResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
            if (bitmap != null) {
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    /**
     * 访问urlString中传入的网址
     * 并通过outputStream写入到本地
     * @param urlString
     * @param outputStream
     * @return
     */
    public boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
//            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }

            return true;

        } catch (final IOException e) {
//            e.printStackTrace();
            Log.e(TAG, "downloadBitmap failed." + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 从网络拉取图片
     * @param urlString
     * @return
     */
    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadBitmap : " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    /**
     * 将图片的URL进行MD5编码
     * 编码后的字符串肯定是唯一的，并且只会包含0-F这样的字符
     * @param url
     * @return
     */
    private String hashKeyFormUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(url.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
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
//        return stats.getBlockSizeLong() * stats.getAvailableBlocksLong();
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    private static class LoaderResult {

        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }

    }

}

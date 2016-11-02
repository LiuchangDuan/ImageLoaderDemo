package com.example.imageloaderdemo;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

/**
 * Created by Administrator on 2016/11/1.
 */
public class ImageAdapter extends BaseAdapter {

    private LayoutInflater mInflater;

    private Drawable mDefaultBitmapDrawable;

    private List<String> mUrList;

    private boolean mIsGridViewIdle = true;

    private int mImageWidth = 0;

    ImageLoader mImageLoader;

    private boolean mCanGetBitmapFromNetWork = true;

    public ImageAdapter(Context context, List<String> mUrList) {
        mInflater = LayoutInflater.from(context);
        mDefaultBitmapDrawable = context.getResources().getDrawable(R.drawable.meizi);
        this.mUrList = mUrList;
//        for (String s : Images.imageThumbUrls) {
//            mUrList.add(s);
//        }
        mImageLoader = ImageLoader.build(context.getApplicationContext());
    }

    @Override
    public int getCount() {
        return mUrList.size();
    }

    @Override
    public Object getItem(int position) {
        return mUrList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder = null;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item, parent, false);
            holder = new ViewHolder();
            holder.imageView = (ImageView) convertView.findViewById(R.id.image);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        ImageView imageView = holder.imageView;
        final String tag = (String) imageView.getTag();
        final String uri = (String) getItem(position);
        if (!uri.equals(tag)) {
            imageView.setImageDrawable(mDefaultBitmapDrawable);
        }
        if (mIsGridViewIdle && mCanGetBitmapFromNetWork) {
            imageView.setTag(uri);
            mImageLoader.bindBitmap(uri, imageView, mImageWidth, mImageWidth);
        }
        return convertView;
    }

    private static class ViewHolder {
        ImageView imageView;
    }

}

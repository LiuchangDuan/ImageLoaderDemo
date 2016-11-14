package com.example.rotatepicbrowser;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Administrator on 2016/11/14.
 */
public class PictureAdapter extends ArrayAdapter<Picture> {

    public PictureAdapter(Context context, int textViewResourceId, List<Picture> objects) {
        super(context, textViewResourceId, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Picture picture = getItem(position);
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, null);
        } else {
            view = convertView;
        }
        TextView text1 = (TextView) view.findViewById(android.R.id.text1);
        text1.setText(picture.getName());
        return view;
    }

}

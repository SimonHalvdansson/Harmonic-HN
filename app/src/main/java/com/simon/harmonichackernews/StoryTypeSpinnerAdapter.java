package com.simon.harmonichackernews;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.databinding.SpinnerItemLayoutBinding;
import com.simon.harmonichackernews.databinding.SpinnerTopLayoutBinding;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.ArrayList;

class StoryTypeSpinnerAdapter extends ArrayAdapter<CharSequence> {
    @Nullable
    private CharSequence selectedTitle;

    StoryTypeSpinnerAdapter(Context context, ArrayList<CharSequence> items) {
        super(context, 0, items);
    }

    void setSelectedTitle(@Nullable CharSequence selectedTitle) {
        if (!TextUtils.equals(this.selectedTitle, selectedTitle)) {
            this.selectedTitle = selectedTitle;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TextView textView;
        if (convertView instanceof TextView) {
            textView = (TextView) convertView;
        } else {
            textView = SpinnerTopLayoutBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false).getRoot();
        }
        bindText(textView, position, true);
        return textView;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        SpinnerItemLayoutBinding binding = SpinnerItemLayoutBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false);
        bindText(binding.selectionDropdownItemTextview, position, false);
        return binding.getRoot();
    }

    private void bindText(TextView textView, int position, boolean selectedView) {
        textView.setText(selectedView && selectedTitle != null ? selectedTitle : getItem(position));

        String preferredFont = SettingsUtils.getPreferredFont(getContext());
        if (FontUtils.activeBold == null || TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(preferredFont)) {
            FontUtils.init(getContext());
        }
        if (selectedView) {
            FontUtils.setStoriesDropdownSelectedTypeface(textView);
        } else {
            FontUtils.setStoriesDropdownItemTypeface(textView);
        }
    }
}

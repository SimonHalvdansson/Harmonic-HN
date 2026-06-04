package com.simon.harmonichackernews;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.ArrayList;

class StoryTypeSpinnerAdapter extends ArrayAdapter<CharSequence> {
    StoryTypeSpinnerAdapter(Context context, ArrayList<CharSequence> items) {
        super(context, R.layout.spinner_top_layout, R.id.selection_dropdown_item_textview, items);
        setDropDownViewResource(R.layout.spinner_item_layout);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        applySelectedFont(view, true);
        return view;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        applySelectedFont(view, false);
        return view;
    }

    private void applySelectedFont(View view, boolean selectedView) {
        TextView textView = view instanceof TextView
                ? (TextView) view
                : view.findViewById(R.id.selection_dropdown_item_textview);
        if (textView == null) {
            return;
        }

        String preferredFont = SettingsUtils.getPreferredFont(getContext());
        if (FontUtils.activeBold == null || TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(preferredFont)) {
            FontUtils.init(getContext());
        }
        if (selectedView) {
            FontUtils.setStoriesDropdownSelectedTypeface(textView);
        } else {
            textView.setTypeface(FontUtils.activeBold);
        }
    }
}

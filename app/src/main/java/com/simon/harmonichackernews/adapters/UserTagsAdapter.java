package com.simon.harmonichackernews.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UserTagsAdapter extends RecyclerView.Adapter<UserTagsAdapter.ViewHolder> {

    private List<Map.Entry<String, String>> users;
    private final ItemClickListener itemClickListener;

    public interface ItemClickListener {
        void onItemClick(String username);
    }

    public UserTagsAdapter(Map<String, String> tags, ItemClickListener listener) {
        this.itemClickListener = listener;
        setItems(tags);
    }

    public void setItems(Map<String, String> tags) {
        this.users = new ArrayList<>(tags.entrySet());
        Collections.sort(this.users, (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.user_tag_item_text);
            itemView.setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(users.get(getAbsoluteAdapterPosition()).getKey());
                }
            });
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.user_tag_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map.Entry<String, String> entry = users.get(position);
        String username = entry.getKey();
        String tag = entry.getValue();
        if (TextUtils.isEmpty(tag)) {
            holder.textView.setText(username);
        } else {
            holder.textView.setText(username + " (" + tag + ")");
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }
}

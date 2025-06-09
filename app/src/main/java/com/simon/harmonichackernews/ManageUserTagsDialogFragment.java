package com.simon.harmonichackernews;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.adapters.UserTagsAdapter;
import com.simon.harmonichackernews.utils.Utils;

import java.util.Map;

public class ManageUserTagsDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_manage_user_tags";

    private UserTagsAdapter adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.user_tags_dialog, null);
        builder.setView(rootView);
        builder.setNegativeButton("Done", null);
        AlertDialog dialog = builder.create();

        RecyclerView recyclerView = rootView.findViewById(R.id.user_tags_recyclerview);
        Map<String, String> tags = Utils.getUserTags(getContext());
        adapter = new UserTagsAdapter(tags, username -> UserDialogFragment.showUserDialog(getParentFragmentManager(), username, accepted -> {
            if (accepted) {
                adapter.setItems(Utils.getUserTags(getContext()));
                adapter.notifyDataSetChanged();
            }
        }));
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        return dialog;
    }

    public static void showManageUserTagsDialog(FragmentManager fm) {
        ManageUserTagsDialogFragment fragment = new ManageUserTagsDialogFragment();
        fragment.show(fm, TAG);
    }
}

package com.pushupfit.locker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * RecyclerView adapter for the app-selection screen.
 * Each row shows the app icon, name, package name, and a checkbox.
 */
public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private final List<AppInfo> apps;

    public AppListAdapter(List<AppInfo> apps) {
        this.apps = apps;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        AppInfo app = apps.get(pos);
        h.icon.setImageDrawable(app.icon);
        h.name.setText(app.appName);
        h.pkg.setText(app.packageName);
        h.checkbox.setChecked(app.isBlocked);

        // Toggle on row or checkbox tap
        View.OnClickListener toggle = v -> {
            app.isBlocked = !app.isBlocked;
            h.checkbox.setChecked(app.isBlocked);
        };
        h.itemView.setOnClickListener(toggle);
        h.checkbox.setOnClickListener(toggle);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, pkg;
        CheckBox checkbox;

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.imgAppIcon);
            name = v.findViewById(R.id.tvAppName);
            pkg = v.findViewById(R.id.tvAppPackage);
            checkbox = v.findViewById(R.id.cbBlocked);
        }
    }
}

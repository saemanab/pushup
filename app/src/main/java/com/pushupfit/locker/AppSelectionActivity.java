package com.pushupfit.locker;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AppSelectionActivity — Screen 1b.
 *
 * Shows every installed app (using QUERY_ALL_PACKAGES) as a scrollable list.
 * The user checks apps they want to BLOCK during pushup sessions.
 *
 * Pre-checked: any packages previously saved in SessionManager.
 *
 * On "Confirm" → selections are saved → MainActivity.
 */
public class AppSelectionActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private ProgressBar loadingBar;
    private TextView tvCount;
    private EditText etSearch;
    private Button btnConfirm;

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        session = new SessionManager(this);
        loadingBar = findViewById(R.id.loadingBar);
        recyclerView = findViewById(R.id.rvApps);
        tvCount = findViewById(R.id.tvSelectedCount);
        etSearch = findViewById(R.id.etSearch);
        btnConfirm = findViewById(R.id.btnConfirmApps);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load apps in background so UI stays smooth
        loadingBar.setVisibility(View.VISIBLE);
        new LoadAppsTask().execute();

        // Live search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Confirm selection
        btnConfirm.setOnClickListener(v -> {
            Set<String> blocked = new HashSet<>();
            for (AppInfo a : allApps) {
                if (a.isBlocked)
                    blocked.add(a.packageName);
            }
            if (blocked.isEmpty()) {
                showSnackbar("Select at least one app to block, or skip →");
                // Allow skipping
            }
            session.saveBlockedPackages(blocked);

            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    // ── Filter visible list ───────────────────────────────────────────────────
    private void filterApps(String query) {
        filteredApps.clear();
        if (query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String q = query.toLowerCase();
            for (AppInfo a : allApps) {
                if (a.appName.toLowerCase().contains(q)
                        || a.packageName.toLowerCase().contains(q)) {
                    filteredApps.add(a);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        int count = 0;
        for (AppInfo a : allApps) {
            if (a.isBlocked)
                count++;
        }
        tvCount.setText(count + " app" + (count == 1 ? "" : "s") + " selected to block");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Background task — loads installed apps off the main thread
    // ══════════════════════════════════════════════════════════════════════════
    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            PackageManager pm = getPackageManager();
            Set<String> saved = session.getBlockedPackages();
            String myPkg = getPackageName();

            // Query ALL installed packages
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            List<AppInfo> result = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                // Skip ourselves and pure system daemons (keep system apps that
                // show a launcher icon — social, browser, etc.)
                if (ai.packageName.equals(myPkg))
                    continue;

                // Only include apps that have a launcher entry (i.e., visible to user)
                Intent launchIntent = pm.getLaunchIntentForPackage(ai.packageName);
                if (launchIntent == null)
                    continue;

                try {
                    AppInfo info = new AppInfo(
                            ai.packageName,
                            pm.getApplicationLabel(ai).toString(),
                            pm.getApplicationIcon(ai.packageName));
                    info.isBlocked = saved.contains(ai.packageName);
                    result.add(info);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            // Sort alphabetically
            Collections.sort(result, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
            return result;
        }

        @Override
        protected void onPostExecute(List<AppInfo> result) {
            loadingBar.setVisibility(View.GONE);
            allApps.addAll(result);
            filteredApps.addAll(result);

            adapter = new AppListAdapter(filteredApps);
            recyclerView.setAdapter(adapter);

            // Observe checkbox changes to refresh the counter
            updateCount();
        }
    }

    private void showSnackbar(String msg) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snack = Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT);
        snack.setBackgroundTint(0xFF003D7A);
        snack.setTextColor(0xFFFFFFFF);
        snack.setActionTextColor(0xFF00D2FF);
        snack.getView().setElevation(16f);
        snack.show();
    }
}

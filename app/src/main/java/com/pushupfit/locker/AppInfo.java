package com.pushupfit.locker;

import android.graphics.drawable.Drawable;

/**
 * Simple POJO representing an installed app shown in the selection list.
 */
public class AppInfo {
    public final String packageName;
    public final String appName;
    public final Drawable icon;
    public boolean isBlocked; // true = user has checked this app

    public AppInfo(String packageName, String appName, Drawable icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.isBlocked = false;
    }
}

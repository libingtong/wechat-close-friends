package io.github.inkveil.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Window;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import io.github.inkveil.data.ConfigRepository;
import io.github.inkveil.hook.HostLifecycle;

public final class SettingsPanel {
    private static final WeakHashMap<Activity, WeakReference<AlertDialog>> OPEN = new WeakHashMap<>();

    public static void dismiss(Activity activity) {
        WeakReference<AlertDialog> reference = OPEN.remove(activity);
        AlertDialog dialog = reference == null ? null : reference.get();
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static boolean show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        WeakReference<AlertDialog> reference = OPEN.get(activity);
        AlertDialog existing = reference == null ? null : reference.get();
        if (existing != null && existing.isShowing()) {
            return true;
        }
        ConfigRepository repository = ConfigRepository.get(activity);
        HostLifecycle.observe(activity, repository);
        SettingsView settings = new SettingsView(activity, true);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("墨隐 · 本地设置")
                .setView(settings).setNegativeButton("关闭", null).create();
        dialog.setOnDismissListener(ignored -> {
            settings.detach();
            OPEN.remove(activity);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
        OPEN.put(activity, new WeakReference<>(dialog));
        settings.attach();
        return true;
    }
}

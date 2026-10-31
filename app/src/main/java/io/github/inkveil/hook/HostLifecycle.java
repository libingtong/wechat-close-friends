package io.github.inkveil.hook;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.WeakHashMap;
import io.github.inkveil.data.ConfigRepository;
import io.github.inkveil.ui.SettingsPanel;

public final class HostLifecycle {
    private static boolean registered;

    public static void observe(Activity current, ConfigRepository repository) {
        if (registered) {
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        WeakHashMap<Activity, Boolean> resumed = new WeakHashMap<>();
        resumed.put(current, Boolean.TRUE);
        Runnable background = () -> {
            if (resumed.isEmpty()) {
                repository.onBackground();
            }
        };
        current.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                resumed.put(activity, Boolean.TRUE);
                main.removeCallbacks(background);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                resumed.remove(activity);
                if (resumed.isEmpty()) {
                    main.removeCallbacks(background);
                    main.postDelayed(background, 500);
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle output) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                resumed.remove(activity);
                SettingsPanel.dismiss(activity);
            }
        });
        registered = true;
    }
}

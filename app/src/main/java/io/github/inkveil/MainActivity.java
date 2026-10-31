package io.github.inkveil;

import android.app.Activity;
import android.os.Bundle;
import io.github.inkveil.ui.SettingsView;

public final class MainActivity extends Activity {
    private SettingsView settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsView(this, false);
        float density = getResources().getDisplayMetrics().density;
        settings.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(Math.round(16 * density) + insets.getSystemWindowInsetLeft(),
                    Math.round(12 * density) + insets.getSystemWindowInsetTop(),
                    Math.round(16 * density) + insets.getSystemWindowInsetRight(),
                    Math.round(8 * density) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(settings);
    }

    @Override
    protected void onStart() {
        super.onStart();
        settings.attach();
    }

    @Override
    protected void onStop() {
        settings.detach();
        if (!isChangingConfigurations()) {
            settings.onBackground();
        }
        super.onStop();
    }
}

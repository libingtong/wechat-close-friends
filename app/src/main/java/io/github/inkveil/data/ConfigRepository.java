package io.github.inkveil.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.inkveil.core.Surface;
import io.github.inkveil.core.VisibilityEngine;
import io.github.inkveil.core.VisibilitySnapshot;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ConfigRepository {
    private static ConfigRepository instance;
    private final Context applicationContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "InkVeil-Config");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final VisibilityEngine engine = new VisibilityEngine();
    private final LinkedHashSet<String> temporary = new LinkedHashSet<>();
    private volatile UiState published = new UiState(AppConfig.defaults(), Collections.emptySet(), false, null);
    private volatile Listener listener;
    private AppConfig config = AppConfig.defaults();
    private SharedPreferences preferences;

    private ConfigRepository(Context applicationContext) {
        this.applicationContext = applicationContext;
        worker.execute(this::load);
    }

    public static synchronized ConfigRepository get(Context context) {
        if (instance == null) {
            Context application = context.getApplicationContext();
            if (application == null) {
                throw new IllegalStateException("应用上下文尚未就绪");
            }
            instance = new ConfigRepository(application);
        }
        return instance;
    }

    public VisibilityEngine engine() {
        return engine;
    }

    public void attach(Listener value) {
        listener = value;
        deliver();
    }

    public void detach(Listener value) {
        if (listener == value) {
            listener = null;
        }
    }

    public void setEnabled(boolean value) {
        worker.execute(() -> persist(config.withEnabled(value)));
    }

    public void setSurface(Surface surface, boolean value) {
        worker.execute(() -> persist(config.withSurface(surface, value)));
    }

    public void setRehideOnBackground(boolean value) {
        worker.execute(() -> persist(config.withRehide(value)));
    }

    public void addContact(String identifier, String label) {
        String canonical = identifier.trim();
        String displayLabel = label == null ? "" : label.trim();
        if (canonical.isEmpty()) {
            return;
        }
        worker.execute(() -> {
            ArrayList<HiddenContact> contacts = new ArrayList<>(config.contacts);
            for (HiddenContact contact : contacts) {
                if (contact.identifier.equals(canonical)) {
                    publish("此标识已在名单中");
                    return;
                }
            }
            contacts.add(new HiddenContact(canonical, displayLabel));
            persist(config.withContacts(contacts));
        });
    }

    public void removeContact(String identifier) {
        worker.execute(() -> {
            ArrayList<HiddenContact> contacts = new ArrayList<>(config.contacts);
            contacts.removeIf(contact -> contact.identifier.equals(identifier));
            if (persist(config.withContacts(contacts))) {
                temporary.remove(identifier);
                publish(null);
            }
        });
    }

    public void toggleTemporary(String identifier) {
        worker.execute(() -> {
            if (!temporary.remove(identifier)) {
                temporary.add(identifier);
            }
            publish(null);
        });
    }

    public void showAllTemporarily() {
        worker.execute(() -> {
            for (HiddenContact contact : config.contacts) {
                temporary.add(contact.identifier);
            }
            publish(null);
        });
    }

    public void toggleAllTemporarily() {
        worker.execute(() -> {
            if (temporary.isEmpty()) {
                for (HiddenContact contact : config.contacts) {
                    temporary.add(contact.identifier);
                }
            } else {
                temporary.clear();
            }
            publish(null);
        });
    }

    public void rehide() {
        worker.execute(() -> {
            temporary.clear();
            publish(null);
        });
    }

    public void onBackground() {
        worker.execute(() -> {
            if (config.rehideOnBackground && !temporary.isEmpty()) {
                temporary.clear();
                publish(null);
            }
        });
    }

    private void load() {
        try {
            preferences = applicationContext.getSharedPreferences("inkveil_config_v1", Context.MODE_PRIVATE);
            String saved = preferences.getString("configuration", null);
            if (saved != null) {
                JSONObject root = new JSONObject(saved);
                if (root.optInt("schemaVersion", 1) != 1) {
                    throw new JSONException("不支持的配置版本");
                }
                LinkedHashMap<String, HiddenContact> contacts = new LinkedHashMap<>();
                JSONArray entries = root.optJSONArray("contacts");
                if (entries != null) {
                    for (int index = 0; index < entries.length(); index++) {
                        JSONObject item = entries.getJSONObject(index);
                        String identifier = item.getString("identifier").trim();
                        if (!identifier.isEmpty()) {
                            contacts.put(identifier, new HiddenContact(identifier, item.optString("label", "")));
                        }
                    }
                }
                config = new AppConfig(root.optBoolean("enabled", false),
                        root.optInt("surfaceMask", Surface.ALL_MASK),
                        root.optBoolean("rehideOnBackground", true), new ArrayList<>(contacts.values()));
            }
            publish(null);
        } catch (Exception error) {
            config = AppConfig.defaults();
            publish("配置读取失败，当前使用默认放行规则；原配置未被覆盖");
        }
    }

    private boolean persist(AppConfig next) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 1);
            root.put("enabled", next.enabled);
            root.put("surfaceMask", next.surfaceMask);
            root.put("rehideOnBackground", next.rehideOnBackground);
            JSONArray contacts = new JSONArray();
            for (HiddenContact contact : next.contacts) {
                JSONObject item = new JSONObject();
                item.put("identifier", contact.identifier);
                item.put("label", contact.label);
                contacts.put(item);
            }
            root.put("contacts", contacts);
            if (preferences == null || !preferences.edit().putString("configuration", root.toString()).commit()) {
                publish("保存失败，已保留上一次规则");
                return false;
            }
            config = next;
            publish(null);
            return true;
        } catch (Exception error) {
            publish("保存失败，已保留上一次规则");
            return false;
        }
    }

    private void publish(String message) {
        ArrayList<String> identifiers = new ArrayList<>(config.contacts.size());
        for (HiddenContact contact : config.contacts) {
            identifiers.add(contact.identifier);
        }
        engine.publish(VisibilitySnapshot.create(config.enabled, config.surfaceMask, identifiers, temporary));
        published = new UiState(config, Collections.unmodifiableSet(new HashSet<>(temporary)), true, message);
        deliver();
    }

    private void deliver() {
        main.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onState(published);
            }
        });
    }

    public interface Listener {
        void onState(UiState state);
    }

    public static final class UiState {
        public final AppConfig config;
        public final Set<String> temporaryIds;
        public final boolean loaded;
        public final String message;

        private UiState(AppConfig config, Set<String> temporaryIds, boolean loaded, String message) {
            this.config = config;
            this.temporaryIds = temporaryIds;
            this.loaded = loaded;
            this.message = message;
        }
    }
}

package io.github.inkveil.hook;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.inkveil.core.CareModeEntryPolicy;
import io.github.inkveil.core.SearchEntryPolicy;
import io.github.inkveil.core.TitleGesturePolicy;
import io.github.inkveil.data.ConfigRepository;
import io.github.inkveil.ui.SettingsPanel;

public final class MainHook implements IXposedHookLoadPackage {
    private static final String LAUNCHER = "com.tencent.mm.ui.LauncherUI";
    private static final String CARE_SCANNED = "io.github.inkveil.careScanned";
    private static final String SEARCH_POSTED = "io.github.inkveil.searchPosted";
    private static final String SEARCH_BOUND = "io.github.inkveil.searchBound";
    private static final String TITLE_BOUND = "io.github.inkveil.titleBound";
    private static final String TITLE_REFERENCE = "io.github.inkveil.titleReference";
    private static final String TITLE_PENDING = "io.github.inkveil.titlePending";
    private static final int MAX_VIEW_NODES = 2_000;
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean CARE_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean SEARCH_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean TITLE_FAILURE = new AtomicBoolean();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam parameter) {
        if (parameter == null || !"com.tencent.mm".equals(parameter.packageName)
                || !"com.tencent.mm".equals(parameter.processName)
                || !INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        installCareModeEntry(parameter.classLoader);
        installSearchEntry(parameter.classLoader);
        installTitleGesture(parameter.classLoader);
    }

    private static void installCareModeEntry(ClassLoader loader) {
        try {
            Class<?> target = XposedHelpers.findClass(CareModeEntryPolicy.TARGET_ACTIVITY, loader);
            if (XposedBridge.hookAllMethods(target, "initView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam parameter) {
                    try {
                        if (!(parameter.thisObject instanceof Activity)) {
                            return;
                        }
                        Activity activity = (Activity) parameter.thisObject;
                        if (XposedHelpers.getAdditionalInstanceField(activity, CARE_SCANNED) != null) {
                            return;
                        }
                        XposedHelpers.setAdditionalInstanceField(activity, CARE_SCANNED, Boolean.TRUE);
                        bindCareButton(activity);
                    } catch (Throwable error) {
                        reportOnce(CARE_FAILURE, "关怀模式入口不可用");
                    }
                }
            }).isEmpty()) {
                reportOnce(CARE_FAILURE, "未找到关怀模式 initView");
            }
        } catch (Throwable error) {
            reportOnce(CARE_FAILURE, "当前微信未找到已知关怀模式入口");
        }
    }

    private static void bindCareButton(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        ArrayList<Button> buttons = new ArrayList<>();
        ArrayList<CareModeEntryPolicy.Candidate> candidates = new ArrayList<>();
        ArrayDeque<View> pending = new ArrayDeque<>();
        pending.add(content);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < MAX_VIEW_NODES) {
            View view = pending.removeFirst();
            if (view instanceof Button) {
                Button button = (Button) view;
                buttons.add(button);
                candidates.add(new CareModeEntryPolicy.Candidate(true, button.getId(), button.getText()));
            }
            enqueueChildren(view, pending);
        }
        if (!pending.isEmpty()) {
            return;
        }
        int selected = CareModeEntryPolicy.uniqueCandidateIndex(activity.getClass().getName(), candidates);
        if (selected >= 0) {
            buttons.get(selected).setOnLongClickListener(view -> {
                try {
                    return SettingsPanel.show(activity);
                } catch (Throwable error) {
                    reportOnce(CARE_FAILURE, "打开本地设置失败");
                    return false;
                }
            });
        }
    }

    private static void installSearchEntry(ClassLoader loader) {
        try {
            Class<?> target = XposedHelpers.findClass(SearchEntryPolicy.TARGET_VIEW, loader);
            XposedBridge.hookAllConstructors(target, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam parameter) {
                    try {
                        if (!(parameter.thisObject instanceof View)) {
                            return;
                        }
                        View root = (View) parameter.thisObject;
                        if (XposedHelpers.getAdditionalInstanceField(root, SEARCH_POSTED) != null) {
                            return;
                        }
                        XposedHelpers.setAdditionalInstanceField(root, SEARCH_POSTED, Boolean.TRUE);
                        root.post(() -> {
                            try {
                                bindSearchInput(root);
                            } catch (Throwable error) {
                                reportOnce(SEARCH_FAILURE, "绑定搜索命令失败");
                            }
                        });
                    } catch (Throwable error) {
                        reportOnce(SEARCH_FAILURE, "搜索入口初始化失败");
                    }
                }
            });
        } catch (Throwable error) {
            reportOnce(SEARCH_FAILURE, "当前微信未找到已知搜索输入类");
        }
    }

    private static void bindSearchInput(View root) {
        ArrayDeque<View> pending = new ArrayDeque<>();
        pending.add(root);
        EditText selected = null;
        int count = 0;
        int visited = 0;
        while (!pending.isEmpty() && visited++ < MAX_VIEW_NODES) {
            View view = pending.removeFirst();
            if (view instanceof EditText) {
                selected = (EditText) view;
                count++;
            }
            enqueueChildren(view, pending);
        }
        if (!pending.isEmpty() || !SearchEntryPolicy.shouldAttach(root.getClass().getName(), count)
                || XposedHelpers.getAdditionalInstanceField(selected, SEARCH_BOUND) != null) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(selected, SEARCH_BOUND, Boolean.TRUE);
        EditText input = selected;
        input.addTextChangedListener(new TextWatcher() {
            private boolean handling;

            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (handling) {
                    return;
                }
                try {
                    if (!SearchEntryPolicy.isSettingsCommand(text)) {
                        return;
                    }
                    Activity activity = findActivity(input.getContext());
                    if (activity == null || !SettingsPanel.show(activity)) {
                        return;
                    }
                    handling = true;
                    text.clear();
                    InputMethodManager keyboard = (InputMethodManager)
                            activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (keyboard != null) {
                        keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                } catch (Throwable error) {
                    reportOnce(SEARCH_FAILURE, "执行搜索命令失败");
                } finally {
                    handling = false;
                }
            }
        });
    }

    private static void installTitleGesture(ClassLoader loader) {
        try {
            Class<?> target = XposedHelpers.findClass(LAUNCHER, loader);
            if (XposedBridge.hookAllMethods(target, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam parameter) {
                    try {
                        if (!(parameter.thisObject instanceof Activity)) {
                            return;
                        }
                        Activity activity = (Activity) parameter.thisObject;
                        if (!LAUNCHER.equals(activity.getClass().getName())) {
                            return;
                        }
                        Object stored = XposedHelpers.getAdditionalInstanceField(activity, TITLE_REFERENCE);
                        if (stored instanceof WeakReference) {
                            Object title = ((WeakReference<?>) stored).get();
                            if (title instanceof TextView && ((TextView) title).isAttachedToWindow()
                                    && isWechatTitle((TextView) title)
                                    && XposedHelpers.getAdditionalInstanceField(title, TITLE_BOUND) != null) {
                                return;
                            }
                        }
                        if (XposedHelpers.getAdditionalInstanceField(activity, TITLE_PENDING) != null) {
                            return;
                        }
                        XposedHelpers.setAdditionalInstanceField(activity, TITLE_PENDING, Boolean.TRUE);
                        View decor = activity.getWindow().getDecorView();
                        decor.post(() -> {
                            try {
                                bindTitle(activity);
                            } catch (Throwable error) {
                                reportOnce(TITLE_FAILURE, "首页标题绑定失败");
                            } finally {
                                XposedHelpers.removeAdditionalInstanceField(activity, TITLE_PENDING);
                            }
                        });
                    } catch (Throwable error) {
                        reportOnce(TITLE_FAILURE, "首页标题入口初始化失败");
                    }
                }
            }).isEmpty()) {
                reportOnce(TITLE_FAILURE, "未找到首页 onResume");
            }
        } catch (Throwable error) {
            reportOnce(TITLE_FAILURE, "当前微信未找到已知首页入口");
        }
    }

    private static void bindTitle(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        TextView title = knownTitle(activity);
        if (title == null) {
            return;
        }
        if (XposedHelpers.getAdditionalInstanceField(title, TITLE_BOUND) != null) {
            XposedHelpers.setAdditionalInstanceField(activity, TITLE_REFERENCE, new WeakReference<>(title));
            return;
        }
        Object listenerInfo = XposedHelpers.getObjectField(title, "mListenerInfo");
        View.OnClickListener previousClick = listenerInfo == null ? null
                : (View.OnClickListener) XposedHelpers.getObjectField(listenerInfo, "mOnClickListener");
        View.OnTouchListener previousTouch = listenerInfo == null ? null
                : (View.OnTouchListener) XposedHelpers.getObjectField(listenerInfo, "mOnTouchListener");
        ConfigRepository repository = ConfigRepository.get(activity);
        HostLifecycle.observe(activity, repository);
        TitleGesturePolicy gesture = new TitleGesturePolicy(3, 800);
        title.setOnTouchListener((view, event) -> {
            try {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    gesture.onGestureStart();
                }
                return previousTouch != null && previousTouch.onTouch(view, event);
            } catch (Throwable error) {
                reportOnce(TITLE_FAILURE, "标题触摸处理失败");
                return false;
            }
        });
        title.setOnClickListener(view -> {
            try {
                if (previousClick != null) {
                    previousClick.onClick(view);
                }
                if (gesture.onTap(SystemClock.uptimeMillis())) {
                    repository.toggleAllTemporarily();
                }
            } catch (Throwable error) {
                reportOnce(TITLE_FAILURE, "标题多击处理失败");
            }
        });
        title.setOnLongClickListener(view -> {
            try {
                if (gesture.onLongPress()) {
                    repository.toggleAllTemporarily();
                }
                return true;
            } catch (Throwable error) {
                reportOnce(TITLE_FAILURE, "标题长按处理失败");
                return false;
            }
        });
        XposedHelpers.setAdditionalInstanceField(title, TITLE_BOUND, Boolean.TRUE);
        XposedHelpers.setAdditionalInstanceField(activity, TITLE_REFERENCE, new WeakReference<>(title));
    }

    private static TextView knownTitle(Activity activity) {
        for (String field : new String[] {"chattingTabUI", "chattingTabUl"}) {
            try {
                Object tab = XposedHelpers.getObjectField(activity, field);
                Object header = XposedHelpers.getObjectField(tab, "b");
                Object candidate = XposedHelpers.getObjectField(header, "e");
                if (candidate instanceof TextView && isWechatTitle((TextView) candidate)) {
                    return (TextView) candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isWechatTitle(TextView view) {
        return view.isShown() && "微信".contentEquals(view.getText());
    }

    private static void enqueueChildren(View view, ArrayDeque<View> pending) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                pending.addLast(group.getChildAt(index));
            }
        }
    }

    private static Activity findActivity(Context context) {
        for (int depth = 0; depth < 16 && context != null; depth++) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) {
                return null;
            }
            context = base;
        }
        return null;
    }

    private static void reportOnce(AtomicBoolean flag, String message) {
        if (flag.compareAndSet(false, true)) {
            XposedBridge.log("InkVeil：" + message + "；宿主原行为保持放行。");
        }
    }
}

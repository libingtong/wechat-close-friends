package io.github.inkveil.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import io.github.inkveil.core.Surface;
import io.github.inkveil.data.ConfigRepository;
import io.github.inkveil.data.HiddenContact;

public final class SettingsView extends LinearLayout implements ConfigRepository.Listener {
    private final Activity activity;
    private final ConfigRepository repository;
    private final TextView status;
    private final CheckBox enabled;
    private final CheckBox[] surfaces = new CheckBox[Surface.values().length];
    private final CheckBox rehide;
    private final ContactAdapter contacts = new ContactAdapter();
    private boolean binding;

    public SettingsView(Activity activity, boolean host) {
        super(activity);
        this.activity = activity;
        repository = ConfigRepository.get(activity);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(12), dp(16), dp(8));
        setBackgroundColor(Color.rgb(246, 247, 250));
        TextView title = text(host ? "墨隐 · 微信内设置" : "墨隐 · 独立预览", 22);
        addView(title);
        TextView explanation = text(host
                ? "本机私有配置 · 两种入口共用\n开发版：仅规则与入口，微信列表过滤尚未接入"
                : "此页面使用模块自身沙箱，不与微信内设置同步\n开发版：仅规则与入口，尚未实现真实微信隐藏", 12);
        explanation.setTextColor(Color.rgb(104, 71, 23));
        addView(explanation);
        status = text("正在后台加载配置…", 12);
        addView(status);
        enabled = check("启用隐藏规则", value -> repository.setEnabled(value));
        addView(enabled);
        Button expand = button("展开 / 收起各页面选项");
        addView(expand);
        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(VERTICAL);
        options.setVisibility(GONE);
        String[] labels = {"首页会话", "通讯录", "搜索结果", "通知"};
        Surface[] values = Surface.values();
        for (int index = 0; index < values.length; index++) {
            Surface surface = values[index];
            surfaces[index] = check(labels[index] + "隐藏规则", value -> repository.setSurface(surface, value));
            options.addView(surfaces[index]);
        }
        rehide = check("退到后台后重新隐藏", repository::setRehideOnBackground);
        options.addView(rehide);
        addView(options);
        expand.setOnClickListener(view -> options.setVisibility(options.getVisibility() == GONE ? VISIBLE : GONE));
        LinearLayout actions = new LinearLayout(activity);
        addAction(actions, "添加", this::addContact);
        addAction(actions, "临时全显", repository::showAllTemporarily);
        addAction(actions, "立即重隐", repository::rehide);
        addView(actions);
        TextView hint = text("隐藏名单 · 点按临时显隐，长按移除", 13);
        addView(hint);
        ListView list = new ListView(activity);
        list.setAdapter(contacts);
        list.setOnItemClickListener((parent, view, position, identifier) ->
                repository.toggleTemporary(contacts.items.get(position).identifier));
        list.setOnItemLongClickListener((parent, view, position, identifier) -> {
            HiddenContact contact = contacts.items.get(position);
            new AlertDialog.Builder(activity).setTitle("移出隐藏名单")
                    .setMessage(contact.identifier).setNegativeButton("取消", null)
                    .setPositiveButton("移除", (dialog, which) -> repository.removeContact(contact.identifier)).show();
            return true;
        });
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        Button preview = button("规则预览（不操作微信数据）");
        preview.setOnClickListener(view -> preview());
        addView(preview);
    }

    public void attach() {
        repository.attach(this);
    }

    public void detach() {
        repository.detach(this);
    }

    public void onBackground() {
        repository.onBackground();
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    @Override
    public void onState(ConfigRepository.UiState state) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        binding = true;
        enabled.setChecked(state.config.enabled);
        enabled.setEnabled(state.loaded);
        Surface[] values = Surface.values();
        for (int index = 0; index < values.length; index++) {
            surfaces[index].setChecked((state.config.surfaceMask & values[index].mask) != 0);
            surfaces[index].setEnabled(state.loaded);
        }
        rehide.setChecked(state.config.rehideOnBackground);
        rehide.setEnabled(state.loaded);
        contacts.items = state.config.contacts;
        contacts.temporary = state.temporaryIds;
        contacts.notifyDataSetChanged();
        status.setText(state.loaded ? "名单 " + state.config.contacts.size() + " 项 · 临时显示 "
                + state.temporaryIds.size() + " 项 · " + (state.config.enabled ? "规则启用" : "规则关闭")
                : "正在后台加载配置…");
        binding = false;
        if (state.message != null) {
            Toast.makeText(activity, state.message, Toast.LENGTH_LONG).show();
        }
    }

    private void addContact() {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText identifier = new EditText(activity);
        identifier.setSingleLine(true);
        identifier.setHint("真实 username / wxid（不是昵称）");
        EditText label = new EditText(activity);
        label.setSingleLine(true);
        label.setHint("本地备注（可选）");
        fields.addView(identifier);
        fields.addView(label);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("添加隐藏对象").setView(fields)
                .setNegativeButton("取消", null).setPositiveButton("添加", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = identifier.getText().toString().trim();
                    if (value.isEmpty()) {
                        identifier.setError("请输入真实标识");
                        return;
                    }
                    repository.addContact(value, label.getText().toString());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void preview() {
        EditText identifier = new EditText(activity);
        identifier.setSingleLine(true);
        identifier.setHint("输入标识，查看内存规则结果");
        new AlertDialog.Builder(activity).setTitle("规则预览").setView(identifier)
                .setNegativeButton("取消", null).setPositiveButton("检查", (dialog, which) -> {
                    String canonical = identifier.getText().toString().trim();
                    StringBuilder result = new StringBuilder();
                    String[] labels = {"会话", "通讯录", "搜索", "通知"};
                    Surface[] values = Surface.values();
                    for (int index = 0; index < values.length; index++) {
                        result.append(labels[index]).append("：")
                                .append(repository.engine().shouldHide(canonical, values[index]) ? "应隐藏" : "应显示")
                                .append('\n');
                    }
                    new AlertDialog.Builder(activity).setTitle("规则结果（非微信实测）")
                            .setMessage(result.toString()).setPositiveButton("知道了", null).show();
                }).show();
    }

    private CheckBox check(String label, BooleanAction action) {
        CheckBox check = new CheckBox(activity);
        check.setText(label);
        check.setTextColor(Color.rgb(28, 36, 48));
        check.setOnCheckedChangeListener((button, value) -> {
            if (!binding) {
                action.run(value);
            }
        });
        return check;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(28, 36, 48));
        text.setPadding(0, dp(3), 0, dp(3));
        return text;
    }

    private Button button(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        return button;
    }

    private void addAction(LinearLayout row, String label, Runnable action) {
        Button button = button(label);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface BooleanAction {
        void run(boolean value);
    }

    private final class ContactAdapter extends BaseAdapter {
        private List<HiddenContact> items = Collections.emptyList();
        private Set<String> temporary = Collections.emptySet();

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public HiddenContact getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView ? (TextView) convertView : text("", 14);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            HiddenContact contact = items.get(position);
            row.setText((contact.label.isEmpty() ? contact.identifier : contact.label + "\n" + contact.identifier)
                    + (temporary.contains(contact.identifier) ? "  [临时显示]" : "  [在隐藏名单]"));
            return row;
        }
    }
}

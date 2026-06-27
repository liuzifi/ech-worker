package com.echworker.vpn;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用选择器界面
 * 
 * 功能：
 * - 显示所有已安装应用列表（带图标、名称、包名）
 * - 支持多选应用
 * - 搜索过滤应用
 * - 返回选中的应用包名列表
 */
public class AppSelectorActivity extends Activity {

    public static final String EXTRA_SELECTED_APPS = "selected_apps";
    public static final String EXTRA_MODE = "mode"; // "whitelist" 或 "blacklist"

    private ListView listView;
    private EditText searchEdit;
    private Button btnConfirm;
    private Button btnSelectAll;
    private Button btnDeselectAll;
    
    private List<AppInfo> allApps;
    private List<AppInfo> filteredApps;
    private AppListAdapter adapter;
    private Set<String> selectedPackages;
    private String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selector);

        // 获取传入的参数
        Intent intent = getIntent();
        mode = intent.getStringExtra(EXTRA_MODE);
        String selectedAppsStr = intent.getStringExtra(EXTRA_SELECTED_APPS);
        
        selectedPackages = new HashSet<>();
        if (selectedAppsStr != null && !selectedAppsStr.isEmpty()) {
            for (String pkg : selectedAppsStr.split(",")) {
                selectedPackages.add(pkg.trim());
            }
        }

        // 初始化 UI
        initViews();
        
        // 加载应用列表
        loadApps();
        
        // 设置监听器
        setupListeners();
    }

    private void initViews() {
        listView = findViewById(R.id.list_apps);
        searchEdit = findViewById(R.id.edit_search);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnSelectAll = findViewById(R.id.btn_select_all);
        btnDeselectAll = findViewById(R.id.btn_deselect_all);
        
        // 设置标题
        TextView title = findViewById(R.id.text_title);
        if ("whitelist".equals(mode)) {
            title.setText("选择走代理的应用（白名单）");
        } else if ("blacklist".equals(mode)) {
            title.setText("选择不走代理的应用（黑名单）");
        } else {
            title.setText("选择应用");
        }
    }

    private void loadApps() {
        allApps = new ArrayList<>();
        filteredApps = new ArrayList<>();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : installedApps) {
            // 过滤系统应用（可选）
            // if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            //     continue;
            // }

            AppInfo info = new AppInfo();
            info.packageName = appInfo.packageName;
            info.appName = pm.getApplicationLabel(appInfo).toString();
            info.icon = pm.getApplicationIcon(appInfo);
            info.isSelected = selectedPackages.contains(info.packageName);
            
            allApps.add(info);
        }

        // 按名称排序
        Collections.sort(allApps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        
        filteredApps.addAll(allApps);

        // 设置适配器
        adapter = new AppListAdapter();
        listView.setAdapter(adapter);
    }

    private void setupListeners() {
        // 搜索功能
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 全选
        btnSelectAll.setOnClickListener(v -> {
            for (AppInfo app : filteredApps) {
                app.isSelected = true;
            }
            adapter.notifyDataSetChanged();
        });

        // 取消全选
        btnDeselectAll.setOnClickListener(v -> {
            for (AppInfo app : filteredApps) {
                app.isSelected = false;
            }
            adapter.notifyDataSetChanged();
        });

        // 确认选择
        btnConfirm.setOnClickListener(v -> {
            List<String> selected = new ArrayList<>();
            for (AppInfo app : allApps) {
                if (app.isSelected) {
                    selected.add(app.packageName);
                }
            }

            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_APPS, String.join(",", selected));
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void filterApps(String query) {
        filteredApps.clear();
        
        if (query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : allApps) {
                if (app.appName.toLowerCase().contains(lowerQuery) ||
                    app.packageName.toLowerCase().contains(lowerQuery)) {
                    filteredApps.add(app);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
    }

    /**
     * 应用列表适配器
     */
    private class AppListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredApps.size();
        }

        @Override
        public AppInfo getItem(int position) {
            return filteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.img_icon);
                holder.appName = convertView.findViewById(R.id.text_app_name);
                holder.packageName = convertView.findViewById(R.id.text_package_name);
                holder.checkbox = convertView.findViewById(R.id.checkbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo app = getItem(position);
            holder.icon.setImageDrawable(app.icon);
            holder.appName.setText(app.appName);
            holder.packageName.setText(app.packageName);
            holder.checkbox.setChecked(app.isSelected);

            // 点击整行切换选择状态
            convertView.setOnClickListener(v -> {
                app.isSelected = !app.isSelected;
                holder.checkbox.setChecked(app.isSelected);
            });

            // 点击 checkbox 也切换
            holder.checkbox.setOnClickListener(v -> {
                app.isSelected = holder.checkbox.isChecked();
            });

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            TextView appName;
            TextView packageName;
            CheckBox checkbox;
        }
    }

    /**
     * 应用信息
     */
    private static class AppInfo {
        String packageName;
        String appName;
        android.graphics.drawable.Drawable icon;
        boolean isSelected;
    }
}

package com.echworker.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * ECH Worker VPN 主界面
 * 
 * 功能：
 * - 配置 VPN 连接参数（服务器地址、Token 等）
 * - 选择代理模式（全局/白名单/黑名单）
 * - 启动/停止 VPN 连接
 * - 显示连接状态
 */
public class MainActivity extends Activity {

    private static final int REQUEST_VPN_PERMISSION = 1;
    private static final int REQUEST_SELECT_APPS = 2;

    // UI 组件
    private EditText editServerAddr;
    private EditText editServerIP;
    private EditText editToken;
    private EditText editDnsServer;
    private EditText editEchDomain;
    private RadioGroup radioProxyMode;
    private EditText editAppList;
    private Switch switchVpn;
    private TextView textStatus;
    private Button btnSaveConfig;

    private VpnConfig config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 UI
        initViews();
        
        // 加载配置
        config = VpnConfig.load(this);
        loadConfigToUI();
        
        // 设置监听器
        setupListeners();
        
        // 更新状态
        updateStatus();
    }

    private void initViews() {
        editServerAddr = findViewById(R.id.edit_server_addr);
        editServerIP = findViewById(R.id.edit_server_ip);
        editToken = findViewById(R.id.edit_token);
        editDnsServer = findViewById(R.id.edit_dns_server);
        editEchDomain = findViewById(R.id.edit_ech_domain);
        radioProxyMode = findViewById(R.id.radio_proxy_mode);
        editAppList = findViewById(R.id.edit_app_list);
        switchVpn = findViewById(R.id.switch_vpn);
        textStatus = findViewById(R.id.text_status);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        
        // 应用选择器按钮
        Button btnSelectApps = findViewById(R.id.btn_select_apps);
        btnSelectApps.setOnClickListener(v -> openAppSelector());
    }

    private void loadConfigToUI() {
        editServerAddr.setText(config.serverAddr);
        editServerIP.setText(config.serverIP);
        editToken.setText(config.token);
        editDnsServer.setText(config.dnsServer);
        editEchDomain.setText(config.echDomain);
        
        // 设置代理模式
        switch (config.proxyMode) {
            case VpnConfig.MODE_GLOBAL:
                radioProxyMode.check(R.id.radio_global);
                break;
            case VpnConfig.MODE_WHITELIST:
                radioProxyMode.check(R.id.radio_whitelist);
                editAppList.setText(String.join(",", config.allowedApps));
                break;
            case VpnConfig.MODE_BLACKLIST:
                radioProxyMode.check(R.id.radio_blacklist);
                editAppList.setText(String.join(",", config.disallowedApps));
                break;
        }
    }

    private void setupListeners() {
        // 保存配置按钮
        btnSaveConfig.setOnClickListener(v -> saveConfig());
        
        // VPN 开关
        switchVpn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startVpn();
            } else {
                stopVpn();
            }
        });
        
        // 代理模式切换
        radioProxyMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_global) {
                editAppList.setEnabled(false);
                editAppList.setHint("全局代理，所有应用走代理");
            } else if (checkedId == R.id.radio_whitelist) {
                editAppList.setEnabled(true);
                editAppList.setHint("白名单应用包名，逗号分隔");
            } else if (checkedId == R.id.radio_blacklist) {
                editAppList.setEnabled(true);
                editAppList.setHint("黑名单应用包名，逗号分隔");
            }
        });
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        config.serverAddr = editServerAddr.getText().toString().trim();
        config.serverIP = editServerIP.getText().toString().trim();
        config.token = editToken.getText().toString().trim();
        config.dnsServer = editDnsServer.getText().toString().trim();
        config.echDomain = editEchDomain.getText().toString().trim();
        
        // 获取代理模式
        int checkedId = radioProxyMode.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_global) {
            config.proxyMode = VpnConfig.MODE_GLOBAL;
        } else if (checkedId == R.id.radio_whitelist) {
            config.proxyMode = VpnConfig.MODE_WHITELIST;
            String apps = editAppList.getText().toString().trim();
            config.allowedApps.clear();
            if (!apps.isEmpty()) {
                for (String app : apps.split(",")) {
                    config.allowedApps.add(app.trim());
                }
            }
        } else if (checkedId == R.id.radio_blacklist) {
            config.proxyMode = VpnConfig.MODE_BLACKLIST;
            String apps = editAppList.getText().toString().trim();
            config.disallowedApps.clear();
            if (!apps.isEmpty()) {
                for (String app : apps.split(",")) {
                    config.disallowedApps.add(app.trim());
                }
            }
        }
        
        // 验证配置
        if (!config.isValid()) {
            Toast.makeText(this, "请填写服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 保存到 SharedPreferences
        config.save(this);
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
    }

    /**
     * 启动 VPN
     */
    private void startVpn() {
        // 保存配置
        saveConfig();
        
        // 请求 VPN 权限
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, REQUEST_VPN_PERMISSION);
        } else {
            // 已有权限，直接启动
            startVpnService();
        }
    }

    /**
     * 停止 VPN
     */
    private void stopVpn() {
        Intent intent = new Intent(this, EchVpnService.class);
        intent.setAction(EchVpnService.ACTION_DISCONNECT);
        startService(intent);
        
        updateStatus();
    }

    /**
     * 启动 VPN 服务
     */
    private void startVpnService() {
        Intent intent = new Intent(this, EchVpnService.class);
        intent.setAction(EchVpnService.ACTION_CONNECT);
        startService(intent);
        
        Toast.makeText(this, "VPN 启动中...", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    /**
     * 打开应用选择器
     */
    private void openAppSelector() {
        // 保存当前配置
        saveConfig();
        
        // 启动应用选择器
        Intent intent = new Intent(this, AppSelectorActivity.class);
        
        // 根据当前代理模式传入不同参数
        int checkedId = radioProxyMode.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_whitelist) {
            intent.putExtra(AppSelectorActivity.EXTRA_MODE, "whitelist");
            intent.putExtra(AppSelectorActivity.EXTRA_SELECTED_APPS,
                String.join(",", config.allowedApps));
        } else if (checkedId == R.id.radio_blacklist) {
            intent.putExtra(AppSelectorActivity.EXTRA_MODE, "blacklist");
            intent.putExtra(AppSelectorActivity.EXTRA_SELECTED_APPS,
                String.join(",", config.disallowedApps));
        } else {
            Toast.makeText(this, "请先选择白名单或黑名单模式", Toast.LENGTH_SHORT).show();
            return;
        }
        
        startActivityForResult(intent, REQUEST_SELECT_APPS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                Toast.makeText(this, "VPN 权限被拒绝", Toast.LENGTH_SHORT).show();
                switchVpn.setChecked(false);
            }
        } else if (requestCode == REQUEST_SELECT_APPS) {
            if (resultCode == RESULT_OK && data != null) {
                String selectedApps = data.getStringExtra(AppSelectorActivity.EXTRA_SELECTED_APPS);
                if (selectedApps != null) {
                    // 更新应用列表输入框
                    editAppList.setText(selectedApps);
                    
                    // 更新配置
                    int checkedId = radioProxyMode.getCheckedRadioButtonId();
                    if (checkedId == R.id.radio_whitelist) {
                        config.allowedApps.clear();
                        if (!selectedApps.isEmpty()) {
                            for (String app : selectedApps.split(",")) {
                                config.allowedApps.add(app.trim());
                            }
                        }
                    } else if (checkedId == R.id.radio_blacklist) {
                        config.disallowedApps.clear();
                        if (!selectedApps.isEmpty()) {
                            for (String app : selectedApps.split(",")) {
                                config.disallowedApps.add(app.trim());
                            }
                        }
                    }
                    
                    Toast.makeText(this, "已选择 " + config.allowedApps.size() +
                        config.disallowedApps.size() + " 个应用", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        
        // 同步开关状态
        switchVpn.setChecked(EchVpnService.isVpnRunning());
    }

    /**
     * 更新状态显示
     */
    private void updateStatus() {
        boolean isRunning = EchVpnService.isVpnRunning();
        
        if (isRunning) {
            textStatus.setText("状态: 运行中 ✓");
            textStatus.setTextColor(0xFF4CAF50); // 绿色
        } else {
            textStatus.setText("状态: 已停止");
            textStatus.setTextColor(0xFF757575); // 灰色
        }
    }
}

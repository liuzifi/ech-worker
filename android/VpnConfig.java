package com.echworker.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VPN 配置类
 * 用于存储和读取 VPN 连接配置
 */
public class VpnConfig {
    
    private static final String PREFS_NAME = "EchVpnConfig";
    
    // 配置键
    private static final String KEY_SERVER_ADDR = "server_addr";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DNS_SERVER = "dns_server";
    private static final String KEY_ECH_DOMAIN = "ech_domain";
    private static final String KEY_PROXY_MODE = "proxy_mode";
    private static final String KEY_ALLOWED_APPS = "allowed_apps";
    private static final String KEY_DISALLOWED_APPS = "disallowed_apps";
    
    // 代理模式常量
    public static final int MODE_GLOBAL = 0;     // 全局代理
    public static final int MODE_WHITELIST = 1;  // 白名单模式
    public static final int MODE_BLACKLIST = 2;  // 黑名单模式
    
    // 配置字段
    public String serverAddr;
    public String serverIP;
    public String token;
    public String dnsServer;
    public String echDomain;
    public int proxyMode;
    public List<String> allowedApps;
    public List<String> disallowedApps;
    
    public VpnConfig() {
        // 默认配置
        serverAddr = "";
        serverIP = "";
        token = "";
        dnsServer = "dns.alidns.com/dns-query";
        echDomain = "cloudflare-ech.com";
        proxyMode = MODE_GLOBAL;
        allowedApps = new ArrayList<>();
        disallowedApps = new ArrayList<>();
    }
    
    /**
     * 从 SharedPreferences 加载配置
     */
    public static VpnConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        VpnConfig config = new VpnConfig();
        
        config.serverAddr = prefs.getString(KEY_SERVER_ADDR, "");
        config.serverIP = prefs.getString(KEY_SERVER_IP, "");
        config.token = prefs.getString(KEY_TOKEN, "");
        config.dnsServer = prefs.getString(KEY_DNS_SERVER, "dns.alidns.com/dns-query");
        config.echDomain = prefs.getString(KEY_ECH_DOMAIN, "cloudflare-ech.com");
        config.proxyMode = prefs.getInt(KEY_PROXY_MODE, MODE_GLOBAL);
        
        String allowedStr = prefs.getString(KEY_ALLOWED_APPS, "");
        if (!allowedStr.isEmpty()) {
            config.allowedApps = new ArrayList<>(Arrays.asList(allowedStr.split(",")));
        }
        
        String disallowedStr = prefs.getString(KEY_DISALLOWED_APPS, "");
        if (!disallowedStr.isEmpty()) {
            config.disallowedApps = new ArrayList<>(Arrays.asList(disallowedStr.split(",")));
        }
        
        return config;
    }
    
    /**
     * 保存配置到 SharedPreferences
     */
    public void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString(KEY_SERVER_ADDR, serverAddr);
        editor.putString(KEY_SERVER_IP, serverIP);
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_DNS_SERVER, dnsServer);
        editor.putString(KEY_ECH_DOMAIN, echDomain);
        editor.putInt(KEY_PROXY_MODE, proxyMode);
        editor.putString(KEY_ALLOWED_APPS, String.join(",", allowedApps));
        editor.putString(KEY_DISALLOWED_APPS, String.join(",", disallowedApps));
        
        editor.apply();
    }
    
    /**
     * 获取应用列表字符串（逗号分隔）
     */
    public String getAppListString() {
        if (proxyMode == MODE_WHITELIST) {
            return String.join(",", allowedApps);
        } else if (proxyMode == MODE_BLACKLIST) {
            return String.join(",", disallowedApps);
        }
        return "";
    }
    
    /**
     * 验证配置是否完整
     */
    public boolean isValid() {
        return serverAddr != null && !serverAddr.isEmpty();
    }
}

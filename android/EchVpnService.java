package com.echworker.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

import main.Main; // gomobile 生成的 Go 绑定

/**
 * ECH Worker VPN 服务
 * 
 * 功能：
 * - 创建 VPN TUN 接口，捕获系统网络流量
 * - 支持全局代理和分应用代理（白名单/黑名单）
 * - 通过 tun2socks 将流量转发到本地 Go SOCKS5 代理 (127.0.0.1:1080)
 * - 显示前台通知，保持服务运行
 */
public class EchVpnService extends VpnService {

    private static final String TAG = "EchVpnService";
    
    // 服务动作
    public static final String ACTION_CONNECT = "com.echworker.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.echworker.vpn.DISCONNECT";
    
    // 通知相关
    private static final String NOTIFICATION_CHANNEL_ID = "EchVpnChannel";
    private static final int NOTIFICATION_ID = 1;
    
    // VPN 接口
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean isRunning = false;
    
    // VPN 状态
    private static volatile boolean vpnRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VPN Service 创建");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            startVpn();
        } else if (ACTION_DISCONNECT.equals(action)) {
            stopVpn();
        }

        return START_STICKY;
    }

    /**
     * 启动 VPN 连接
     */
    private void startVpn() {
        if (isRunning) {
            Log.w(TAG, "VPN 已在运行中");
            return;
        }

        Log.i(TAG, "启动 VPN...");

        // 从 SharedPreferences 读取配置
        VpnConfig config = VpnConfig.load(this);
        
        // 设置 Go 侧配置
        try {
            Main.setConfig(
                config.serverAddr,
                config.serverIP,
                config.token,
                config.dnsServer,
                config.echDomain
            );
            
            // 设置代理模式
            Main.setProxyMode(config.proxyMode, config.getAppListString());
            
        } catch (Exception e) {
            Log.e(TAG, "设置 Go 配置失败", e);
            showNotification("VPN 启动失败", "配置错误: " + e.getMessage());
            return;
        }

        // 启动 Go 代理服务
        try {
            Main.startVPN();
        } catch (Exception e) {
            Log.e(TAG, "启动 Go VPN 失败", e);
            showNotification("VPN 启动失败", e.getMessage());
            return;
        }

        // 建立 VPN 接口
        vpnInterface = establishVpn(config);
        if (vpnInterface == null) {
            Log.e(TAG, "建立 VPN 接口失败");
            Main.stopVPN();
            showNotification("VPN 启动失败", "无法建立 VPN 接口");
            return;
        }

        isRunning = true;
        vpnRunning = true;
        
        // 启动前台服务
        showNotification("ECH VPN 运行中", "代理已启动");

        // 启动 tun2socks（将 TUN 流量转为 SOCKS5）
        startTun2Socks();
        
        Log.i(TAG, "VPN 启动成功");
    }

    /**
     * 停止 VPN 连接
     */
    private void stopVpn() {
        if (!isRunning) {
            Log.w(TAG, "VPN 未运行");
            return;
        }

        Log.i(TAG, "停止 VPN...");

        isRunning = false;
        vpnRunning = false;

        // 停止 tun2socks
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }

        // 关闭 VPN 接口
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭 VPN 接口失败", e);
        }

        // 停止 Go 代理服务
        Main.stopVPN();

        // 停止前台服务
        stopForeground(true);
        stopSelf();

        Log.i(TAG, "VPN 已停止");
    }

    /**
     * 建立 VPN 接口
     */
    private ParcelFileDescriptor establishVpn(VpnConfig config) {
        Builder builder = new Builder();
        
        // 设置 VPN 参数
        builder.setSession("ECH Worker VPN")
               .setMtu(1500)
               .addAddress("10.0.0.2", 24) // VPN 虚拟 IP
               .addRoute("0.0.0.0", 0) // 路由所有流量
               .addDnsServer("8.8.8.8"); // DNS 服务器

        // 分应用代理配置
        if (config.proxyMode == VpnConfig.MODE_WHITELIST) {
            // 白名单模式: 只有指定应用走代理
            try {
                for (String pkg : config.allowedApps) {
                    builder.addAllowedApplication(pkg);
                }
            } catch (Exception e) {
                Log.e(TAG, "设置白名单应用失败", e);
            }
        } else if (config.proxyMode == VpnConfig.MODE_BLACKLIST) {
            // 黑名单模式: 指定应用不走代理
            try {
                for (String pkg : config.disallowedApps) {
                    builder.addDisallowedApplication(pkg);
                }
            } catch (Exception e) {
                Log.e(TAG, "设置黑名单应用失败", e);
            }
        }
        // MODE_GLOBAL (0): 全局代理，不设置应用过滤

        try {
            return builder.establish();
        } catch (Exception e) {
            Log.e(TAG, "建立 VPN 失败", e);
            return null;
        }
    }

    /**
     * 启动 tun2socks，将 TUN 流量转为 SOCKS5
     */
    private void startTun2Socks() {
        vpnThread = new Thread(() -> {
            try {
                // 这里需要集成 tun2socks 库
                // 示例使用 badvpn 或 outline-go-tun2socks
                // 将 vpnInterface.getFd() 的流量转发到 127.0.0.1:1080 (SOCKS5)
                
                Log.i(TAG, "tun2socks 启动中... (TUN -> SOCKS5:127.0.0.1:1080)");
                
                // TODO: 调用 tun2socks native 库
                // 示例: Tun2socks.start(vpnInterface.getFd(), "127.0.0.1:1080");
                
                while (isRunning && !Thread.interrupted()) {
                    Thread.sleep(1000);
                }
                
            } catch (InterruptedException e) {
                Log.i(TAG, "tun2socks 线程中断");
            } catch (Exception e) {
                Log.e(TAG, "tun2socks 运行错误", e);
            }
        });
        vpnThread.start();
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ECH VPN 服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("ECH Worker VPN 运行状态通知");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 显示前台通知
     */
    private void showNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 替换为自定义图标
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
        Log.d(TAG, "VPN Service 销毁");
    }

    /**
     * 查询 VPN 是否正在运行（供 Tile 使用）
     */
    public static boolean isVpnRunning() {
        return vpnRunning;
    }
}

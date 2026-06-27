package com.echworker.vpn;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;

/**
 * ECH Worker VPN 快捷开关 (Quick Settings Tile)
 * 
 * 功能：
 * - 点击开关可快速启动/停止 VPN 服务
 * - 显示当前 VPN 运行状态（运行中/已停止）
 * - 集成到 Android 下拉快捷设置面板
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class EchVpnTileService extends TileService {

    private static final String TAG = "EchVpnTile";

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Tile 显示时更新状态
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        // 检查 VPN 当前状态
        boolean isRunning = EchVpnService.isVpnRunning();
        
        if (isRunning) {
            // 停止 VPN
            stopVpnService();
        } else {
            // 启动 VPN
            startVpnService();
        }
        
        // 更新 Tile 状态
        updateTileState();
    }

    /**
     * 启动 VPN 服务
     */
    private void startVpnService() {
        Intent intent = new Intent(this, EchVpnService.class);
        intent.setAction(EchVpnService.ACTION_CONNECT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    /**
     * 停止 VPN 服务
     */
    private void stopVpnService() {
        Intent intent = new Intent(this, EchVpnService.class);
        intent.setAction(EchVpnService.ACTION_DISCONNECT);
        startService(intent);
    }

    /**
     * 更新 Tile 显示状态
     */
    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean isRunning = EchVpnService.isVpnRunning();
        
        if (isRunning) {
            // VPN 运行中
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("ECH VPN");
            tile.setSubtitle("运行中");
            tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_add)); // 替换为自定义图标
        } else {
            // VPN 已停止
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("ECH VPN");
            tile.setSubtitle("已停止");
            tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel));
        }
        
        tile.updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTileState();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }
}

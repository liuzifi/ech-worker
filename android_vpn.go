package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
)

// ======================== Android VPN / 快捷开关状态 ========================

var (
	// VPN 运行状态
	vpnRunning  int32 // 原子变量: 0=未运行, 1=运行中
	vpnMu       sync.Mutex
	vpnCancel   context.CancelFunc
	vpnListener net.Listener

	// 分应用代理配置
	proxyMode      int32  // 0=全局代理, 1=白名单, 2=黑名单
	allowedApps    string // 白名单: 允许走代理的应用包名,逗号分隔
	disallowedApps string // 黑名单: 不走代理的应用包名,逗号分隔

	// Android 回调: 通知 Java 层 VPN 状态变更
	onVPNStateChanged func(running bool)
)

// ======================== Android VPN 服务模式 ========================

// runAndroidVPNMode 以 Android VPN 服务模式运行代理
func runAndroidVPNMode() {
	androidAddr := "127.0.0.1:1080"

	ctx, cancel := context.WithCancel(context.Background())
	vpnCancel = cancel

	// 优雅关闭
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		select {
		case <-sigCh:
			log.Printf("[Android] 收到关闭信号")
			StopVPN()
		case <-ctx.Done():
		}
	}()

	log.Printf("[Android] VPN 代理模式启动: %s", androidAddr)
	runProxyServerWithContext(ctx, androidAddr)
}

// runProxyServerWithContext 带上下文的代理服务器，支持优雅关闭
func runProxyServerWithContext(ctx context.Context, addr string) {
	var err error
	vpnListener, err = net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("[代理] 监听失败: %v", err)
	}
	defer vpnListener.Close()

	log.Printf("[代理] 服务器启动: %s (支持 SOCKS5 和 HTTP)", addr)
	log.Printf("[代理] 后端服务器: %s", serverAddr)
	if serverIP != "" {
		log.Printf("[代理] 使用固定 IP: %s", serverIP)
	}

	// 标记为运行中
	atomic.StoreInt32(&vpnRunning, 1)
	notifyVPNState(true)

	// 等待上下文取消
	go func() {
		<-ctx.Done()
		log.Printf("[代理] 收到关闭信号，正在停止...")
		vpnListener.Close()
	}()

	for {
		conn, err := vpnListener.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				log.Printf("[代理] 服务器已关闭")
			default:
				log.Printf("[代理] 接受连接失败: %v", err)
			}
			break
		}

		go handleConnection(conn)
	}

	// 标记为已停止
	atomic.StoreInt32(&vpnRunning, 0)
	notifyVPNState(false)
	log.Printf("[代理] 服务器已停止")
}

// notifyVPNState 通知 Android 层 VPN 状态变更
func notifyVPNState(running bool) {
	state := "stopped"
	if running {
		state = "running"
	}
	log.Printf("[Android] VPN 状态变更: %s", state)
	if onVPNStateChanged != nil {
		onVPNStateChanged(running)
	}
}

// ======================== Android VPN 控制 API (gomobile 导出) ========================

// StartVPN 启动 VPN 代理服务（gomobile 导出，供 Android JNI 调用）
func StartVPN() error {
	vpnMu.Lock()
	defer vpnMu.Unlock()

	// 检查是否已在运行
	if atomic.LoadInt32(&vpnRunning) == 1 {
		return errors.New("VPN 已在运行中")
	}

	// 检查必要配置
	if serverAddr == "" {
		return errors.New("未配置服务端地址，请先调用 SetConfig")
	}

	// 获取 ECH 配置
	log.Printf("[VPN] 正在获取 ECH 配置...")
	if err := prepareECH(); err != nil {
		return fmt.Errorf("获取 ECH 配置失败: %w", err)
	}

	// 启动代理服务
	ctx, cancel := context.WithCancel(context.Background())
	vpnCancel = cancel

	go func() {
		runProxyServerWithContext(ctx, "127.0.0.1:1080")
	}()

	// 等待启动完成或超时
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if atomic.LoadInt32(&vpnRunning) == 1 {
			log.Printf("[VPN] 代理服务启动成功")
			return nil
		}
		time.Sleep(50 * time.Millisecond)
	}

	// 超时但可能仍在启动中，返回成功（后续通过 IsVPNRunning 检查）
	log.Printf("[VPN] 代理服务正在启动中...")
	return nil
}

// StopVPN 停止 VPN 代理服务（gomobile 导出，供 Android JNI 调用）
func StopVPN() {
	vpnMu.Lock()
	defer vpnMu.Unlock()

	if atomic.LoadInt32(&vpnRunning) == 0 {
		return
	}

	log.Printf("[VPN] 正在停止代理服务...")

	// 取消上下文
	if vpnCancel != nil {
		vpnCancel()
		vpnCancel = nil
	}

	// 关闭监听器
	if vpnListener != nil {
		vpnListener.Close()
		vpnListener = nil
	}

	atomic.StoreInt32(&vpnRunning, 0)
	notifyVPNState(false)
	log.Printf("[VPN] 代理服务已停止")
}

// IsVPNRunning 查询 VPN 是否正在运行（gomobile 导出，供 Android JNI 调用）
func IsVPNRunning() bool {
	return atomic.LoadInt32(&vpnRunning) == 1
}

// SetConfig 设置代理配置（gomobile 导出，供 Android JNI 调用）
func SetConfig(server, serverIPVal, authToken, dns, ech string) {
	if server != "" {
		serverAddr = server
	}
	if serverIPVal != "" {
		serverIP = serverIPVal
	}
	if authToken != "" {
		token = authToken
	}
	if dns != "" {
		dnsServer = dns
	}
	if ech != "" {
		echDomain = ech
	}
	log.Printf("[配置] server=%s, ip=%s, dns=%s, ech=%s",
		serverAddr, serverIP, dnsServer, echDomain)
}

// SetProxyMode 设置代理模式（gomobile 导出，供 Android JNI 调用）
// mode: 0=全局代理, 1=白名单模式, 2=黑名单模式
// apps: 应用包名列表，逗号分隔
func SetProxyMode(mode int, apps string) {
	atomic.StoreInt32(&proxyMode, int32(mode))
	switch mode {
	case 0:
		log.Printf("[代理模式] 全局代理")
	case 1:
		allowedApps = apps
		log.Printf("[代理模式] 白名单: %s", apps)
	case 2:
		disallowedApps = apps
		log.Printf("[代理模式] 黑名单: %s", apps)
	}
}

// GetProxyMode 获取当前代理模式（gomobile 导出）
func GetProxyMode() int {
	return int(atomic.LoadInt32(&proxyMode))
}

// GetAllowedApps 获取白名单应用列表（gomobile 导出）
func GetAllowedApps() string {
	return allowedApps
}

// GetDisallowedApps 获取黑名单应用列表（gomobile 导出）
func GetDisallowedApps() string {
	return disallowedApps
}

// SetVPNStateCallback 设置 VPN 状态变更回调（gomobile 导出）
func SetVPNStateCallback(cb func(running bool)) {
	onVPNStateChanged = cb
}

// RefreshECHConfig 手动刷新 ECH 配置（gomobile 导出）
func RefreshECHConfig() error {
	return refreshECH()
}

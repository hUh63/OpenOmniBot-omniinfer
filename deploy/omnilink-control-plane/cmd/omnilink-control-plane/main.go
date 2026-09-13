package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/omnimind/omnilink-control-plane/internal/controlplane"
)

func main() {
	config, valkeyConfig, listenAddress, trustProxy, err := loadConfig()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(2)
	}
	store, err := controlplane.NewValkeyStore(valkeyConfig)
	if err != nil {
		slog.Error("create Valkey store", "error", err)
		os.Exit(2)
	}
	service, err := controlplane.NewService(config, store)
	if err != nil {
		_ = store.Close()
		slog.Error("create control-plane service", "error", err)
		os.Exit(2)
	}
	defer func() {
		if err := service.Close(); err != nil {
			slog.Error("close control-plane service", "error", err)
		}
	}()

	server := &http.Server{
		Addr: listenAddress,
		Handler: controlplane.NewHandler(
			service,
			controlplane.HandlerConfig{TrustProxyHeaders: trustProxy},
		),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 * 1024,
	}
	go func() {
		slog.Info("OmniLink control plane listening", "address", listenAddress)
		if err := server.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
			slog.Error("control plane stopped unexpectedly", "error", err)
			os.Exit(1)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownContext); err != nil {
		slog.Error("graceful shutdown failed", "error", err)
	}
}

func loadConfig() (
	controlplane.Config,
	controlplane.ValkeyConfig,
	string,
	bool,
	error,
) {
	relayPort, err := envInt("OMNILINK_RELAY_SERVER_PORT", 7000)
	if err != nil {
		return controlplane.Config{}, controlplane.ValkeyConfig{}, "", false, err
	}
	publicPort, err := envInt("OMNILINK_RELAY_PUBLIC_PORT", 443)
	if err != nil {
		return controlplane.Config{}, controlplane.ValkeyConfig{}, "", false, err
	}
	valkeyDatabase, err := envInt("OMNILINK_VALKEY_DATABASE", 0)
	if err != nil {
		return controlplane.Config{}, controlplane.ValkeyConfig{}, "", false, err
	}
	trustProxy, err := envBool("OMNILINK_TRUST_PROXY_HEADERS", false)
	if err != nil {
		return controlplane.Config{}, controlplane.ValkeyConfig{}, "", false, err
	}
	valkeyTLS, err := envBool("OMNILINK_VALKEY_TLS", false)
	if err != nil {
		return controlplane.Config{}, controlplane.ValkeyConfig{}, "", false, err
	}
	return controlplane.Config{
			AdminToken:         os.Getenv("OMNILINK_ADMIN_TOKEN"),
			HashKey:            os.Getenv("OMNILINK_HASH_KEY"),
			FRPSharedToken:     os.Getenv("OMNILINK_FRP_SHARED_TOKEN"),
			RelayServerHost:    os.Getenv("OMNILINK_RELAY_SERVER_HOST"),
			RelayServerPort:    relayPort,
			RelayPublicDomain:  os.Getenv("OMNILINK_RELAY_PUBLIC_DOMAIN"),
			RelayPublicPort:    publicPort,
			RelayTLSServerName: os.Getenv("OMNILINK_RELAY_TLS_SERVER_NAME"),
			RendezvousBaseURL:  os.Getenv("OMNILINK_RENDEZVOUS_BASE_URL"),
		}, controlplane.ValkeyConfig{
			Address:       envOrDefault("OMNILINK_VALKEY_ADDRESS", "valkey:6379"),
			Username:      os.Getenv("OMNILINK_VALKEY_USERNAME"),
			Password:      os.Getenv("OMNILINK_VALKEY_PASSWORD"),
			Database:      valkeyDatabase,
			Prefix:        envOrDefault("OMNILINK_VALKEY_PREFIX", "omnilink:"),
			TLS:           valkeyTLS,
			TLSServerName: os.Getenv("OMNILINK_VALKEY_TLS_SERVER_NAME"),
		}, envOrDefault("OMNILINK_LISTEN_ADDRESS", ":8080"), trustProxy, nil
}

func envInt(name string, fallback int) (int, error) {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback, nil
	}
	return strconv.Atoi(value)
}

func envBool(name string, fallback bool) (bool, error) {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback, nil
	}
	return strconv.ParseBool(value)
}

func envOrDefault(name string, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

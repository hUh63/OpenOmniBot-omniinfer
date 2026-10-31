import { useEffect, useRef, useState } from "react";
import { eventsUrl, notifyUnauthorized } from "../api";
import type {
  ConnectionStatus,
  RealtimeEventData,
  RealtimeEventName,
} from "../types";

const EVENT_NAMES: RealtimeEventName[] = [
  "conversation_created",
  "conversation_updated",
  "conversation_deleted",
  "messages_replaced",
  "acp_event",
  "chat_task_event",
  "browser_snapshot_updated",
  "workspace_changed",
];

export function useRealtime(
  enabled: boolean,
  onEvent: (name: RealtimeEventName, data: RealtimeEventData) => void,
): ConnectionStatus {
  const handlerRef = useRef(onEvent);
  const [status, setStatus] = useState<ConnectionStatus>("connecting");

  useEffect(() => {
    handlerRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!enabled) {
      setStatus("connecting");
      return undefined;
    }
    const source = new EventSource(eventsUrl());
    setStatus("connecting");
    source.onopen = () => setStatus("online");
    let probing = false;
    // EventSource 拿不到 HTTP 状态码：MCP 服务重启 / session 过期（401）时它只会每
    // 3 秒无限重连，页面停在「已在线」而收不到任何事件，只有用户点按钮才报错。这里在
    // error 时主动探一次连接状态，401 直接交给集中处理回登录页。
    const probeSession = async (): Promise<void> => {
      if (probing) return;
      probing = true;
      const controller = new AbortController();
      const timer = window.setTimeout(() => controller.abort(), 3000);
      try {
        const response = await fetch(eventsUrl(), {
          method: "GET",
          signal: controller.signal,
          headers: { Accept: "text/event-stream" },
        });
        if (response.status === 401) {
          source.close();
          notifyUnauthorized();
          return;
        }
        // 非 401：这条只是探针，立刻断掉，真正的连接交给 EventSource 自己重连。
        controller.abort();
      } catch {
        // 网络不可达等：保持原样，让 EventSource 自己重试。
      } finally {
        window.clearTimeout(timer);
        probing = false;
      }
    };
    source.onerror = () => {
      setStatus("offline");
      void probeSession();
    };

    const listeners = EVENT_NAMES.map((name) => {
      const listener = (event: MessageEvent<string>) => {
        try {
          handlerRef.current(name, JSON.parse(event.data) as RealtimeEventData);
        } catch {
          // Ignore malformed server events and keep the realtime channel alive.
        }
      };
      source.addEventListener(name, listener as EventListener);
      return [name, listener] as const;
    });

    return () => {
      listeners.forEach(([name, listener]) => {
        source.removeEventListener(name, listener as EventListener);
      });
      source.close();
    };
  }, [enabled]);

  return status;
}

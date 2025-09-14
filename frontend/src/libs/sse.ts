// src/libs/sse.ts
export type SSEMessage<T = any> = {
  id?: string;
  event?: string;
  data: T;
};

export class SSEClient {
  private url: string;
  private controller?: AbortController;
  private lastEventId?: string;

  constructor(url: string) {
    this.url = url;
  }

  // SSE 연결 시작
  async connect(onMessage: (msg: SSEMessage) => void) {
    this.controller = new AbortController();

    try {
      const res = await fetch(this.url, {
        method: "GET",
        headers: this.lastEventId ? { "Last-Event-ID": this.lastEventId } : {},
        signal: this.controller.signal,
      });

      if (!res.ok || !res.body) {
        throw new Error(`SSE connection failed: ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      // 스트림 읽기 루프
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // 이벤트 단위로 파싱
        let parts = buffer.split(/\r?\n\r?\n/);
        buffer = parts.pop() ?? ""; // 마지막은 incomplete → 버퍼에 남김

        for (const part of parts) {
          const lines = part.split(/\r?\n/);
          let id: string | undefined;
          let event = "message";
          const dataLines: string[] = [];

          for (const line of lines) {
            if (line.startsWith("id:")) {
              id = line.slice(3).trim();
            } else if (line.startsWith("event:")) {
              event = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              dataLines.push(line.slice(5).trim());
            }
          }

          const data = dataLines.join("\n");

          try {
            onMessage({
              id,
              event,
              data: JSON.parse(data),
            });
          } catch {
            onMessage({ id, event, data });
          }

          // ✅ 이벤트 처리 완료 후에만 lastEventId 갱신
          if (id) this.lastEventId = id;
        }
      }
    } catch (err) {
      console.warn("SSE error, retrying in 3s", err);
      this.disconnect();
      setTimeout(() => this.connect(onMessage), 3000); // 재연결
    }
  }

  disconnect() {
    if (this.controller) {
      this.controller.abort();
      this.controller = undefined;
    }
  }
}

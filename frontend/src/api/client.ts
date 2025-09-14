function makeClient(base: string) {
  const API_BASE = (base ?? "").replace(/\/+$/, "");

  async function http<T>(path: string, init?: RequestInit): Promise<T> {
    const url = `${API_BASE}${path.startsWith("/") ? path : `/${path}`}`;

    // ✅ Content-Type 기본값 유지 + init.headers 병합
    const headers = {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    };

    const res = await fetch(url, {
      ...init,
      headers, // 병합된 headers 사용
    });

    const ct = res.headers.get("content-type") ?? "";
    if (!res.ok) {
      let body: any = null;
      try {
        body = ct.includes("application/json") ? await res.json() : await res.text();
      } catch {}
      const err: any = new Error(`HTTP ${res.status}`);
      err.status = res.status;
      err.body = body;
      err.code = typeof body === "object" && body?.code ? body.code : undefined;
      throw err;
    }

    return ct.includes("application/json") ? (res.json() as Promise<T>) : (undefined as T);
  }


  return {
    get:    <T>(path: string) => http<T>(path),
    post: <T>(path: string, body: any, init?: RequestInit) =>
      http<T>(path, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
        body: JSON.stringify(body),
        ...init,
      }),
    put:    <T>(path: string, body: any) => http<T>(path, { method: "PUT", body: JSON.stringify(body) }),
    patch:  <T>(path: string, body: any) => http<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
    delete: <T>(path: string) => http<T>(path, { method: "DELETE" }),
  };
}

export const catalogApi = makeClient(import.meta.env.VITE_CATALOG_API);
export const reservationApi = makeClient(import.meta.env.VITE_RESERVATION_API);
export const orderApi = makeClient(import.meta.env.VITE_ORDER_API);

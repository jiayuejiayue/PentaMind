/**
 * PentaMind - content/network-hook.js
 * 注入到页面主线程 (world: MAIN)，Hook 原生 XMLHttpRequest 和 fetch
 * 用途：完整捕获请求 URL / Method / Headers / Body（JSON、FormData 等）
 *
 * 执行时机：document_start（页面 JS 执行前注入，确保先于业务代码拦截）
 */

(function () {
    'use strict';

    // 防止重复注入
    if (window.__PENTAMIND_NET_HOOKED__) return;
    window.__PENTAMIND_NET_HOOKED__ = true;

    /* ============================================================
     * 工具：将请求数据安全地序列化并发往 Extension 环境
     * ============================================================ */
    function emitRequest(data) {
        // window.postMessage 跨越 MAIN <-> ISOLATED 边界
        window.postMessage(
            { __pentamind__: true, type: 'NET_REQUEST', payload: data },
            '*'
        );
    }

    function safeStr(val) {
        if (val === undefined || val === null) return null;
        try { return String(val).slice(0, 4096); } catch { return null; }
    }

    /**
     * 尝试将 body 序列化为可读字符串
     */
    async function serializeBody(body) {
        if (!body) return null;
        try {
            if (typeof body === 'string') return body.slice(0, 4096);
            if (body instanceof URLSearchParams) return body.toString().slice(0, 4096);
            if (body instanceof FormData) {
                const obj = {};
                body.forEach((v, k) => { obj[k] = typeof v === 'string' ? v : '[File]'; });
                return JSON.stringify(obj).slice(0, 4096);
            }
            if (body instanceof Blob) {
                const text = await body.text();
                return text.slice(0, 4096);
            }
            if (body instanceof ArrayBuffer || ArrayBuffer.isView(body)) {
                return '[Binary]';
            }
            return safeStr(body);
        } catch {
            return '[SerializeError]';
        }
    }

    /* ============================================================
     * Hook XMLHttpRequest
     * ============================================================ */
    const OriginalXHR = window.XMLHttpRequest;

    class PentaMindXHR extends OriginalXHR {
        constructor() {
            super();
            this._pm = { method: 'GET', url: null, reqHeaders: {}, startTime: null };
        }

        open(method, url, ...rest) {
            this._pm.method = (method || 'GET').toUpperCase();
            this._pm.url = safeStr(url);
            this._pm.startTime = Date.now();
            return super.open(method, url, ...rest);
        }

        setRequestHeader(name, value) {
            this._pm.reqHeaders[name] = safeStr(value);
            return super.setRequestHeader(name, value);
        }

        send(body) {
            const pm = this._pm;

            // 异步序列化 body 后发送
            serializeBody(body).then((serializedBody) => {
                this.addEventListener('readystatechange', () => {
                    if (this.readyState === OriginalXHR.DONE) {
                        const resHeaders = {};
                        // 解析响应头
                        const rawHeaders = this.getAllResponseHeaders() || '';
                        rawHeaders.trim().split(/[\r\n]+/).forEach((line) => {
                            const [k, ...vParts] = line.split(': ');
                            if (k) resHeaders[k.toLowerCase().trim()] = vParts.join(': ').slice(0, 512);
                        });

                        emitRequest({
                            source: 'XHR',
                            method: pm.method,
                            url: pm.url,
                            requestHeaders: pm.reqHeaders,
                            requestBody: serializedBody,
                            statusCode: this.status,
                            responseHeaders: resHeaders,
                            responseBody: safeStr(this.responseText),
                            duration: Date.now() - pm.startTime,
                            timestamp: pm.startTime,
                        });
                    }
                });
            });

            return super.send(body);
        }
    }

    window.XMLHttpRequest = PentaMindXHR;

    /* ============================================================
     * Hook fetch
     * ============================================================ */
    const originalFetch = window.fetch;

    window.fetch = async function (input, init = {}) {
        const startTime = Date.now();
        let url, method, reqHeaders = {}, reqBody = null;

        if (input instanceof Request) {
            url = input.url;
            method = (input.method || 'GET').toUpperCase();
            input.headers.forEach((v, k) => { reqHeaders[k] = v.slice(0, 512); });
            // 克隆以避免 body 被消费
            reqBody = await serializeBody(await input.clone().text().catch(() => null));
        } else {
            url = safeStr(input);
            method = ((init.method) || 'GET').toUpperCase();
            if (init.headers) {
                const h = new Headers(init.headers);
                h.forEach((v, k) => { reqHeaders[k] = v.slice(0, 512); });
            }
            reqBody = await serializeBody(init.body);
        }

        try {
            const response = await originalFetch(input, init);
            const clone = response.clone();

            // 异步读取响应体（不阻塞主流程）
            clone.text().then((resText) => {
                const resHeaders = {};
                response.headers.forEach((v, k) => { resHeaders[k] = v.slice(0, 512); });

                emitRequest({
                    source: 'FETCH',
                    method,
                    url,
                    requestHeaders: reqHeaders,
                    requestBody: reqBody,
                    statusCode: response.status,
                    responseHeaders: resHeaders,
                    responseBody: safeStr(resText),
                    duration: Date.now() - startTime,
                    timestamp: startTime,
                });
            }).catch(() => { });

            return response;
        } catch (err) {
            // 网络错误也记录
            emitRequest({
                source: 'FETCH',
                method, url,
                requestHeaders: reqHeaders,
                requestBody: reqBody,
                statusCode: 0,
                responseHeaders: {},
                responseBody: null,
                error: String(err),
                duration: Date.now() - startTime,
                timestamp: startTime,
            });
            throw err;
        }
    };
})();

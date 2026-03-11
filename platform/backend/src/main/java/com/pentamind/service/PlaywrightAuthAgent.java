package com.pentamind.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * 基于 Playwright 的无头会话维持与智能探针
 * <p>
 * 解决传统基于 HttpClient/Jsoup 的扫描器无法执行 JS，无法自动提取弹窗 Token 等痛点。
 */
@Slf4j
@Service
public class PlaywrightAuthAgent {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    @PostConstruct
    public void init() {
        log.info("【Playwright探针】正在后台启动无头浏览器容器...");
        // 在后台常驻一个 Chromium 实例，提升后续探测的速度
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            context = browser.newContext(new Browser.NewContextOptions()
                    .setIgnoreHTTPSErrors(true) // 渗透测试时强制接受失效证书
            );
            log.info("【Playwright探针】Chromium内核启动成功.");
        } catch (Exception e) {
            log.error("【Playwright探针】启动浏览器内核时发生错误，请检查是否缺少系统依赖: {}", e.getMessage(), e);
        }
    }

    /**
     * 使用真实浏览器访问目标，深度提取页面功能特征供 LLM 分析
     *
     * @param targetUrl 目标的完整地址
     * @return 结构化的页面特征描述（供 GLM-5 做攻击面分析）
     */
    public String smartNavigateAndDetect(String targetUrl) {
        if (browser == null) {
            return "【Playwright未就绪】无头浏览器探针尚未启动，可能缺少系统运行库。";
        }

        log.info("【Playwright探针】派遣智能爬虫前往侦察: {}", targetUrl);
        StringBuilder features = new StringBuilder();

        try (Page page = context.newPage()) {
            // 1. 拦截网络请求，收集 AJAX/API 接口
            final java.util.Set<String> apiRequests = java.util.Collections
                    .synchronizedSet(new java.util.LinkedHashSet<>());
            page.onRequest(request -> {
                String url = request.url();
                String method = request.method();
                // 过滤静态资源，只收集 API 类请求
                if (!url.contains(".css") && !url.contains(".js") && !url.contains(".png")
                        && !url.contains(".jpg") && !url.contains(".ico") && !url.contains(".woff")
                        && !url.equals(targetUrl)) {
                    apiRequests.add("[" + method + "] " + url);
                }
            });

            // 2. 导航到目标，等待 JS 执行完毕
            page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            // 3. 基础信息
            features.append("=== 页面基础信息 ===\n");
            features.append("标题: ").append(page.title()).append("\n");
            features.append("最终URL: ").append(page.url()).append("\n");
            if (!page.url().equals(targetUrl)) {
                features.append("⚠️ 发生重定向: ").append(targetUrl).append(" → ").append(page.url()).append("\n");
            }

            // 4. 提取所有表单
            features.append("\n=== 表单输入点（高价值攻击面）===\n");
            long formCount = ((Number) page.evaluate("document.querySelectorAll('form').length")).intValue();
            features.append("表单数量: ").append(formCount).append("\n");
            if (formCount > 0) {
                // 提取每个表单的 action、method 和所有 input
                String formInfo = (String) page.evaluate("""
                        JSON.stringify(Array.from(document.querySelectorAll('form')).map(f => ({
                            action: f.action || '(无action)',
                            method: f.method || 'GET',
                            inputs: Array.from(f.querySelectorAll('input,select,textarea')).map(i => ({
                                name: i.name || i.id || '(匿名)',
                                type: i.type || 'text',
                                placeholder: i.placeholder || ''
                            }))
                        })))
                        """);
                features.append("表单详情: ").append(formInfo).append("\n");
            }

            // 5. 重要输入类型特征
            features.append("\n=== 关键功能点识别 ===\n");
            if (((Number) page.evaluate("document.querySelectorAll('input[type=password]').length")).intValue() > 0)
                features.append("✅ 检测到登录/密码输入框（弱口令、绕过攻击面）\n");
            if (((Number) page.evaluate("document.querySelectorAll('input[type=file]').length")).intValue() > 0)
                features.append("✅ 检测到文件上传点（文件上传绕过攻击面）\n");
            if (((Number) page.evaluate(
                    "document.querySelectorAll('input[type=search], input[name*=search], input[name*=query], input[name*=keyword]').length"))
                    .intValue() > 0)
                features.append("✅ 检测到搜索/查询输入框（SQLi/XSS攻击面）\n");

            // 6. URL 参数分析
            features.append("\n=== URL 参数分析 ===\n");
            features.append("原始URL参数: ")
                    .append(targetUrl.contains("?") ? targetUrl.substring(targetUrl.indexOf('?') + 1) : "(无参数)")
                    .append("\n");

            // 7. 动态捕获的 AJAX/API 接口
            features.append("\n=== 动态捕获的 API 接口（AJAX/Fetch）===\n");
            if (apiRequests.isEmpty()) {
                features.append("未捕获到异步 API 请求\n");
            } else {
                apiRequests.stream().limit(20).forEach(r -> features.append(r).append("\n"));
            }

            // 8. 页面 HTML 关键标签提取（框架识别）
            String metaGenerator = (String) page.evaluate(
                    "document.querySelector('meta[name=generator]')?.content || ''");
            if (!metaGenerator.isEmpty())
                features.append("\n=== 技术指纹 ===\nmeta generator: ").append(metaGenerator).append("\n");

            String pageContent = page.content();
            // 从 HTML 关键词识别框架
            features.append(detectFramework(pageContent, page.url()));

            log.info("【Playwright探针】特征提取完成，特征长度={}", features.length());
            return features.toString();

        } catch (Exception e) {
            log.error("【Playwright探针】抓取分析目标 {} 时发生错误: {}", targetUrl, e.getMessage());
            return "【爬取失败】目标 " + targetUrl + " 无法访问或被 WAF 阻断，错误信息: " + e.getMessage();
        }
    }

    /** 从页面内容识别常见 Web 框架特征 */
    private String detectFramework(String html, String url) {
        StringBuilder sb = new StringBuilder("\n=== 框架/技术特征（从页面内容推断）===\n");
        String h = html.toLowerCase();
        if (h.contains("thinkphp") || h.contains("think\\"))
            sb.append("检测到 ThinkPHP 框架特征\n");
        if (h.contains("laravel") || h.contains("_token"))
            sb.append("检测到 Laravel 框架特征\n");
        if (h.contains("django") || h.contains("csrfmiddlewaretoken"))
            sb.append("检测到 Django 框架特征\n");
        if (h.contains("spring") || h.contains("jsessionid"))
            sb.append("检测到 Spring/Java 框架特征\n");
        if (h.contains("wp-content") || h.contains("wordpress"))
            sb.append("检测到 WordPress CMS\n");
        if (h.contains("joomla"))
            sb.append("检测到 Joomla CMS\n");
        if (h.contains("vue") || h.contains("v-app") || h.contains("__vue"))
            sb.append("检测到 Vue.js 前端框架\n");
        if (h.contains("react") || h.contains("__react"))
            sb.append("检测到 React 前端框架\n");
        if (h.contains("angular"))
            sb.append("检测到 Angular 前端框架\n");
        if (url.contains(".php"))
            sb.append("URL 包含 .php 后缀，推断后端为 PHP\n");
        if (url.contains(".jsp"))
            sb.append("URL 包含 .jsp 后缀，推断后端为 Java/JSP\n");
        if (url.contains(".asp"))
            sb.append("URL 包含 .asp/.aspx 后缀，推断后端为 ASP.NET\n");
        return sb.toString();
    }

    /**
     * 临时提取存储在该 Context 下的全部会话 Cookie，交给 ToolManager 下放给漏扫工具
     */
    public String exportSessionCookies() {
        if (context == null)
            return "";

        List<Cookie> cookies = context.cookies();
        if (cookies.isEmpty())
            return "";

        StringBuilder cookieStr = new StringBuilder();
        for (Cookie c : cookies) {
            cookieStr.append(c.name).append("=").append(c.value).append("; ");
        }
        return cookieStr.toString();
    }

    @PreDestroy
    public void cleanup() {
        log.info("【Playwright探针】正在销毁持久化浏览器实例...");
        if (context != null)
            context.close();
        if (browser != null)
            browser.close();
        if (playwright != null)
            playwright.close();
    }
}

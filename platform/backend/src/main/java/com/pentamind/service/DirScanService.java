package com.pentamind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 目录扫描并行服务
 * 同时调用 Gobuster 和 Dirsearch，结果合并去重后返回
 */
@Slf4j
@Service
public class DirScanService {

    @Autowired
    private ToolManager toolManager;

    @Value("${pentamind.tools-dir:tools/}")
    private String toolsBasePath;

    // 专用线程池：最多 4 个扫描线程并发
    private final ExecutorService executor = Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "DirScan-Worker");
                t.setDaemon(true);
                return t;
            });

    /**
     * 并行运行 Gobuster + Dirsearch，结果去重合并
     *
     * @param target     目标 URL
     * @param wordlist   字典逻辑名（common/big/api）或绝对路径
     * @param extensions 文件扩展名，逗号分隔
     * @return 去重后的合并原始输出
     */
    public String scanParallel(String target, String wordlist, String extensions) {
        String wordlistPath = resolveWordlist(wordlist);
        String ext = (extensions != null && !extensions.isBlank()) ? extensions : "php,html,bak,txt,js,json,xml";

        log.info("【目录扫描】启动并行扫描 target={} wordlist={} ext={}", target, wordlistPath, ext);

        // Gobuster 任务
        CompletableFuture<String> gobusterFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("target", target);
                args.put("wordlist", wordlistPath);
                args.put("extensions", ext);
                String result = toolManager.executeTool("gobuster_dir", args);
                log.info("【目录扫描】Gobuster 完成，输出 {} 字节", result.length());
                return result;
            } catch (Exception e) {
                log.warn("【目录扫描】Gobuster 执行失败: {}", e.getMessage());
                return "# Gobuster 执行失败: " + e.getMessage();
            }
        }, executor);

        // Dirsearch 任务
        CompletableFuture<String> dirsearchFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("target", target);
                args.put("extensions", ext);
                args.put("threads", "20");
                String result = toolManager.executeTool("dirsearch_dir", args);
                log.info("【目录扫描】Dirsearch 完成，输出 {} 字节", result.length());
                return result;
            } catch (Exception e) {
                log.warn("【目录扫描】Dirsearch 执行失败: {}", e.getMessage());
                return "# Dirsearch 执行失败: " + e.getMessage();
            }
        }, executor);

        // 等待全部完成（超时 10 分钟）
        String gobusterOut = "";
        String dirsearchOut = "";
        try {
            gobusterOut = gobusterFuture.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("【目录扫描】Gobuster 超时或异常: {}", e.getMessage());
        }
        try {
            dirsearchOut = dirsearchFuture.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("【目录扫描】Dirsearch 超时或异常: {}", e.getMessage());
        }

        // 合并+去重
        return mergeAndDeduplicate(target, gobusterOut, dirsearchOut);
    }

    /**
     * 将两个工具的输出合并去重
     * 去重规则：提取路径部分（不含协议和域名），忽略大小写，相同路径只保留第一次出现
     */
    private String mergeAndDeduplicate(String target, String gobusterOut, String dirsearchOut) {
        // key=规范化路径, value=原始行
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();

        // 解析 Gobuster 输出：格式 "/path (Status: 200) [Size: 1234]"
        parseGobuster(gobusterOut, seen);
        // 解析 Dirsearch 输出：格式 "200 1234B /path"
        parseDirsearch(dirsearchOut, seen);

        int total = seen.size();
        log.info("【目录扫描】合并去重完成，共 {} 条唯一路径", total);

        StringBuilder merged = new StringBuilder();
        merged.append("=== 目录扫描并行结果（Gobuster + Dirsearch 去重合并，共 ").append(total).append(" 条）===\n\n");

        // 按状态码分类输出
        List<String> results = new ArrayList<>(seen.values());
        // 先输出高价值发现（200/301/302）
        merged.append("--- 可访问路径 (2xx/3xx) ---\n");
        results.stream().filter(l -> l.contains("200") || l.contains("301") || l.contains("302"))
                .forEach(l -> merged.append(l).append("\n"));

        merged.append("\n--- 禁止访问但存在 (403) ---\n");
        results.stream().filter(l -> l.contains("403"))
                .forEach(l -> merged.append(l).append("\n"));

        merged.append("\n--- 其他发现 ---\n");
        results.stream().filter(l -> !l.contains("200") && !l.contains("301")
                && !l.contains("302") && !l.contains("403"))
                .forEach(l -> merged.append(l).append("\n"));

        return merged.toString();
    }

    /** 解析 Gobuster 输出 */
    private void parseGobuster(String output, Map<String, String> seen) {
        if (output == null || output.isBlank())
            return;
        Pattern p = Pattern.compile("(/[^\\s]*)\\s+\\(Status:\\s*(\\d+)\\)");
        for (String line : output.split("\n")) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String path = m.group(1).toLowerCase();
                seen.putIfAbsent(path, "[Gobuster] " + line.trim());
            }
        }
    }

    /** 解析 Dirsearch 输出 */
    private void parseDirsearch(String output, Map<String, String> seen) {
        if (output == null || output.isBlank())
            return;
        // 匹配格式例如: [12:34:56] 200 - 12KB - /admin
        // 或者: [12:34:56] 301 - 300B -> http://target.com/admin/
        Pattern p = Pattern.compile("\\]\\s+(\\d{3})\\s+-\\s+(?:[^\\-]*?)\\s+[->]+\\s*([^\\s]+)");
        for (String line : output.split("\n")) {
            if (line.startsWith("#") || line.isBlank())
                continue;
            Matcher m = p.matcher(line);
            if (m.find()) {
                // String code = m.group(1);
                String pathOrUrl = m.group(2).toLowerCase();
                try {
                    // 如果重定向到了全路径，提取出 URI 路径部分用于去重
                    if (pathOrUrl.startsWith("http")) {
                        pathOrUrl = new java.net.URL(pathOrUrl).getPath();
                    }
                } catch (Exception ignored) {
                }
                // 如果为空，补齐 /
                if (pathOrUrl.isEmpty())
                    pathOrUrl = "/";

                seen.putIfAbsent(pathOrUrl, "[Dirsearch] " + line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "").trim());
            }
        }
    }

    /** 将逻辑字典名（common/big/api）解析为绝对路径 */
    private String resolveWordlist(String wordlist) {
        String wordlistDir = toolsBasePath + "wordlists/";
        if (wordlist == null || wordlist.isBlank())
            return wordlistDir + "common.txt";
        return switch (wordlist.toLowerCase()) {
            case "common" -> wordlistDir + "common.txt";
            case "big" -> wordlistDir + "big.txt";
            case "api" -> wordlistDir + "api.txt";
            default -> wordlist; // 如果传的是绝对路径，直接使用
        };
    }
}

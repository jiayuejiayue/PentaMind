import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 路由配置 - 对应渗透测试管理平台的完整菜单结构
 * meta.title: 菜单显示标题
 * meta.icon: Element Plus 图标组件名
 * meta.badge: 徽章数字（可选）
 */
const routes = [
    {
        path: '/redirect',
        component: Layout,
        children: [
            { path: '/redirect/:path(.*)', component: () => import('@/views/redirect.vue') },
        ],
        meta: { hidden: true },
    },
    {
        path: '/login',
        component: () => import('@/views/login.vue'),
        meta: { hidden: true },
    },
    {
        path: '/',
        component: Layout,
        redirect: '/dashboard/overview',
        meta: { title: '仪表盘', icon: 'DataBoard' },
        children: [
            {
                path: 'dashboard/overview',
                name: 'DashboardOverview',
                component: () => import('@/views/dashboard/overview.vue'),
                meta: { title: '概览统计', icon: 'PieChart' },
            },
            {
                path: 'dashboard/realtime',
                name: 'DashboardRealtime',
                component: () => import('@/views/dashboard/realtime.vue'),
                meta: { title: '实时态势', icon: 'Monitor' },
            },
            {
                path: 'dashboard/analysis',
                name: 'DashboardAnalysis',
                component: () => import('@/views/dashboard/analysis.vue'),
                meta: { title: '系统分析', icon: 'Share' },
            },
        ],
    },
    {
        path: '/agent',
        component: Layout,
        redirect: '/agent/penetration',
        meta: { title: 'AI 渗透', icon: 'Cpu' },
        children: [
            {
                path: 'penetration',
                name: 'AgentPenetration',
                component: () => import('@/views/agent/penetration.vue'),
                meta: { title: '自动化渗透', icon: 'MagicStick' },
            }
        ]
    },
    {
        path: '/recon',
        component: Layout,
        redirect: '/recon/targets',
        meta: { title: '信息收集', icon: 'Search' },
        children: [
            {
                path: 'targets',
                name: 'ReconTargets',
                component: () => import('@/views/recon/targets.vue'),
                meta: { title: '目标管理', icon: 'Aim' },
            },
            {
                path: 'assets',
                name: 'ReconAssets',
                component: () => import('@/views/recon/assets.vue'),
                meta: { title: '目录扫描', icon: 'FolderOpened' },
            },
            {
                path: 'ports',
                name: 'ReconPorts',
                component: () => import('@/views/recon/ports.vue'),
                meta: { title: '端口扫描', icon: 'Connection' },
            },
            {
                path: 'subdomains',
                name: 'ReconSubdomains',
                component: () => import('@/views/recon/subdomains.vue'),
                meta: { title: '子域名收集', icon: 'Share' },
            },
        ],
    },
    {
        path: '/detection',
        component: Layout,
        redirect: '/detection/page-scan',
        meta: { title: '功能点检测', icon: 'Aim' },
        children: [
            {
                path: 'page-scan',
                name: 'DetectionPageScan',
                component: () => import('@/views/detection/page-scan.vue'),
                meta: { title: '页面扫描', icon: 'Document' },
            },
            {
                path: 'api-identify',
                name: 'DetectionApiIdentify',
                component: () => import('@/views/detection/api-identify.vue'),
                meta: { title: 'API识别', icon: 'Link' },
            },
            {
                path: 'form-detect',
                name: 'DetectionFormDetect',
                component: () => import('@/views/detection/form-detect.vue'),
                meta: { title: '表单检测', icon: 'Tickets' },
            },
            {
                path: 'login-identify',
                name: 'DetectionLoginIdentify',
                component: () => import('@/views/detection/login-identify.vue'),
                meta: { title: '登录识别', icon: 'Lock' },
            },
        ],
    },
    {
        path: '/scanner',
        component: Layout,
        redirect: '/scanner/path-scan',
        meta: { title: '漏洞扫描', icon: 'WarnTriangleFilled' },
        children: [
            {
                path: 'path-scan',
                name: 'ScannerPathScan',
                component: () => import('@/views/scanner/path-scan.vue'),
                meta: { title: '路径扫描', icon: 'FolderOpened' },
            },
            {
                path: 'param-fuzz',
                name: 'ScannerParamFuzz',
                component: () => import('@/views/scanner/param-fuzz.vue'),
                meta: { title: '参数fuzz', icon: 'EditPen' },
            },
            {
                path: 'vuln-detect',
                name: 'ScannerVulnDetect',
                component: () => import('@/views/scanner/vuln-detect.vue'),
                meta: { title: '漏洞检测', icon: 'Warning' },
            },
            {
                path: 'tasks',
                name: 'ScannerTasks',
                component: () => import('@/views/scanner/tasks.vue'),
                meta: { title: '测试任务', icon: 'List' },
            },
        ],
    },
    {
        path: '/testing',
        component: Layout,
        redirect: '/testing/plans',
        meta: { title: '测试管理', icon: 'Notebook' },
        children: [
            {
                path: 'plans',
                name: 'TestingPlans',
                component: () => import('@/views/testing/plans.vue'),
                meta: { title: '测试计划', icon: 'Calendar' },
            },
            {
                path: 'cases',
                name: 'TestingCases',
                component: () => import('@/views/testing/cases.vue'),
                meta: { title: '测试用例', icon: 'Checked' },
            },
            {
                path: 'reports',
                name: 'TestingReports',
                component: () => import('@/views/testing/reports.vue'),
                meta: { title: '测试报告', icon: 'DataAnalysis' },
            },
            {
                path: 'history',
                name: 'TestingHistory',
                component: () => import('@/views/testing/history.vue'),
                meta: { title: '历史记录', icon: 'Clock' },
            },
        ],
    },
    {
        path: '/analysis',
        component: Layout,
        redirect: '/analysis/llm-config',
        meta: { title: '智能分析', icon: 'MagicStick' },
        children: [
            {
                path: 'llm-config',
                name: 'AnalysisLlmConfig',
                component: () => import('@/views/analysis/llm-config.vue'),
                meta: { title: 'LLM配置', icon: 'Setting' },
            },
            {
                path: 'reports',
                name: 'AnalysisReports',
                component: () => import('@/views/analysis/reports.vue'),
                meta: { title: '分析报告', icon: 'TrendCharts' },
            },
            {
                path: 'vuln-verify',
                name: 'AnalysisVulnVerify',
                component: () => import('@/views/analysis/vuln-verify.vue'),
                meta: { title: '漏洞验证', icon: 'CircleCheck' },
            },
            {
                path: 'fix-suggest',
                name: 'AnalysisFixSuggest',
                component: () => import('@/views/analysis/fix-suggest.vue'),
                meta: { title: '修复建议', icon: 'FirstAidKit' },
            },
        ],
    },
    {
        path: '/system',
        component: Layout,
        redirect: '/system/payload',
        meta: { title: '系统配置', icon: 'Tools' },
        children: [
            {
                path: 'payload',
                name: 'SystemPayload',
                component: () => import('@/views/system/payload.vue'),
                meta: { title: 'Payload管理', icon: 'Suitcase' },
            },
            {
                path: 'strategy',
                name: 'SystemStrategy',
                component: () => import('@/views/system/strategy.vue'),
                meta: { title: '扫描策略', icon: 'SetUp' },
            },
            {
                path: 'notification',
                name: 'SystemNotification',
                component: () => import('@/views/system/notification.vue'),
                meta: { title: '通知设置', icon: 'Bell' },
            },
            {
                path: 'logs',
                name: 'SystemLogs',
                component: () => import('@/views/system/logs.vue'),
                meta: { title: '系统日志', icon: 'Memo' },
            },
        ],
    },
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/404.vue'),
        meta: { hidden: true },
    },
]

const router = createRouter({
    history: createWebHistory(),
    routes,
})

export default router

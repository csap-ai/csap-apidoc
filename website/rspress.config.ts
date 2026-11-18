import { defineConfig } from 'rspress/config';

const repo = 'https://github.com/csap-ai/csap-apidoc';

export default defineConfig({
  root: 'docs',
  base: '/',
  title: 'CSAP Apidoc',
  description: '多语言的运行时 API 文档框架(Java、Go 已发布):服务启动即生成文档,按接口控制显示字段,文档页内在线调试',
  lang: 'zh',
  icon: '/favicon.ico',
  logo: '/images/logo.png',
  markdown: {
    checkDeadLinks: true,
  },
  themeConfig: {
    socialLinks: [{ icon: 'github', mode: 'link', content: repo }],
    nav: [
      { text: '指南', link: '/guide/overview', activeMatch: '^/guide/' },
      { text: '参考', link: '/api/annotations', activeMatch: '^/api/' },
      { text: 'Go 版', link: '/go/', activeMatch: '^/go/' },
      { text: '路线图', link: '/roadmap' },
      { text: '发布说明', link: '/changelog' },
      { text: '下载', link: '/download/' },
      {
        text: 'v1.3.0',
        items: [
          { text: 'GitHub Releases', link: `${repo}/releases` },
          { text: 'Maven Central', link: 'https://central.sonatype.com/namespace/ai.csap.apidoc' },
          { text: '参与贡献', link: `${repo}/blob/main/CONTRIBUTING.md` },
          { text: '安全策略', link: `${repo}/blob/main/SECURITY.md` },
        ],
      },
    ],
    sidebar: {
      '/guide/': [
        {
          text: '开始',
          items: [
            { text: '介绍', link: '/guide/overview' },
            { text: '快速开始', link: '/guide/quick-start' },
            { text: '安装', link: '/guide/installation' },
            { text: '基础使用', link: '/guide/basic-usage' },
            { text: '配置参考', link: '/guide/configuration' },
          ],
        },
        {
          text: '核心功能',
          items: [
            { text: '注解', link: '/guide/annotations' },
            { text: '存储策略与返回过滤', link: '/guide/storage-strategies' },
            { text: '返回字段过滤', link: '/guide/runtime-filter' },
            { text: '参数校验', link: '/guide/validation' },
            { text: 'DevTools', link: '/guide/devtools' },
            { text: 'MCP(AI 调用接口)', link: '/guide/mcp' },
          ],
        },
        {
          text: '进阶',
          items: [
            { text: '在线调试', link: '/guide/testing' },
            { text: '跨域(CORS)排错', link: '/guide/cors' },
            { text: '导出', link: '/guide/export' },
            { text: '网关聚合', link: '/guide/gateway-aggregation' },
            { text: '架构', link: '/guide/architecture' },
          ],
        },
        {
          text: '其他',
          items: [
            { text: 'AI 编码助手技能', link: '/guide/ai-skill' },
            { text: '常见问题', link: '/guide/faq' },
          ],
        },
      ],
      '/api/': [
        {
          text: '参考',
          items: [
            { text: '注解参考', link: '/api/annotations' },
            { text: 'REST 接口', link: '/api/rest' },
            { text: 'Java 扩展点', link: '/api/java' },
          ],
        },
      ],
    },
    footer: {
      message: `基于 Apache-2.0 协议开源 · Copyright © 2019-present CSAP Team · <a href="${repo}">GitHub</a>`,
    },
    lastUpdated: true,
    editLink: {
      docRepoBaseUrl: `${repo}/tree/main/website/docs`,
      text: '在 GitHub 上编辑此页',
    },
    searchPlaceholderText: '搜索文档',
    outlineTitle: '本页目录',
    prevPageText: '上一页',
    nextPageText: '下一页',
    lastUpdatedText: '最后更新',
  },
});

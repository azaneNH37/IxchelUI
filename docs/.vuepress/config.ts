import { viteBundler } from '@vuepress/bundler-vite'
import { defineUserConfig } from 'vuepress'
import { plumeTheme } from 'vuepress-theme-plume'

export default defineUserConfig({
  lang: 'zh-CN',
  title: 'Ixchel UI',
  description: '面向 Minecraft 的可嵌入 Web 风格 UI 库',
  bundler: viteBundler(),
  theme: plumeTheme({
    // The theme would write generated permalinks into every page, the generated reference included.
    autoFrontmatter: false,
    // The generated pages describe params and members with ::: field containers.
    markdown: {
      field: true,
    },
    navbar: [
      { text: '参考', link: '/reference/' },
      { text: 'Java API', link: '/java-api/' },
    ],
    // reference/ and the module directories under java-api/ are written by `./gradlew docs` and are
    // not committed.
    collections: [
      { type: 'doc', dir: 'reference', title: '参考', sidebar: 'auto' },
      { type: 'doc', dir: 'java-api', title: 'Java API', sidebar: 'auto' },
    ],
  }),
})

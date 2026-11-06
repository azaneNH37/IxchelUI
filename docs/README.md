---
pageLayout: home
config:
  - type: hero
    full: true
    hero:
      name: Ixchel UI
      tagline: 面向 Minecraft 的可嵌入 Web 风格 UI 库
      text: 用 HTML、标准 CSS 子集与 ViewModel 写界面，shade 进你自己的 mod
      actions:
        - theme: brand
          text: 参考
          link: /reference/
        - theme: alt
          text: Java API
          link: /java-api/
  - type: features
    features:
      - title: 嵌入，而非安装
        details: 没有自己的 modid 与入口，由宿主 mod 驱动初始化；不带 mixin，不注册全局 ID，不改主 render target。
      - title: Web 写法
        details: 模板是 HTML，样式是标准 CSS 的子集，数据经 ViewModel 绑定；不内嵌 JS 引擎，也不追求与浏览器渲染等价。
      - title: 离线检查
        details: ixchel-kit 在游戏外检查视图与 ViewModel 的对应关系，诊断带文件位置与稳定的错误码。
---

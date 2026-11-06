---
title: Java API
---

各库模块的 Java API，与发布的制品一一对应。只收录标注了 `@HostApi` 的类型，即宿主 mod 可以依赖的契约；其余 `public` 类型是模块间的实现，不保证兼容。各模块的页面由 `./gradlew docs` 从源码的文档注释生成。

<CardGrid>
  <LinkCard title="ixchel-core" href="/java-api/ixchel-core/" description="不含 Minecraft 的内核：ViewModel 与绑定、文档、样式、布局" />
  <LinkCard title="ixchel-mc-1.20.1" href="/java-api/ixchel-mc-1.20.1/" description="Minecraft 1.20.1 适配层：UiScreen、UiContainerScreen、UiHud、UiRegistries" />
  <LinkCard title="ixchel-forge-1.20.1" href="/java-api/ixchel-forge-1.20.1/" description="Forge 1.20.1 接入：IxchelForge" />
  <LinkCard title="ixchel-mc-1.21.1" href="/java-api/ixchel-mc-1.21.1/" description="Minecraft 1.21.1 适配层" />
  <LinkCard title="ixchel-neoforge-1.21.1" href="/java-api/ixchel-neoforge-1.21.1/" description="NeoForge 1.21.1 接入：IxchelNeoForge" />
</CardGrid>

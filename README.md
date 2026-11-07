## Ix Chel UI

施工中。。。

### 核心设计原则

- 0 mixin，不依赖mod注册，**可shade分发**，兼容其它mod工作量小且规避jarInjar or 硬性前置的全局污染 or 炸包风险
- **轻量化**，一切开发期的辅助工具不进宿主本体
- 核心模块不依赖MC本体，多模块分层构建模式，**版本和加载器适配难度低**
- **Web语言UI**，ai大人最擅长的领域
- **组件，i18n，主题，数据绑定，事件冒泡，模板内表达式**，丰富的前端框架设计可以借鉴

---

- **动效，css样式标准，fontFace，mc shader etc.**，综合前端和mc引擎的视效能力
- mc slot，player inventory，以及html标准和扩展控件，内置了**充足的UI组件库**，可直接使用

---

- No JS，ViewModel数据入口归一，非图灵完备的模板表达式，**所有文件可编译期静态分析**
- ViewModel良好的**表现层数据协议**，设计来源Signal，免去你喜欢写的一大堆抽象轮询或者整天把网络层和UI层耦合在一起
- **完备的离线检查**，lint，清晰的错误码，lsp（WIP），大肥鱼再也不会整天写一堆错的格式在runtime才报错
- build gradle插件 + 编译期lint + web-editor（WIP） + 热重载，用**工作量最低的方案**提供全套对人和agent友好的**开发工具**
- UIElement，UIView，宿主可轻松的实现原生元素，携带java逻辑的组件的**扩展**

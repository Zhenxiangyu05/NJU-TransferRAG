<template>
  <article class="legal-page feedback-page" aria-labelledby="feedback-page-title">
    <a class="back-link" href="/">← 返回首页</a>
    <h1 id="feedback-page-title">纠错 / 侵权反馈</h1>
    <p>NJU Compass 是我个人学习和开发过程中维护的校园知识检索项目。</p>
    <p>由于资料来源、文档解析、知识整理以及 AI 生成回答都可能存在疏漏，如果你在使用过程中发现：</p>
    <ul class="feedback-topics">
      <li>回答内容有误或存在歧义</li>
      <li>引用资料或来源不准确</li>
      <li>页面或功能出现 Bug</li>
      <li>有更好的功能或体验建议</li>
      <li>有适合补充到知识库的公开资料</li>
      <li>发现版权、署名或侵权相关问题</li>
    </ul>
    <p>都非常欢迎联系我。</p>
    <p>如果是内容纠错或资料补充，也欢迎附上具体问题、资料名称、来源链接或相关截图，这样我可以更快地定位和核实。</p>
    <p>这是一个仍在持续完善中的个人项目，非常感谢你的反馈和帮助。</p>

    <section class="feedback-contact" aria-labelledby="feedback-contact-title">
      <h2 id="feedback-contact-title">联系方式</h2>
      <dl>
        <div>
          <dt>QQ</dt>
          <dd>2052578228</dd>
        </div>
        <div>
          <dt>邮箱</dt>
          <dd><a href="mailto:241880560@smail.nju.edu.cn">241880560@smail.nju.edu.cn</a></dd>
        </div>
      </dl>
    </section>

    <section class="feedback-note" aria-labelledby="feedback-removal-title">
      <h2 id="feedback-removal-title">侵权 / 内容移除</h2>
      <p>如果你认为本站展示的某项资料涉及版权、署名、隐私或其他权益问题，请通过上述邮箱联系我，并尽量提供相关页面、资料名称及具体说明。我会在核实后及时处理。</p>
    </section>
    <section class="feedback-note" aria-labelledby="feedback-materials-title">
      <h2 id="feedback-materials-title">资料补充</h2>
      <p>如果你有适合补充到知识库的公开资料，也欢迎联系我。为了保证来源可追溯，优先提供公开来源、原始文件或可验证的资料链接。</p>
    </section>
  </article>
</template>

<style scoped>
.feedback-topics {
  margin: 0 0 19px;
  padding-left: 22px;
}

.feedback-topics li + li {
  margin-top: 6px;
}

.feedback-contact {
  margin: 28px 0;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
}

.feedback-contact h2 {
  margin: 0 0 16px;
}

.feedback-contact dl {
  margin: 0;
}

.feedback-contact dl > div {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 8px 16px;
}

.feedback-contact dl > div + div {
  margin-top: 12px;
}

.feedback-contact dt {
  color: var(--muted);
}

.feedback-contact dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.feedback-note {
  margin-top: 28px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

@media (max-width: 680px) {
  .feedback-contact {
    padding: 20px;
    border-radius: 15px;
  }
}

@media (max-width: 420px) {
  .feedback-contact dl > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}
</style>

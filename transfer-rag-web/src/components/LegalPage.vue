<script setup>
import { contactEmail, feedbackIssuesUrl } from '../complianceConfig.js'
</script>

<template>
  <article class="legal-page" aria-labelledby="legal-title">
    <a class="back-link" href="/">← 返回首页</a>
    <h1 id="legal-title">免责声明</h1>
    <p>
      NJU Compass 是个人开发的校园资料检索与人工智能问答项目，用于个人技术学习、研究与作品展示。本站与南京大学及其任何学院、部门、机构不存在隶属、授权、合作或官方认可关系。
    </p>
    <p>
      本站回答由人工智能根据已收录资料生成，可能存在遗漏、过时、理解偏差或生成错误。涉及培养方案、转专业、课程安排、考试要求、学籍管理等事项，应以南京大学及相关院系最新发布的官方文件、通知和工作人员答复为准。
    </p>
    <p>
      本站尽可能为回答提供资料来源和引用。引用资料的著作权及其他合法权益归原权利人所有。本站不会因收录或引用相关资料而取得其权利。若权利人认为本站内容侵犯其合法权益，可通过本站提供的反馈渠道联系，核实后将及时处理。
    </p>
    <p>
      用户不应依据本站 AI 回答作出具有重大影响的决定，也不应将本站生成内容视为南京大学官方意见、承诺或证明。
    </p>

    <section id="feedback" class="legal-feedback" aria-labelledby="feedback-title">
      <h2 id="feedback-title">纠错、资料更新与侵权反馈</h2>
      <p>请说明相关页面、资料标题或引用位置，以及需要更正、更新或处理的内容。</p>
      <p v-if="contactEmail">
        项目联系邮箱：<a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
      </p>
      <p v-else>
        项目专用邮箱尚未配置。可先通过
        <a :href="feedbackIssuesUrl" target="_blank" rel="noopener noreferrer">项目 GitHub Issues</a>
        提交不含敏感信息的反馈；该入口需要 GitHub 账号，内容可能公开。侵权投诉如涉及身份证明或其他私密材料，请勿在公开 Issue 中上传，先简述问题并等待私下联系。
      </p>
    </section>
  </article>
</template>

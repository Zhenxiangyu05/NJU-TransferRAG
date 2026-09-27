<script setup>
import { contactEmail, feedbackIssuesUrl } from '../complianceConfig.js'
</script>

<template>
  <article class="legal-page" aria-labelledby="privacy-title">
    <a class="back-link" href="/">← 返回首页</a>
    <h1 id="privacy-title">隐私说明</h1>
    <p>公开问答无需注册，也无需提供姓名或联系方式。公开用户端未发现用于识别用户的 Cookie、localStorage、第三方统计 SDK 或广告追踪代码。</p>
    <p class="privacy-warning">请勿在问题中输入姓名、学号、身份证号、手机号、住址、账号密码等不必要的个人信息。</p>
    <p>
      用户提交的问题会发送到本站后端，并交由已配置的远程 AI 服务进行向量化、证据判断及回答生成。本站未在 MySQL 中建立用户问题或问答历史表，也不主动建立用户问答历史档案；远程 AI 服务如何处理所接收内容，不能仅凭本站代码判断。
    </p>
    <p>
      为保障服务安全、故障排查和基本运行，Nginx access log 记录网络地址、访问时间、请求方法、URL 路径及查询参数、HTTP 状态、响应字节数、Referer 和浏览器 User-Agent；管理端认证用户名也可能出现在该技术日志中。POST 问题正文不在 Nginx 默认 access log 中。后端不主动逐字记录用户问题，但 Answerability 的诊断原因会写入服务日志，可能复述问题片段；错误时也可能记录异常信息。
    </p>
    <p>
      当前代码未发现将公开问答用于广告、营销、用户画像或出售、交易的行为。本站不向公开用户提供文件上传、评论或发帖功能；受保护的知识库管理端仅供管理员使用。
    </p>
    <p>
      纠错、资料更新或侵权反馈请联系
      <a v-if="contactEmail" :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
      <a v-else :href="feedbackIssuesUrl" target="_blank" rel="noopener noreferrer">项目 GitHub Issues</a>。
      使用公开 Issue 时请勿提交个人信息或私密证明材料；也可查看<a href="/legal#feedback">反馈说明</a>。
    </p>
  </article>
</template>

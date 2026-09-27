<script setup>
import { contactEmail, feedbackIssuesUrl } from '../complianceConfig.js'
</script>

<template>
  <article class="legal-page" aria-labelledby="privacy-title">
    <a class="back-link" href="/">← 返回首页</a>
    <h1 id="privacy-title">隐私说明</h1>
    <p>公开问答无需注册，也无需提供姓名或联系方式。公开用户端未发现用于识别用户的 Cookie、localStorage、第三方统计 SDK 或广告追踪代码。</p>
    <p class="privacy-warning">请勿在问题中输入姓名、学号、身份证号、手机号、住址、账号密码、验证码等不必要的个人信息。</p>
    <p>当前公开入口使用 HTTP，尚未配置 HTTPS；浏览器与本站之间的传输不具备 HTTPS 加密保护。请勿通过问答或公开反馈入口提交敏感信息。</p>
    <p>
      用户提交的问题会发送到本站后端，并交由已配置的远程人工智能模型服务进行向量化、资料充分性判断及回答生成。本站未在 MySQL 中建立用户问题或问答历史表，也不主动建立用户长期问答历史档案。上游模型服务对请求数据的具体保存期限由相应服务提供方决定，本站无法代其作出承诺。
    </p>
    <p>
      为保障服务安全、故障排查和基本运行，服务器会产生必要技术日志。Nginx access log 记录网络地址、访问时间、请求方法、URL 路径及查询参数、HTTP 状态、响应字节数、Referer 和浏览器 User-Agent；管理端认证用户名也可能出现在该技术日志中。POST 问题正文不在 Nginx 默认 access log 中。后端正常问答日志仅记录检索层、候选数量、回退状态及资料充分性等结构化结果，不主动逐字记录用户问题；错误时仍可能记录异常类型或其他诊断信息。
    </p>
    <p>
      当前代码未发现将公开问答用于广告、营销、用户画像或出售、交易个人信息的行为。本站不向公开用户提供文件上传、评论或发帖功能；受保护的知识库管理端仅供管理员使用。
    </p>
    <p>
      纠错、资料更新或侵权反馈请联系
      <a v-if="contactEmail" :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
      <a v-else :href="feedbackIssuesUrl" target="_blank" rel="noopener noreferrer">项目 GitHub Issues</a>。
      公开 Issue 中请勿提交身份证号、学号、手机号、住址、私人文件或其他敏感个人信息。需要私密发送侵权材料时，请通过项目专用邮箱联系；邮箱配置后将在此显示。在此之前请勿将私密材料上传至公开 Issue。也可查看<a href="/legal#feedback">反馈说明</a>。
    </p>
  </article>
</template>

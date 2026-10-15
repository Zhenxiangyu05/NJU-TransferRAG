# 多轮上下文与追问补全 v1（本地实现）

基线：0473f6ba4ac96acbb75bd1d2b0353604141e71ae。
实现工作树：D:/Codes/Transfer-RAG-Platform/Transfer-RAG-chat-compat-diagnostics。
主工作树仍保留原PrivacyPage未提交改动及三个Benchmark审核文件。初始实现阶段不连接生产、不部署、不commit/push、不调用付费模型，不修改Gold、MySQL Schema、Qdrant或Embedding配置。后续凭据处置及隔离验收见末尾追加记录，不能将原始测试结果当作最新结果。

## 用户行为

当前页面首次提问建立匿名会话，后续提问复用该会话。“那考试呢”“这个有年级限制吗”等需要历史的追问先补全为独立问题；明确的新专业、年级、年份优先，完整的新问题不继承旧话题。点击“新对话”删除旧会话上下文并取得新凭据，页面消息同时清空。

页面保留最多8条展示记录，与后端模型上下文不是同一范围。后端默认只保留最近4轮成功且有来源的问答所对应的用户原问/独立问题，最多4000个Java字符串字符；不把历史回答交给补全模型或作为知识证据。新话题开启新上下文链。拒答、澄清、失败请求不新增话题历史。刷新/关闭页面丢失会话关联，不恢复历史列表。

## 接口：只复用现有POST /api/rag/ask

没有新增URL、Cookie、localStorage、账号、历史查询接口或客户端system/history输入。所有请求继续经过现有Nginx和Redis Lua限流。创建/重置会话也消耗一次现有请求额度，不调用模型；没有修改现有限流额度。

1. 旧客户端只发送 question：继续原单轮逻辑。
2. 创建会话：action=NEW_CONVERSATION。响应含conversationToken、nextRequestId、expiresInSeconds；Cache-Control=no-store。
3. 会话问答：question、conversationToken、requestId（使用上次响应签发的nextRequestId），action省略或ASK。
4. 重置：action=NEW_CONVERSATION并携带旧conversationToken。旧会话忙碌时409，不抢占正在执行的请求。

凭据为SecureRandom生成的256位Base64URL capability，只在当前页面内存保存；Redis Key仅含其SHA-256。仅凭可猜测ID无法读取/修改上下文。拒绝客户端history/messages/system/role和其他未知字段。会话状态没有公开读取API。

## 实际调用链

RagController → ConversationService → StandaloneQuestionRewriter（必要时最多一次）→ RagService.askWithContext(current, independent) → 原RetrievalService/QueryRewriteService → Canonical/Evidence → Answerability → Generation。

补全后的独立问题同时进入检索Embedding、实体/院系/专业/年份解析、Metadata Filter和Answerability。Generation保留本轮原问，并附上有界的语义补全说明；没有把历史回答插入Sources。无会话以及无需补全的问答继续原RagService.ask路径。Canonical/Evidence/fallback、Readiness Gate、相关性门禁、引用格式和学校Embedding保持原实现。

明显完整的问题只做既有实体解析检查，不调用补全模型。不确定的输入允许调用模型分类；模型可以返回INDEPENDENT、RESOLVED或CLARIFY。补全结果使用严格JSON、长度、主体、最新专业/院系/年级/年份及不新增未知条件的校验。语义依旧可能存在模型误判，不声称形式化保证全部语言表达正确。

## Chat保护与超时

conversationCompletionChatModel复用同一个自动配置OpenAiChatModel和实际SDK客户端，不新增连接或凭据；使用同一个deepseekChat熔断器、Bulkhead和生命周期。它有独立较短的权威Prompt HTTP deadline，默认8秒（最多10秒，且不超过主Chat配置值）、maxTokens=768、SDK retries=0。主Answerability/Generation仍40秒，Query Embedding仍学校30秒，导入Embedding配置不变。

真实loopback HTTP测试使用200ms配置、延迟1秒，验证网络请求被截断；测量前预热共享SDK，避免把类加载/JIT时间混作HTTP时间。普通Chat默认选项仍40秒，OPEN时补全不发送HTTP请求。没有新Retry或TimeLimiter，也不声称底层OkHttp绝无follow-up。

依赖历史的请求最多增加一次Chat，实际耗时取决于模型；不需要历史的请求不增加模型调用。名义预算：Canonical8+30+80=118秒，最坏fallback8+30+120=158秒，不包含其他处理、缓存等待或网络恢复，不是端到端硬截止。保留Nginx180秒，不做超时调优。

## Redis、并发和幂等

复用queryCacheRedisConnectionFactory和现有受保护Redis连接。会话默认在最后一次处理写入后30分钟过期；处理中锁租约默认5分钟。Redis故障/淘汰/会话过期不会自动创建空历史来回答追问：明确503或410，要求用户重开对话并说明完整问题。

Lua原子领取同一会话的处理权，无等待队列；并发请求返回409。状态写入和解锁都核对随机owner。租约丢失或Session被淘汰后，旧请求不能覆盖新历史或复活会话；旧外部HTTP可能继续完成，不声称外部fencing/exactly-once。

每次问答使用服务端签发的一次性requestId。先持久登记PENDING并轮换下一nonce，再调用模型，避免进程崩溃窗口中盲目重复付费。只有成功完成后追加上下文。最近8个收据用于冲突检查；即便旧收据已截断，旧nonce也不能重新变成有效的新请求。

只为最近一次完成请求保留最多32KiB响应收据；整份Redis状态最大64KiB。重复同一nonce/问题优先返回收据，不调用模型、不追加历史，且重新检查引用Document的当前Readiness。更早、过大响应或状态未知的重复请求返回409，不自动重新生成。下一nonce通过响应/已认证错误响应交还页面；客户端不会自动HTTP重试。

该收据只服务于同一会话同一已完成请求的去重，不是跨问题/跨会话的答案缓存。查询Embedding缓存仍按实际独立检索输入区分专业/年份；补全不复用旧答案。

共享64MiB Redis采用allkeys-lru，会话和现有缓存/限流数据竞争内存，可能提前失效或增加已有的限流Key淘汰风险；本轮不改变Redis配置。上线前必须补真实Redis验收并评估内存。没有长期记忆、数据库问答表、跨会话提取或摘要。

## 错误与脱敏日志

- 无历史的明显指代、模型CLARIFY、空/非法JSON、未解析的省略、条件冲突：422固定澄清提示，不将原始追问继续送入RAG假装成功。
- 补全超时/一般模型错误：明确CONTEXT_COMPLETION_FAILED；用户可以改成完整问题独立处理。
- 熔断/并发拒绝：保留AI_CIRCUIT_OPEN / AI_BUSY、503及Retry-After，不伪装为资料不足。
- 无效/过期凭据410；会话并发409；Redis不可用503；失去处理权409。

新增RAG conversation白名单日志记录随机请求关联ID、context_used、completion_executed、completion_us和固定降级类别；CONTEXT_COMPLETION纳入现有阶段诊断。没有问题、历史、补全正文、凭据、向量或任意异常message/堆栈。HTTP物理发送次数仍NOT_OBSERVABLE。

## 主要文件

- src/main/java/com/yu/transferrag/conversation/：配置、状态/收据、Redis Lua存储、补全器、会话服务及固定错误。
- config/ChatTimeoutConfiguration.java：复用SDK的短超时补全Bean。
- config/QueryEmbeddingCacheConfiguration.java：会话功能启用时复用Redis工厂。
- controller/RagController.java、dto/AskRequest.java、dto/RagResponse.java、controller/GlobalExceptionHandler.java：兼容的单入口协议、拒绝客户端历史、固定错误。
- service/RagService.java：独立检索问题与当前原问分别传递；所有事实仍来自本轮检索。
- diagnostics/RagDiagnostics.java：新增补全阶段。
- transfer-rag-web/src/conversationClient.js、App.vue、AskForm.vue、AnswerPanel.vue、style.css：页面内多轮、新对话、长度限制、引用继续展示。
- PrivacyPage.vue：当前会话临时数据披露，并保留学校Embedding、现有缓存/限流披露；未覆盖主工作树原文件。

## 本地验证与待验收

最后结果见本文件末尾。单元测试覆盖首轮兼容、追问、专业/年级/年份纠正、独立话题、失败、截断、会话隔离、并发、nonce去重、Readiness重验和日志脱敏。真实SDK loopback验证较短HTTP超时、Provider隔离及熔断；原Canonical/Evidence、Retrieval与Readiness回归继续运行。

本机Docker Engine当前报npipe dockerDesktopLinuxEngine不存在，新增7项RedisConversationIntegrationTest和既有Docker依赖测试未执行，不能报告真实Redis/MySQL/Qdrant验收通过。既有H2应用启动smoke不是MySQL集成替代。没有调用生产数据库、学校/官方付费模型，也没有运行Gold Benchmark。

一次初始定向测试继承了本地真实官方凭据，断言将其输出；请求仅发往127.0.0.1测试桩。已修复测试fixture环境隔离、认证断言只比较身份布尔值，并对本轮生成的失败报告脱敏。后续Maven子进程移除模型/Redis/数据库凭据环境变量。会话中的既有工具输出无法撤回，建议后续轮换该官方Key；本轮未修改生产凭据。

## 最小上线步骤（本轮未执行）

1. Docker恢复后，在隔离Redis执行新增7项集成及相关缓存/限流回归；核验权限、TTL、原子互斥、旧owner不能写入、状态反序列化和重置。先处理上述凭据轮换事项，保留学校Key不变。
2. 审核当前0473基础上的diff、完成发布提交/干净构建并排除本地敏感配置。没有Schema Migration。备份活动JAR、前端静态文件、root-only环境文件。
3. 部署审核的新后端，保留DeepSeek官方Chat、学校bge-m3、缓存/Lua/Resilience及Worker=false；显式APP_RAG_CONVERSATION_ENABLED=true，仅重启Spring Boot。其他会话参数可保持默认。
4. 部署对应前端和会话隐私说明；同一POST路径无需新增Nginx代理或开放管理端。新前端依赖会话功能，不能在后端仍关闭功能时当作已上线。
5. 最小实模型联调为2次RAG：一次已验证的完整首问，再一次明确承接的追问，核验补全、检索条件、Answerability和Citation。新建/重置本身不调用模型。纠正/话题/故障矩阵优先沿用本地证据，不批量调用生产模型。本轮真实付费请求为0。
6. 验证匿名会话隔离、Redis状态TTL和内存、原文/管理端安全边界及29/8/466/15/466指纹；不修改数据，不重启其他容器。

## 最小回滚步骤（本轮未执行）

恢复旧前端静态备份，将APP_RAG_CONVERSATION_ENABLED=false并仅重启Spring Boot；旧question单轮调用仍可用。可以保留新JAR关闭功能，或经审核恢复当前0473 JAR。保持官方Chat、学校Embedding、Redis Cache/Lua/Resilience及Worker=false，不能恢复缺少Readiness/Timeout Hotfix的版本。

让nju:conversation:v1命名空间的临时状态自然TTL过期；不清空Redis、不触碰向量/数据库、不执行反向Migration。只关闭后端开关而不恢复前端时，新页面会明确提示功能未启用，不会自动把不完整追问改当单轮答案。

## 最终测试结果

- Java21编译通过；最终Maven全套：513项，441 PASS、0 FAIL/ERROR、72 SKIP。
- 72 SKIP中69项因Docker Engine不可用（含新增7项Redis会话真实集成），3项为既有的2个opt-in Benchmark及Windows符号链接能力跳过。
- 新增四组会话/HTTP测试：StandaloneQuestionRewriterTest19、ConversationServiceTest16、ConversationHttpTest6、ConversationCompletionHttpTest2，合计43 PASS。
- 另在既有测试中增加V1上下文1、Canonical上下文与fallback2、不同专业/年份向量缓存隔离1，均通过。
- Canonical相关33 PASS；Retrieval24 PASS；Readiness6 PASS；既有Chat Timeout、Provider隔离和Resilience单元/HTTP回归通过。
- 前端npm test：16 PASS、0 FAIL；npm run build通过；npm ci审计0漏洞。
- git diff --check通过；主工作树原隐私页及三个Benchmark审核文件保持原状；没有生产操作、付费模型调用、commit或push。
- 结论：本地实现及可执行mock/HTTP验证完成，真实Redis会话及其余Docker依赖验收尚未完成，不能据此批准生产发布。

## 2026-10-15 凭据处置与补充验收（取代前述Docker阻塞结论）

- 仅在内存中比较本地已保存凭据、生产受保护配置来源及活动进程，确认泄露凭据与生产共用。没有输出值、片段或凭据摘要。生产仍为官方Chat，学校Embedding未变。
- 扫描并脱敏当前任务生成报告；后续扫描145个测试/相关文件及133个近期生成文本文件未发现剩余秘密。Git变更扫描为0个实际凭据命中。既有工具输出不能撤回，旧Key尚待官方控制台轮换/撤销，是否存在滥用UNKNOWN。
- 新增隐藏输入脚本scripts/Set-DeepSeekRotationKey.ps1，替代Key用Windows当前用户DPAPI加密、目录ACL仅当前用户。scripts/Rotate-ProductionDeepSeekKey.ps1及对应Python仅供已授权的凭据轮换，尚未执行；不用于多轮功能部署。
- Docker Desktop启动失败：sailor-ingest.sock失效，未执行重置或删除。使用已安装的独立WSL Docker Engine及临时127.0.0.1连接运行真实Testcontainers。镜像通过AWS公开镜像源取得。没有使用生产Redis、清空数据或删除Volume。
- 原有7项Redis会话集成全部PASS、0 SKIP；新增真实Redis服务级重复提交、新对话清理、旧请求延迟完成3项亦PASS。合计10 PASS。
- 扩大回归：60组、500项，497 PASS、0 FAIL/ERROR、3 SKIP。3项均为已有opt-in Benchmark及Windows符号链接用例。本次有意未运行ImportTaskMySqlIntegrationTest、FrozenEvidenceIntegrationTest、EvidenceQdrantIntegrationTest、EvidenceJvmRestartIntegrationTest、QueryEmbeddingRagIntegrationTest；不把未运行组算作通过。
- 真实Redis Embedding Cache 8 PASS、Redis Lua 14 PASS；前端16 PASS、build PASS；git diff --check PASS。
- 六次联调的固定输入及标准见docs/conversation-live-acceptance-plan.md。凭据未完成轮换前实际付费问答为0/6，实际新增补全耗时NOT_MEASURED，不宣称实模型验证已通过。
- 已从345文件源码快照构建后端候选及前端包，位于target/releases/conversation-v1-local-20261015；只是本地候选，不是获准发布的正式Release。无commit/push或多轮功能生产部署。凭据与六次联调关口未完成，最终冻结/发布就绪为false。

## 后续授权及本轮真实验收

用户明确暂缓Key轮换，接受当前未解决风险并授权复用当前凭据进行联调、Git提交/发布标签及多轮功能生产上线。此授权不代表泄露问题已解决；不打印凭据或其识别摘要，不回退学校Chat，不修改学校bge-m3。

真实问答总数6：首次为夹具Readiness配置失败，修正并通过6项离线Stub后，仅执行原计划剩余5个输入，顺序6→2→3→4→5。全部必须的用户行为在后5次中通过，独立首问5.004s、追问2.605s、纠正3.353s、话题切换2.564s、重置澄清0.017s。补全真实耗时687.406ms及728.049ms。首个失败的原始响应未保留，不伪造其HTTP或根异常；没有重发它或追加第7次。

生产发布最多3次问答：成功首问（沿用已验收Canonical问题）、承接同一话题追问、点击新对话后的无历史指代澄清。创建/重置会话不调用模型。任一非预期失败立即停止，恢复当前0473后端、旧公众前端及仅会话开关；不清空Redis、不恢复带旧Key的历史环境文件，不触及学校Embedding或数据库/向量。

实际部署记录及固定Commit/JAR/前端SHA以本轮target/releases对应正式目录报告为准，不使用早先未提交源码候选覆盖正式版本。Key轮换仍DEFERRED_UNRESOLVED。

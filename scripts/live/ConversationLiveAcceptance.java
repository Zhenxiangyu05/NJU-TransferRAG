package com.yu.transferrag.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.cache.*;
import com.yu.transferrag.config.ChatTimeoutConfiguration;
import com.yu.transferrag.diagnostics.RagDiagnostics;
import com.yu.transferrag.dto.*;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.protection.*;
import com.yu.transferrag.repository.*;
import com.yu.transferrag.service.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.*;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.mockito.Mockito.*;

/** Explicit standalone paid acceptance tool, never a Maven test. Credentials only arrive over stdin. */
public final class ConversationLiveAcceptance {
    static final ObjectMapper JSON=new ObjectMapper();
    static Path RESULT=Path.of("target/conversation-live-20261015.json");
    static final List<Map<String,Object>> results=new ArrayList<>();
    static int requests;
    static boolean resume;
    static String job="legacy";
    static final Path BUDGET=Path.of("target/conversation-v2-budget.json");
    static final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> validationLogs=new ch.qos.logback.core.read.ListAppender<>();
    static void reserveBudget() throws Exception {
        if(job.equals("legacy"))return;
        var ledger=Files.exists(BUDGET)?JSON.readTree(Files.readString(BUDGET)):JSON.createObjectNode().put("questions",0);
        int count=ledger.path("questions").asInt();require(count<20);
        ((com.fasterxml.jackson.databind.node.ObjectNode)ledger).put("questions",count+1);
        Files.writeString(BUDGET,JSON.writeValueAsString(ledger));
    }
    static void require(boolean condition) {if(!condition)throw new IllegalStateException("SAFE_ACCEPTANCE_ASSERTION");}
    static void persist() throws Exception {Files.writeString(RESULT,JSON.writeValueAsString(Map.of("requests",requests,"results",results,"key_rotation","DEFERRED_UNRESOLVED")));}
    public static void main(String[] args) {
        boolean offline=args.length==1&&args[0].equals("--offline");
        if(args.length==1&&Set.of("--diagnose-v2","--verify-v2","--verify-alias-v2").contains(args[0])) {
            job=args[0].substring(2);RESULT=Path.of("target/conversation-"+job+"-20261015.json");
        }
        resume=args.length==1&&args[0].equals("--resume-after-fixture-fix");
        if(offline)RESULT=Path.of("target/conversation-offline-20261015.json");
        try {
            if(resume) {
                var previous=JSON.readTree(Files.readString(RESULT));require(previous.path("requests").asInt()==1&&previous.path("results").isEmpty());
                var proof=JSON.readTree(Files.readString(Path.of("target/conversation-offline-20261015.json")));
                require(proof.path("results").size()==6);for(var result:proof.path("results"))require(result.path("pass").asBoolean());
                requests=1;results.add(new LinkedHashMap<>(Map.of("case",1,"pass",false,"status","NOT_RETAINED","diagnosis","READINESS_FIXTURE_ERROR_REPRODUCED_OFFLINE")));
            } else require(!Files.exists(RESULT)); // Never silently reset a paid attempt ledger.
            @SuppressWarnings("unchecked") Map<String,String> secrets=JSON.readValue(System.in,Map.class);
            require(secrets.get("AI_CHAT_PROVIDER").equals("deepseek-official"));
            require(secrets.get("AI_EMBEDDING_MODEL").equals("bge-m3"));
            // Do not expose test inputs, replies, exception messages or SDK HTTP logging.
            ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
            validationLogs.start();var validationLogger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(StandaloneQuestionRewriter.class);
            validationLogger.setLevel(ch.qos.logback.classic.Level.INFO);validationLogger.setAdditive(false);validationLogger.addAppender(validationLogs);
            String password=UUID.randomUUID().toString();
            try(var redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
                    .withCommand("redis-server","--requirepass",password,"--save","","--appendonly","no")
                    .withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(6379))))) {
                redis.start();
                var config=new RedisStandaloneConfiguration(redis.getHost(),redis.getMappedPort(6379));config.setPassword(password);
                var connection=new LettuceConnectionFactory(config);connection.afterPropertiesSet();connection.start();
                try {
                    var properties=new ConversationProperties(true,Duration.ofMinutes(30),Duration.ofMinutes(5),Duration.ofSeconds(8),4,500,4000);
                    var runner=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,OpenAiEmbeddingAutoConfiguration.class))
                            .withUserConfiguration(ChatTimeoutConfiguration.class,AiResilienceConfiguration.class)
                            .withBean(ToolCallingManager.class,()->mock(ToolCallingManager.class)).withBean(ConversationProperties.class,()->properties)
                            .withInitializer(c->{
                                c.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                                try {c.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));}
                                catch(Exception e){throw new IllegalStateException("SAFE_RESOURCE_ERROR");}
                                c.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("approved-live-credentials",new HashMap<>(secrets)));
                            }).withPropertyValues("app.rag.conversation.enabled=true","app.ai.resilience.enabled=true",
                                    "spring.ai.openai.chat.timeout=40s","spring.ai.openai.chat.max-retries=0","spring.ai.openai.embedding.max-retries=0",
                                    "app.ai.resilience.embedding-timeout=30s","app.ai.resilience.extra-retries=0");
                    runner.run(c->{
                        require(c.getStartupFailure()==null);
                        var original=c.getBean(OpenAiEmbeddingModel.class);
                        try {
                            var chatField=org.springframework.ai.openai.OpenAiChatModel.class.getDeclaredField("openAiClient");chatField.setAccessible(true);
                            var client=chatField.get(c.getBean(org.springframework.ai.openai.OpenAiChatModel.class));
                            var optionField=client.getClass().getDeclaredField("clientOptions");optionField.setAccessible(true);
                            var actual=(com.openai.core.ClientOptions)optionField.get(client);
                            require(java.net.URI.create(actual.baseUrl()).getHost().equals(offline?"127.0.0.1":"api.deepseek.com"));
                            require(actual.apiKey().orElseThrow().equals(secrets.get("DEEPSEEK_API_KEY")));
                            var embeddingField=OpenAiEmbeddingModel.class.getDeclaredField("openAiClient");embeddingField.setAccessible(true);
                            var school=embeddingField.get(original);var schoolOptions=school.getClass().getDeclaredField("clientOptions");schoolOptions.setAccessible(true);
                            var actualSchool=(com.openai.core.ClientOptions)schoolOptions.get(school);
                            require(java.net.URI.create(actualSchool.baseUrl()).getHost().equals(offline?"127.0.0.1":"njusehub.info"));
                            require(!actualSchool.apiKey().equals(actual.apiKey()));
                        } catch(ReflectiveOperationException error){throw new IllegalStateException("SAFE_PROVIDER_PROOF_FAILED");}
                        try {exercise(c.getBean(ChatModel.class),c.getBean("conversationCompletionChatModel",ChatModel.class),
                                original,c.getBean(AiRemoteGuard.class),connection,properties);}
                        catch(Exception e){throw new IllegalStateException("SAFE_LIVE_ACCEPTANCE_STOPPED",e);}
                        finally {
                            try {var f=OpenAiEmbeddingModel.class.getDeclaredField("openAiClient");f.setAccessible(true);((com.openai.client.OpenAIClient)f.get(original)).close();}
                            catch(Exception ignored){}
                        }
                    });
                } finally {connection.destroy();}
            }
            System.out.println("LIVE_ACCEPTANCE_COMPLETE requests="+requests+" passed="+results.stream().filter(r->Boolean.TRUE.equals(r.get("pass"))).count());
        } catch(Throwable failure) {
            try {persist();}catch(Exception ignored){}
            var classified=RagDiagnostics.classify(failure);
            System.out.println("LIVE_ACCEPTANCE_STOPPED requests="+requests+" completed="+results.size()+" category="+classified.category()+" root="+classified.root());
            System.exit(1);
        }
    }
    @SuppressWarnings("unchecked")
    static void exercise(ChatModel main,ChatModel completion,EmbeddingModel original,AiRemoteGuard guard,
                         LettuceConnectionFactory connection,ConversationProperties properties) throws Exception {
        require(((org.springframework.ai.openai.OpenAiChatOptions)main.getDefaultOptions()).getModel().equals("deepseek-flash"));
        require(((org.springframework.ai.openai.OpenAiChatOptions)completion.getDefaultOptions()).getModel().equals("deepseek-flash"));
        var cacheTemplate=new RedisTemplate<String,byte[]>();cacheTemplate.setConnectionFactory(connection);
        cacheTemplate.setKeySerializer(RedisSerializer.string());cacheTemplate.setValueSerializer(RedisSerializer.byteArray());cacheTemplate.afterPropertiesSet();
        var cache=new QueryEmbeddingCache(original,new RedisEmbeddingCacheStore(cacheTemplate),
                new EmbeddingCacheProperties(true,Duration.ofHours(24),.1,1024,"v1","school-live-fixture","exact-utf8-v1",Duration.ZERO),
                ()->"school-bge-m3-only",new io.micrometer.core.instrument.simple.SimpleMeterRegistry());cache.setRemoteGuard(guard);
        var vectors=SimpleVectorStore.builder(new QueryOnlyEmbeddingModel(original,cache)).build();
        String[] texts={
                "仅用于本地软件测试，不是真实学校政策。南京大学转软件工程的合成测试条件：大一学生可申请，需要完成高等数学课程。软件工程考试内容明确为Java基础和程序设计。软件工程机考备考应重点练习Java基础、程序设计和编程题训练。",
                "仅用于本地软件测试，不是真实学校政策。南京大学转计算机科学与技术的合成测试条件：大一学生可申请。计算机科学与技术专业考试内容明确为离散数学和算法设计，不采用软件工程的Java基础考试。",
                "仅用于本地软件测试，不是真实学校通知。南京大学图书馆合成测试开放时间：周六和周日均为上午9点至下午5点。"};
        String[] majors={"软件工程","计算机科学与技术",""};String[] departments={"软件学院","计算机学院","校园服务"};
        var documents=new ArrayList<com.yu.transferrag.entity.Document>();var points=new ArrayList<org.springframework.ai.document.Document>();
        for(int i=0;i<3;i++) {
            var doc=new com.yu.transferrag.entity.Document();doc.setId(9001L+i);doc.setTitle("Synthetic local fixture "+(i+1));doc.setSourceType("PERSONAL");doc.setDocumentRole(DocumentRole.EVIDENCE);doc.setYear(2026);doc.setScope("DEPARTMENT");doc.setDepartment(departments[i]);documents.add(doc);
            Map<String,Object> meta=new HashMap<>(Map.of("documentId",9001L+i,"chunkId",9101L+i,"chunkIndex",0,"documentRole","EVIDENCE","year",2026,"effectiveYear",2026,"sourceType","PERSONAL","scope","DEPARTMENT","department",departments[i],"major",majors[i]));
            points.add(org.springframework.ai.document.Document.builder().id(UUID.randomUUID().toString()).text(texts[i]).metadata(meta).build());
        }
        vectors.add(points); // One approved synthetic-corpus embedding batch; never production data.
        var aliases=mock(EntityAliasRepository.class);var aliasList=new ArrayList<EntityAlias>();
        for(int i=0;i<2;i++){var alias=new EntityAlias();alias.setStandardName(majors[i]);alias.setAlias(i==0?"软件专业":"计算机专业");alias.setEntityType("MAJOR");alias.setDepartment(departments[i]);aliasList.add(alias);}
        if(job.equals("verify-alias-v2"))aliasList.clear(); // Match confirmed production dictionary coverage for these subjects.
        when(aliases.findAll()).thenReturn(aliasList);var entities=new QueryRewriteService(aliases);
        var repository=mock(DocumentRepository.class);when(repository.findAllById(any())).thenAnswer(inv->{var ids=(Iterable<Long>)inv.getArgument(0);var selected=new ArrayList<com.yu.transferrag.entity.Document>();for(Long id:ids)documents.stream().filter(d->d.getId().equals(id)).forEach(selected::add);return selected;});
        when(repository.findReadinessByIds(anyCollection())).thenAnswer(inv->{Collection<Long> ids=inv.getArgument(0);return documents.stream().filter(d->ids.contains(d.getId())).map(d->{var view=mock(DocumentReadinessView.class);when(view.getDocumentId()).thenReturn(d.getId());when(view.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);when(view.getTaskId()).thenReturn(null);return view;}).toList();});
        var readiness=new EvidenceReadinessService(repository);var chunks=mock(ChunkRepository.class);when(chunks.findMaxEffectiveYear()).thenReturn(2026);when(chunks.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(any(),any(),any())).thenReturn(2026);
        var retrieval=new RetrievalService(vectors,entities,chunks,mock(PrecomputedVectorSearch.class),readiness);
        var rag=new RagService(retrieval,new AnswerabilityService(main),main,repository,mock(EvidenceRefRepository.class),false,readiness);
        var counter=new AtomicInteger();var completionMicros=new java.util.concurrent.atomic.AtomicLong();
        ChatModel timed=prompt->{long start=System.nanoTime();counter.incrementAndGet();try{return completion.call(prompt);}finally{completionMicros.set((System.nanoTime()-start)/1000);}};
        ObjectProvider<ChatModel> models=mock(ObjectProvider.class);when(models.getObject()).thenReturn(timed);
        var rewriter=new StandaloneQuestionRewriter(models,entities,properties);
        var store=new RedisConversationStore(new StringRedisTemplate(connection),properties);
        ObjectProvider<ConversationStore> stores=mock(ObjectProvider.class);when(stores.getIfAvailable()).thenReturn(store);
        var service=new ConversationService(stores,properties,rewriter,rag,readiness);
        String[] questions=job.equals("verify-alias-v2")?new String[]{"转软件工程机考要做什么准备？","那我该重点练习哪些内容？","我刚才说错了，是计算机科学与技术专业，考试考什么？","转软件工程机考要做什么准备？","那我该重点练习哪些内容？"}
                :job.equals("diagnose-v2")?new String[]{"转软件工程机考要做什么准备？","那我该重点练习哪些内容？"}
                :job.equals("verify-v2")?new String[]{"转软件工程机考要做什么准备？","那我该重点练习哪些内容？","我刚才说错了，是计算机科学与技术专业，考试考什么？","本地测试资料中，南京大学图书馆周末几点开放？","那考试呢","转软件工程机考要做什么准备？","那我该重点练习哪些内容？","本地测试资料中，南京大学转软件工程的考试考什么？"}
                :new String[]{"本地测试资料中，南京大学转软件工程需要哪些条件？","那考试考什么？","我刚才说错了，是计算机科学与技术专业，考试考什么？","本地测试资料中，南京大学图书馆周末几点开放？","那考试考什么？","本地测试资料中，南京大学转软件工程的考试考什么？"};
        long[] expectedDocs=job.equals("verify-alias-v2")?new long[]{9001,9001,9002,9001,9001}:job.equals("diagnose-v2")?new long[]{9001,9001}:job.equals("verify-v2")?new long[]{9001,9001,9002,9003,0,9001,9001,9001}:new long[]{9001,9001,9002,9003,0,9001};
        var session=store.create();String token=session.token(),nonce=session.requestId();
        int[] sequence=job.equals("legacy")?(resume?new int[]{5,1,2,3,4}:new int[]{0,1,2,3,4,5}):java.util.stream.IntStream.range(0,questions.length).toArray();
        for(int i:sequence) {
            final int index=i;
            if((i==4&&!job.equals("verify-alias-v2"))||(i==3&&job.equals("verify-alias-v2"))){var fresh=service.newConversation(token);token=(String)fresh.get("conversationToken");nonce=(String)fresh.get("nextRequestId");}
            if(job.equals("verify-v2")&&i==5){var fresh=service.newConversation(token);token=(String)fresh.get("conversationToken");nonce=(String)fresh.get("nextRequestId");}
            require(requests<questions.length);reserveBudget();requests++;persist();long start=System.nanoTime();int previous=counter.get();completionMicros.set(0);validationLogs.list.clear();
            Map<String,Object> safe=new LinkedHashMap<>();safe.put("case",i+1);safe.put("official_chat_model","deepseek-flash");
            try(var scope=RagDiagnostics.begin()) {
                try {
                    boolean legacy=job.equals("verify-v2")&&i==7;
                    var answer=legacy?rag.ask(questions[i]):service.ask(token,nonce,questions[i]);if(!legacy)nonce=answer.getNextRequestId();require(i!=4||job.equals("verify-alias-v2"));
                    safe.put("legacy_single_turn",legacy);
                    var markers=Pattern.compile("\\[(S\\d+)\\]").matcher(answer.getAnswer()).results().map(m->m.group(1)).collect(java.util.stream.Collectors.toSet());
                    var cited=answer.getSources().stream().map(SourceResponse::getCitationId).collect(java.util.stream.Collectors.toSet());
                    safe.put("status",200);safe.put("answer_nonempty",answer.getAnswer()!=null&&!answer.getAnswer().isBlank());
                    safe.put("citation_valid",!markers.isEmpty()&&cited.containsAll(markers));
                    safe.put("document_ids",answer.getSources().stream().map(SourceResponse::getDocumentId).toList());
                    results.add(safe);persist();
                    require(!markers.isEmpty()&&cited.containsAll(markers));
                    require(answer.getSources().stream().anyMatch(s->s.getDocumentId()==expectedDocs[index]));
                    if(i==0||i==1||i==2||i==5)require(answer.getSources().stream().allMatch(s->s.getDocumentId()==expectedDocs[index]));
                    require(!answer.getAnswer().isBlank());
                    boolean fact=(!job.equals("legacy")&&(i==0||i==1||i==5||i==6||i==7||(job.equals("verify-alias-v2")&&(i==3||i==4))))?answer.getAnswer().contains("Java")&&(answer.getAnswer().contains("程序设计")||answer.getAnswer().contains("编程"))
                            :switch(i){case 0->answer.getAnswer().contains("大一")&&answer.getAnswer().contains("高等数学");case 1,5->answer.getAnswer().contains("Java")&&answer.getAnswer().contains("程序设计");case 2->answer.getAnswer().contains("离散数学")&&answer.getAnswer().contains("算法");case 3->(answer.getAnswer().contains("9")||answer.getAnswer().contains("九"))&&(answer.getAnswer().contains("5")||answer.getAnswer().contains("17")||answer.getAnswer().contains("五"));default->false;};require(fact);
                    safe.put("status",200);safe.put("citation_valid",true);safe.put("fact_valid",true);safe.put("document_ids",answer.getSources().stream().map(SourceResponse::getDocumentId).toList());
                } catch(ConversationException error) {
                    safe.put("status",error.status());safe.put("error_code",error.code());
                    safe.put("validation_category",error.completionIssue().name());safe.put("completion_ms",completionMicros.get()/1000.0);safe.put("elapsed_ms",(System.nanoTime()-start)/1000000.0);
                    safe.put("validation_structure",validationLogs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList());
                    results.add(safe);persist();
                    if(i!=4||job.equals("verify-alias-v2"))System.out.println("SAFE_CASE="+JSON.writeValueAsString(safe));
                    require(i==4&&!job.equals("verify-alias-v2")&&error.status()==422&&error.code().equals("CONTEXT_REQUIRED"));nonce=error.nextRequestId();safe.put("status",422);safe.put("expected_clarification",true);
                }
                int calls=counter.get()-previous;require(calls==((i==1||i==2||(job.equals("verify-v2")&&i==6)||(job.equals("verify-alias-v2")&&i==4))?1:0));
                safe.put("completion_calls",calls);safe.put("completion_ms",completionMicros.get()/1000.0);safe.put("elapsed_ms",(System.nanoTime()-start)/1000000.0);safe.put("pass",true);
                persist();System.out.println("SAFE_CASE="+JSON.writeValueAsString(safe));
            }
        }
        System.out.println("SAFE_CACHE="+JSON.writeValueAsString(cache.stats()));
    }
}

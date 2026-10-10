package com.yu.transferrag.protection;

import com.openai.errors.*;
import com.openai.client.OpenAIClient;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.retry.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.embedding.*;
import org.springframework.ai.openai.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Remote boundaries only. No executor, fabricated fallback, or distributed/exactly-once claims. */
public final class AiRemoteGuard implements AutoCloseable {
    private final AiResilienceProperties p;private final MeterRegistry meters;
    private final Supplier<org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil.ResolvedConnectionProperties> embeddingConnection;
    private final Map<String,CircuitBreaker> breakers=new HashMap<>();private final Map<String,Bulkhead> bulkheads=new HashMap<>();
    private final Map<String,Retry> retries=new HashMap<>();
    // Identity keys and a short synchronized creation/close boundary; network calls never hold this lock.
    private final Map<EmbeddingModel,QueryClient> queryModels=new IdentityHashMap<>();
    private boolean closed;
    private record QueryClient(OpenAiEmbeddingModel model,OpenAIClient client) {}
    public AiRemoteGuard(AiResilienceProperties p,MeterRegistry meters) {
        this(p,meters,()->null);
    }
    public AiRemoteGuard(AiResilienceProperties p,MeterRegistry meters,
            Supplier<org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil.ResolvedConnectionProperties> embeddingConnection) {
        this.p=p;this.meters=meters;
        this.embeddingConnection=embeddingConnection;
        for(String name:List.of("deepseekChat","queryEmbedding")){
            var cb=CircuitBreaker.of(name,CircuitBreakerConfig.custom().slidingWindowSize(p.windowSize()).minimumNumberOfCalls(p.minimumCalls())
                    .failureRateThreshold(p.failureRate()).waitDurationInOpenState(p.openWait()).permittedNumberOfCallsInHalfOpenState(p.halfOpenCalls())
                    .ignoreException(e->!providerFailure(e)).build());
            breakers.put(name,cb);
            bulkheads.put(name,Bulkhead.of(name,BulkheadConfig.custom().maxConcurrentCalls(name.equals("deepseekChat")?p.chatConcurrency():p.embeddingConcurrency()).maxWaitDuration(Duration.ZERO).build()));
            // No chat retries: response loss may incur duplicate billable generation. Embedding 502/503/504 only, opt-in.
            Retry retry=Retry.of(name,RetryConfig.custom().maxAttempts(name.equals("queryEmbedding")?1+p.extraRetries():1)
                    .waitDuration(p.retryWait()).retryOnException(AiRemoteGuard::retryable).build());retries.put(name,retry);
            retry.getEventPublisher().onRetry(e->meters.counter("nju.ai.retry","model",name).increment());
            cb.getEventPublisher().onStateTransition(e->org.slf4j.LoggerFactory.getLogger(AiRemoteGuard.class)
                    .info("AI circuit state: model={} transition={}",name,e.getStateTransition()));
            meters.gauge("nju.ai.circuit.state",io.micrometer.core.instrument.Tags.of("model",name),cb,b->b.getState().getOrder());
        }
    }
    public boolean enabled(){return p.enabled();}
    public CircuitBreaker breaker(String name){return breakers.get(name);}
    static boolean providerFailure(Throwable e) {
        if(e instanceof OpenAIIoException)return true;
        return e instanceof OpenAIServiceException s && s.statusCode()>=500;
    }
    static boolean retryable(Throwable e){return e instanceof OpenAIServiceException s && Set.of(502,503,504).contains(s.statusCode());}
    static boolean timeout(Throwable e){for(int i=0;e!=null&&i<12;i++,e=e.getCause())if(e instanceof java.net.SocketTimeoutException||e instanceof java.net.http.HttpTimeoutException||e instanceof java.io.InterruptedIOException)return true;return false;}
    public <T>T call(String name,Supplier<T> remote){
        if(!p.enabled())return remote.get();
        var bulk=bulkheads.get(name);var cb=breakers.get(name);
        if(!bulk.tryAcquirePermission()){
            meters.counter("nju.ai.calls","model",name,"result","bulkhead_rejected").increment();
            throw new ProtectionException(503,"AI_BUSY","AI 服务繁忙，请稍后重试。",1);
        }
        long start=System.nanoTime();
        try {
            Supplier<T> operation=CircuitBreaker.decorateSupplier(cb,remote);
            T result=Retry.decorateSupplier(retries.get(name),operation).get();
            meters.counter("nju.ai.calls","model",name,"result","success").increment();return result;
        }catch(CallNotPermittedException e){
            meters.counter("nju.ai.calls","model",name,"result","not_permitted").increment();
            throw new ProtectionException(503,"AI_CIRCUIT_OPEN","AI 服务暂时不可用，请稍后重试。",Math.max(1,p.openWait().toSeconds()));
        }catch(OpenAIIoException|OpenAIServiceException e){
            meters.counter("nju.ai.calls","model",name,"result",timeout(e)?"timeout":e instanceof OpenAIIoException?"network_failure":((OpenAIServiceException)e).statusCode()>=500?"server_failure":"client_rejected").increment();
            throw e; // Existing stage-specific AI_SERVICE_UNAVAILABLE handling remains authoritative.
        }finally {bulk.onComplete();meters.timer("nju.ai.remote.duration","model",name).record(System.nanoTime()-start,TimeUnit.NANOSECONDS);}
    }
    public float[] embed(EmbeddingModel original,String query){
        return embed(original,query,m->m.embed(query));
    }
    public float[] embed(EmbeddingModel original,String query,java.util.function.Function<EmbeddingModel,float[]> invoke){
        if(!p.enabled())return invoke.apply(original);
        if(!(original instanceof OpenAiEmbeddingModel openai))throw new IllegalStateException("Protected query embedding requires OpenAiEmbeddingModel");
        // Separate query client: imports retain their original SDK configuration and frozen vectors.
        var model=queryModel(original,openai);
        return call("queryEmbedding",()->invoke.apply(model));
    }
    private synchronized OpenAiEmbeddingModel queryModel(EmbeddingModel original,OpenAiEmbeddingModel openai) {
        if(closed)throw new IllegalStateException("Query embedding protection is closed");
        QueryClient existing=queryModels.get(original);
        if(existing!=null)return existing.model();
        var options=OpenAiEmbeddingOptions.builder().from(openai.getOptions()).timeout(p.embeddingTimeout()).maxRetries(0).build();
        // Auto-config supplies a separate resolved SDK client; toOptions() omits connection credentials.
        // Resolve the same common/model-specific properties rather than falling back to SDK process env.
        var connection=embeddingConnection.get();
        if(connection!=null) {
            options=OpenAiEmbeddingOptions.builder().from(options)
                    .baseUrl(options.getBaseUrl()!=null?options.getBaseUrl():connection.getBaseUrl())
                    .apiKey(options.getApiKey()!=null?options.getApiKey():connection.getApiKey())
                    .credential(options.getCredential()!=null?options.getCredential():connection.getCredential())
                    .organizationId(options.getOrganizationId()!=null?options.getOrganizationId():connection.getOrganizationId())
                    .deploymentName(options.getMicrosoftDeploymentName()!=null?options.getMicrosoftDeploymentName():connection.getMicrosoftDeploymentName())
                    .microsoftFoundryServiceVersion(options.getMicrosoftFoundryServiceVersion()!=null?options.getMicrosoftFoundryServiceVersion():connection.getMicrosoftFoundryServiceVersion())
                    .microsoftFoundry(options.isMicrosoftFoundry()||connection.isMicrosoftFoundry())
                    .gitHubModels(options.isGitHubModels()||connection.isGitHubModels())
                    .proxy(options.getProxy()!=null?options.getProxy():connection.getProxy())
                    .customHeaders(options.getCustomHeaders()!=null&&!options.getCustomHeaders().isEmpty()?options.getCustomHeaders():connection.getCustomHeaders())
                    .build();
        }
        // Same setup as Spring AI's model builder, but retain ownership so context shutdown closes it.
        var client=org.springframework.ai.openai.setup.OpenAiSetup.setupSyncClient(options.getBaseUrl(),options.getApiKey(),
                options.getCredential(),options.getMicrosoftDeploymentName(),options.getMicrosoftFoundryServiceVersion(),
                options.getOrganizationId(),options.isMicrosoftFoundry(),options.isGitHubModels(),options.getModel(),
                options.getTimeout(),options.getMaxRetries(),options.getProxy(),options.getCustomHeaders(),
                io.micrometer.observation.ObservationRegistry.NOOP,null,List.of());
        try {
            var model=OpenAiEmbeddingModel.builder().openAiClient(client).options(options).build();
            queryModels.put(original,new QueryClient(model,client));return model;
        }catch(RuntimeException|Error failure){client.close();throw failure;}
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        RuntimeException failure=null;
        try {
            for(QueryClient owned:queryModels.values())try{owned.client().close();}
            catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        }finally {queryModels.clear();}
        if(failure!=null)throw new IllegalStateException("Query embedding client shutdown failed",failure);
    }
}

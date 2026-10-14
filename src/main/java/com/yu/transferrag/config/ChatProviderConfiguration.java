package com.yu.transferrag.config;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Changes only the bound Chat properties, before Spring AI constructs either SDK client. */
@Configuration(proxyBeanMethods = false)
public class ChatProviderConfiguration {
    static boolean official(Environment env) {
        String provider = env.getProperty("app.ai.chat.provider", "njusehub");
        if (!Set.of("njusehub", "deepseek-official").contains(provider)) {
            throw new IllegalArgumentException("Unsupported Chat provider");
        }
        return provider.equals("deepseek-official");
    }

    @Bean
    static BeanPostProcessor isolatedChatProvider(Environment env) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!official(env)) return bean;
                if (bean instanceof OpenAiCommonProperties common) {
                    rejectAuthHeaders(common.getCustomHeaders());
                    if (common.getCredential() != null || common.isMicrosoftFoundry() || common.isGitHubModels()) {
                        throw new IllegalArgumentException("Official Chat requires isolated Bearer authentication");
                    }
                }
                if (bean instanceof OpenAiChatProperties chat) {
                    rejectAuthHeaders(chat.getCustomHeaders());
                    if (chat.getCredential() != null || chat.isMicrosoftFoundry() || chat.isGitHubModels()) {
                        throw new IllegalArgumentException("Official Chat requires isolated Bearer authentication");
                    }
                    String key = required(env, "DEEPSEEK_API_KEY");
                    String endpoint = env.getProperty("app.ai.chat.deepseek.base-url", "https://api.deepseek.com");
                    URI uri = URI.create(endpoint);
                    boolean loopback = Set.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
                    if (!(loopback && "http".equals(uri.getScheme()))
                            && !("https".equals(uri.getScheme()) && "api.deepseek.com".equals(uri.getHost())
                            && (uri.getPort() == -1 || uri.getPort() == 443))) {
                        throw new IllegalArgumentException("Official Chat requires the DeepSeek HTTPS endpoint");
                    }
                    if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                            || !Set.of("", "/", "/v1", "/v1/").contains(uri.getPath())) {
                        throw new IllegalArgumentException("Invalid official Chat endpoint path");
                    }
                    String model = env.getProperty("app.ai.chat.deepseek.model", "deepseek-flash");
                    if (!Set.of("deepseek-flash", "deepseek-v4-pro").contains(model)) {
                        throw new IllegalArgumentException("Unsupported official DeepSeek Chat model");
                    }
                    chat.setBaseUrl(endpoint);
                    chat.setApiKey(key);
                    chat.setModel(model);
                    chat.setMaxRetries(0);
                    var body = new LinkedHashMap<String, Object>();
                    if (chat.getExtraBody() != null) body.putAll(chat.getExtraBody());
                    body.put("thinking", Map.of("type", "disabled"));
                    chat.setExtraBody(Map.copyOf(body));
                }
                if (bean instanceof OpenAiEmbeddingProperties embedding) {
                    rejectAuthHeaders(embedding.getCustomHeaders());
                    // Fail before any network request if an operator accidentally migrates the shared URL/key.
                    String endpoint = embedding.getBaseUrl();
                    String key = embedding.getApiKey();
                    if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()
                            || "api.deepseek.com".equalsIgnoreCase(URI.create(endpoint).getHost())
                            || key.equals(required(env, "DEEPSEEK_API_KEY"))
                            || !"bge-m3".equals(embedding.getModel())
                            || (embedding.getDimensions() != null && embedding.getDimensions() != 1024)
                            || env.getProperty("app.cache.embedding.dimensions", Integer.class, 1024) != 1024) {
                        throw new IllegalArgumentException("Official Chat requires a separate school bge-m3 connection");
                    }
                }
                return bean;
            }
        };
    }

    private static void rejectAuthHeaders(Map<String, String> headers) {
        if (headers != null && headers.keySet().stream().anyMatch(name ->
                Set.of("authorization", "proxy-authorization", "api-key", "x-api-key")
                        .contains(name.toLowerCase(java.util.Locale.ROOT)))) {
            throw new IllegalArgumentException("Authentication headers must use isolated model API-key properties");
        }
    }

    private static String required(Environment env, String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}

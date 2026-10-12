package com.yu.transferrag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transfer_rag_test;MODE=MySQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.openai.base-url=http://127.0.0.1:9",
        "spring.ai.openai.api-key=test-only-key",
        "spring.ai.openai.embedding.model=isolated-test-model",
        "app.import-tasks.enabled=false",
        "spring.ai.model.chat=none",
        "spring.autoconfigure.exclude="
                + "org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration"
})
@Import(TransferRagApplicationTests.TestBeans.class)
class TransferRagApplicationTests {
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext context;

    @Test
    void contextLoads() {
        org.junit.jupiter.api.Assertions.assertTrue(context.getBeansOfType(com.yu.transferrag.service.ImportTaskDispatcher.class).isEmpty());
        org.junit.jupiter.api.Assertions.assertNotNull(context.getBean(com.yu.transferrag.service.EvidenceReadinessService.class));
        org.junit.jupiter.api.Assertions.assertNotNull(context.getBean(com.yu.transferrag.service.PublicSourceService.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        VectorStore vectorStore() {
            return mock(VectorStore.class);
        }
    }

}

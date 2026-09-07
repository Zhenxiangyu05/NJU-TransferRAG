package com.yu.transferrag.service;

import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.entity.EntityAlias;
import com.yu.transferrag.repository.EntityAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private EntityAliasRepository entityAliasRepository;

    private QueryRewriteService queryRewriteService;

    @BeforeEach
    void setUp() {
        queryRewriteService = new QueryRewriteService(entityAliasRepository);
    }

    @Test
    void shouldExpandStandardNameWithAlias() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("光电系统信息材料实验班", "光电", "PROGRAM")
        ));

        String result = queryRewriteService.rewrite("光电系统信息材料实验班需要什么条件？");

        assertEquals("光电系统信息材料实验班（光电）需要什么条件？", result);
    }

    @Test
    void shouldExpandAliasWithStandardName() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("光电系统信息材料实验班", "光电", "PROGRAM")
        ));

        String result = queryRewriteService.rewrite("光电需要什么条件？");

        assertEquals("光电（光电系统信息材料实验班）需要什么条件？", result);
    }

    @Test
    void shouldPreferFullNameWhenAliasIsPartOfFullName() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("光电系统信息材料实验班", "光电", "PROGRAM"),
                alias("软件学院", "软院", "DEPARTMENT")
        ));

        String result = queryRewriteService.rewrite("光电系统信息材料实验班和软件学院的要求");

        assertEquals("光电系统信息材料实验班（光电）和软件学院（软院）的要求", result);
    }

    @Test
    void shouldKeepQueryWhenNoEntityMatches() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("软件学院", "软院", "DEPARTMENT")
        ));

        String result = queryRewriteService.rewrite("食堂今天有什么菜？");

        assertEquals("食堂今天有什么菜？", result);
    }

    @Test
    void shouldReturnDepartmentFromMatchedDatabaseEntity() {
        EntityAlias entityAlias = alias("光电系统信息材料实验班", "光电", "PROGRAM");
        entityAlias.setDepartment("现代工程学院");
        when(entityAliasRepository.findAll()).thenReturn(List.of(entityAlias));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("光电需要什么条件？");

        assertEquals("光电（光电系统信息材料实验班）需要什么条件？", result.rewrittenQuery());
        assertEquals(1, result.matchedEntities().size());
        assertEquals("光电系统信息材料实验班", result.matchedEntities().getFirst().standardName());
        assertEquals("PROGRAM", result.matchedEntities().getFirst().entityType());
        assertEquals("现代工程学院", result.matchedEntities().getFirst().department());
    }

    @Test
    void shouldRecognizeDepartmentFromStandardNameWithoutAlias() {
        EntityAlias entityAlias = alias("汉语言文学", null, "MAJOR");
        entityAlias.setDepartment("文学院");
        when(entityAliasRepository.findAll()).thenReturn(List.of(entityAlias));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("汉语言文学转专业需要什么条件？");

        assertEquals("汉语言文学转专业需要什么条件？", result.rewrittenQuery());
        assertEquals(1, result.matchedEntities().size());
        assertEquals("汉语言文学", result.matchedEntities().getFirst().standardName());
        assertEquals("文学院", result.matchedEntities().getFirst().department());
    }

    @Test
    void shouldResolveExplicitSingleYear() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "2025年软件学院转专业需要什么条件？"
        );

        assertEquals(2025, result.explicitYear());
        assertEquals(2025, result.resolvedYear());
        assertEquals(false, result.multiYearQuery());
    }

    @Test
    void shouldResolveCurrentYearExpression() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "今年软件学院转专业需要什么条件？"
        );

        assertEquals(null, result.explicitYear());
        assertEquals(LocalDate.now().getYear(), result.resolvedYear());
        assertEquals(false, result.multiYearQuery());
    }

    @Test
    void shouldRecognizeMultipleDifferentYears() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "2025和2026软件学院转专业政策有什么区别？"
        );

        assertEquals(null, result.explicitYear());
        assertEquals(null, result.resolvedYear());
        assertEquals(true, result.multiYearQuery());
    }

    private EntityAlias alias(String standardName, String alias, String entityType) {
        EntityAlias entityAlias = new EntityAlias();
        entityAlias.setStandardName(standardName);
        entityAlias.setAlias(alias);
        entityAlias.setEntityType(entityType);
        return entityAlias;
    }
}

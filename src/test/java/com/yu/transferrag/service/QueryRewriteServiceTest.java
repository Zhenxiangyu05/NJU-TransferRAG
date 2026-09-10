package com.yu.transferrag.service;

import com.yu.transferrag.dto.EntityRole;
import com.yu.transferrag.dto.ApplicantStage;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void shouldRecognizeExperienceQueryByKeyword() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "数学学院保研有什么经验？"
        );

        assertTrue(result.experienceQuery());
    }

    @Test
    void shouldNotClassifyPolicyQueryAsExperienceQuery() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "软件学院转专业有哪些准入课程？"
        );

        assertFalse(result.experienceQuery());
    }

    @Test
    void shouldResolveMajorToCanonicalDepartment() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("计算机科学与技术", null, "MAJOR", "计算机学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "2026年计算机科学与技术转专业需要什么条件？"
        );

        assertEquals(List.of("计算机学院"), result.departments());
        assertEquals(List.of("计算机科学与技术"), result.majors());
        assertEquals(2026, result.explicitYear());
    }

    @Test
    void shouldApplyLongestMatchToResolvedEntities() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("光电信息科学与工程", "光电信息类", "MAJOR", "现代工程与应用科学学院"),
                alias("光电系统信息材料实验班", "光电", "PROGRAM", "现代工程与应用科学学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("光电信息类怎么转专业？");

        assertEquals(1, result.matchedEntities().size());
        assertEquals("光电信息科学与工程", result.matchedEntities().getFirst().standardName());
        assertEquals(List.of("光电信息科学与工程"), result.majors());
    }

    @Test
    void shouldResolveMultipleTargetEntities() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("软件学院", "软院", "DEPARTMENT", "软件学院"),
                alias("电子科学与工程学院", "电子学院", "DEPARTMENT", "电子科学与工程学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("软院和电子学院怎么选？");

        assertEquals(List.of("软件学院", "电子科学与工程学院"), result.departments());
        assertEquals(2, result.resolvedEntities().size());
    }

    @Test
    void shouldNotUseExcludedEntityAsDepartmentFilter() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("软件学院", "软院", "DEPARTMENT", "软件学院"),
                alias("电子科学与工程学院", "电子学院", "DEPARTMENT", "电子科学与工程学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext(
                "软院之外，电子学院转专业有什么要求？"
        );

        assertEquals(List.of("电子科学与工程学院"), result.departments());
        assertEquals(EntityRole.EXCLUDED, result.resolvedEntities().getFirst().role());
        assertEquals(EntityRole.TARGET, result.resolvedEntities().get(1).role());
        assertTrue(result.rewrittenQuery().contains("软院（软件学院）之外"));
    }

    @Test
    void shouldRecognizePrefixExclusionAndComparisonRoles() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("软件学院", "软院", "DEPARTMENT", "软件学院"),
                alias("电子科学与工程学院", "电子学院", "DEPARTMENT", "电子科学与工程学院")
        ));

        QueryRewriteResult excluded = queryRewriteService.rewriteWithContext("除了软院，电子学院呢？");
        QueryRewriteResult comparison = queryRewriteService.rewriteWithContext("相比软院，电子学院如何？");

        assertEquals(EntityRole.EXCLUDED, excluded.resolvedEntities().getFirst().role());
        assertEquals(EntityRole.COMPARISON, comparison.resolvedEntities().getFirst().role());
        assertEquals(List.of("电子科学与工程学院"), excluded.departments());
        assertEquals(List.of("电子科学与工程学院"), comparison.departments());
    }

    @Test
    void shouldKeepBareElectronicAndOpticalExpressionsAmbiguous() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("电子和光电怎么选？");

        assertTrue(result.departments().isEmpty());
        assertEquals(List.of("电子", "光电"), result.ambiguousEntities());
        assertTrue(result.resolvedEntities().stream()
                .allMatch(entity -> entity.role() == EntityRole.AMBIGUOUS));
    }

    @Test
    void shouldRecognizeAliasWithoutCollegeSuffix() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("现代工程与应用科学学院", "现工", "DEPARTMENT", "现代工程与应用科学学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("现工转专业怎么准备？");

        assertEquals(List.of("现代工程与应用科学学院"), result.departments());
    }

    @Test
    void shouldKeepSoftwareMajorDistinctFromSoftwareCollege() {
        when(entityAliasRepository.findAll()).thenReturn(List.of(
                alias("软件工程", null, "MAJOR", "软件学院"),
                alias("软件学院", "软院", "DEPARTMENT", "软件学院")
        ));

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("软件工程转专业要求");

        assertEquals(1, result.matchedEntities().size());
        assertEquals("软件工程", result.matchedEntities().getFirst().standardName());
        assertEquals(List.of("软件工程"), result.majors());
    }

    @Test
    void shouldResolveFirstYearInExplicitPolicyCycle() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("2026年大一转专业条件");

        assertTrue(result.policyQuery());
        assertEquals(2026, result.cycleYear());
        assertEquals(2025, result.cohortYear());
        assertEquals(ApplicantStage.FIRST_YEAR, result.applicantStage());
    }

    @Test
    void shouldResolveSecondYearInExplicitPolicyCycle() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("2026年大二转专业条件");

        assertEquals(2026, result.cycleYear());
        assertEquals(2024, result.cohortYear());
        assertEquals(ApplicantStage.SECOND_YEAR, result.applicantStage());
    }

    @Test
    void shouldNotForceCohortWhenPolicyStageIsAbsent() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("2026年转专业条件");

        assertEquals(2026, result.cycleYear());
        assertEquals(null, result.cohortYear());
        assertEquals(null, result.applicantStage());
    }

    @Test
    void shouldNotInterpretAcademicStageOutsidePolicyContext() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("2026年大一课程怎么规划？");

        assertFalse(result.policyQuery());
        assertEquals(null, result.cycleYear());
        assertEquals(null, result.cohortYear());
        assertEquals(null, result.applicantStage());
    }

    @Test
    void shouldResolveExplicitCohortOutsidePolicyContext() {
        when(entityAliasRepository.findAll()).thenReturn(List.of());

        QueryRewriteResult result = queryRewriteService.rewriteWithContext("2024级数理大类分流比例");

        assertFalse(result.policyQuery());
        assertEquals(null, result.cycleYear());
        assertEquals(2024, result.cohortYear());
    }

    private EntityAlias alias(String standardName, String alias, String entityType) {
        EntityAlias entityAlias = new EntityAlias();
        entityAlias.setStandardName(standardName);
        entityAlias.setAlias(alias);
        entityAlias.setEntityType(entityType);
        return entityAlias;
    }

    private EntityAlias alias(String standardName,
                              String alias,
                              String entityType,
                              String department) {
        EntityAlias entityAlias = alias(standardName, alias, entityType);
        entityAlias.setDepartment(department);
        return entityAlias;
    }
}

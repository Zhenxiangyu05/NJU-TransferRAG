#!/usr/bin/env python3
"""Apply the evidence-verified review of the remaining frozen 82-case Gold.

This script is intentionally one-shot and fails unless the prior Gold is exactly
24 confirmed / 58 pending. It reads local copies of the 20 production Evidence
documents' metadata; production DocumentRole was checked separately by SQL.
It never calls the RAG service or changes test-cases.json.
"""

from __future__ import annotations

import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / "evaluation/test-cases.json"
GOLD = ROOT / "evaluation/benchmark/gold-labels.json"
AUDIT = ROOT / "evaluation/benchmark/full-gold-review.md"
SOURCE_MAP = Path("D:/transfer-rag-data/canonical-generated/canonical-source-map.json")
FROZEN_TEST_CASES_SHA256 = "0205eec24168e405c86ee1a57acb7b74ee568c5b596ce7ad41b82c3a1c48e299"
EVIDENCE_IDS = [1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21]

# Each listed ID was checked against the current Document row and its original
# Evidence file. No historical citation ID or Canonical document is reused.
POSITIVE_BY_DOCUMENT = {
    10: [10],
    11: [11, 12, 13, 14],
    20: [16, 17, 18, 19, 20, 22, 23, 25, 26, 27, 28, 29],
    9: [30, 33],
    17: [35, 36],
    12: [40, 41, 42, 43],
    19: [50, 51],
    8: [52, 53, 54, 55, 56],
    16: [57, 58, 59],
    15: [64, 66, 67],
    14: [68, 70, 71, 72, 73, 76, 77],
    13: [78, 79, 80, 81],
}
NEGATIVE_REASONS = {
    8: "当前库的汉文二次选拔资料只直接描述先前年份流程，不能把初复试时长外推为2026年安排。",
    31: "现有入库 Evidence 没有2023级人文大类110人的五项分流人数；原题对应文件未入库。",
    44: "工科试验班生存指南未作为当前 Evidence 导入；其他试验班资料不能替代其2026级方向清单。",
    45: "当前 Evidence 没有原工科试验班指南关于转专业优势的论证；不能用其他大类经验替代。",
    46: "当前 Evidence 没有2026级工试分流现工院的三选一课程要求。",
    60: "Document 16 虽以计算机命名，正文实际讨论人工智能；当前库缺少转计算机微积分与笔试机试平衡的证据。",
    61: "当前库没有可核实的转CS笔试两小时四题及数学题范围的 Evidence。",
    62: "当前库没有可核实的转CS机试三题题型与分数的 Evidence。",
    63: "当前库没有足以支持转计算机四类困难的 Evidence；Document 16 的正文是转AI风险。",
}

PERSONAL_SCOPE = {
    8: "据更新至2025年的个人法学转专业指南，",
    9: "据2025年个人汉文转专业指南，",
    10: "据2026年个人汉文分流指南，",
    11: "据以2024级课程为参考的个人化生大类手册，",
    12: "据个人地学大类生存指南，",
    13: "据以2023级培养方案为背景的个人南赫手册，",
    14: "据2026年个人转电子指南，",
    15: "据2026年个人转光电概述，",
    16: "据个人整理的转AI风险材料，",
    17: "据以2025级经验为主的个人技科指南，",
    19: "据2025级个人数理大类指南，",
}
OFFICIAL_SCOPE = {
    16: "2026准入计划中2025级汉语言文学条目：",
    17: "2026准入计划中2025级法学条目：",
    18: "2026准入计划中2025级数学类条目：",
    19: "2026准入计划中2025级数学类条目：",
    20: "2026准入计划中2025级物理学类条目：",
    22: "2026准入计划中2025级人工智能条目：",
    23: "2026准入计划中2024级软件工程条目：",
    25: "2026准入计划中2024级计算机科学与技术条目：",
    26: "2026准入计划中计算机学院综合考核条目：",
    27: "2026准入计划中2024级软件工程条目：",
    28: "2026准入计划中2025级社会工作条目：",
    29: "2026准入计划中2025级信息管理与信息系统条目：",
}

# Complete replacements. Unlisted positive cases keep their original fact list,
# with an explicit source/cohort qualifier applied to each fact below.
CORRECTED_FACTS = {
    16: ["第一学期全部课程平均学分绩须达4.0（含）以上", "课程无不及格记录"],
    19: ["校内数学竞赛成绩可以计入准入考核附加分",
         "修读数学分析、高等代数、解析几何路线，对应数学专业类竞赛附加分",
         "修读第一层次微积分I/II、线性代数路线，对应非数学专业A类竞赛附加分"],
    23: ["计算系统基础、C语言程序设计基础、软件工程与计算I、离散数学四门中至少修读两门并取得学分"],
    25: ["离散数学，以及程序设计基础或计算机程序的构造和解释",
         "两门准入课程本学期结束须取得学分且成绩均达80分（含）以上"],
    27: ["微积分I、微积分II、线性代数须为第一层次并取得学分",
         "计算系统基础、C语言程序设计基础、软件工程与计算I、离散数学中至少两门取得学分"],
    30: ["人文大类分流方向包括历史、哲学、新闻传播、汉语国际教育",
         "2025年汉语言文学回到人文大类分流，指南估计约有五个名额"],
    33: ["指南建议阅读两三本经典原著、四五本相关衍生著作，并参考学术论文或书籍",
         "指南强调耐心阅读、提炼自己的观点并培养文学思考与表达能力"],
    50: ["按指南往年经验，数学分流核心课平均学分绩约4.2可进入数学学院",
         "指南回顾2024级因考试较难约降到4.1；这不是固定准入线"],
    58: ["AI专业课程不能直接替代其他专业课程，其他专业课程也不能直接替代AI课程",
         "若转AI失败，所修AI数学分析仍可能需要补修通修微积分，作者认为失败代价较大"],
    64: ["个人概述转述的准入文件要求：普通物理（力学）、大学化学、普通物理（热学）三门中至少已修或在修一门，并在学期结束取得学分"],
    70: ["面试包括限时一分钟自我陈述，随后老师轮流提问",
         "提问可能涉及大学物理、电路分析、模拟电路、C语言，也可能根据陈述追问"],
    71: ["指南建议利用往年卷、课后习题和考前习题课复习",
         "指南建议大学物理重视书后习题"],
    76: ["五门专业导学课中至少选修一门；其中四门对外开放",
         "导学课在鼓楼开设，计一学分",
         "选择某门导学课不限制之后的专业分流方向"],
}

SUPPORT = {
    10: "指南明确写明文学院不接受大二学年的转专业申请。",
    11: "课程介绍列出大一上微积分、化学实验基础、普通生物学上、大学化学A。",
    12: "大一下生科方向课程列表明确列出Python与C语言二选一。",
    13: "大一下化学方向课程列表逐项列出数学、编程、化学实验、普通物理和马原。",
    14: "补充章节直接列出十小时基础劳育、每学期十小时志愿与劳育考试。",
    16: "官方2025级汉文行载有平均学分绩4.0和无不及格记录。",
    17: "官方2025级法学行列出四选一准入课并要求取得学分。",
    18: "官方2025级数学类行给出数分/高代/解析几何与第一层次微积分/线代两套路径。",
    19: "官方数学类行按两套修课路径分别对应数学专业类与非数学专业A类竞赛附加分。",
    20: "官方2025级物理学类行列出三门第一层次数学和力学、热学。",
    22: "官方2025级人工智能行明确资格审核后的综合考核为面试。",
    23: "四门中至少两门来自官方2024级软件工程行；2025级行另有不同条件。",
    25: "离散数学加程序设计基础或SICP、80分门槛来自官方2024级计算机行。",
    26: "官方计算机学院准入方法列明笔试、机试、面试和任一项不及格不录取。",
    27: "第一层次数学三门与四门专业课中至少两门来自官方2024级软件工程行。",
    28: "官方2025级社会工作行规定先取得导论学分，再在修三门选一。",
    29: "官方2025级信息管理与信息系统行列出四门课任意一门；2024级第四门不同。",
    30: "个人图像PDF列举人文分流方向，并说明2025年汉文回到人文大类分流。",
    33: "个人图像PDF提出原著、衍生著作与论文阅读建议，强调形成自己的文学观点。",
    35: "技科个人指南写明通常大一鼓楼、大二起苏州。",
    36: "指南明确以2025级经验为主，分流加权课程含微积分、线代和信息科学中的物理学。",
    40: "地学个人指南列出分流只看上学期英语、数学、大学化学A、地学导论四门。",
    41: "地学个人指南附有2023级五项分流/转出人数。",
    42: "指南写明地海拔尖需要第一层次数学，而大类分流只需第二层次。",
    43: "指南建议按目标专业要求选数学层次，并提前安排线代。",
    50: "个人数理指南以往年经验回顾4.2及2024级约4.1，均非官方固定线。",
    51: "个人指南分别讨论解析几何、数学分析、高等代数的复习投入。",
    52: "个人法学指南的前期流程列出四选一准入课、笔试面试及综合择优。",
    53: "个人法学指南直接列出四门法学院准入课。",
    54: "个人法学指南的笔试节列出三科各50分、三选二。",
    55: "个人法学指南的面试节写明自我介绍、专业知识、英语口语。",
    56: "指南说明大多数人选择民法与刑法，建议结合教材笔记和往年题准备。",
    57: "Document 16标题涉及计算机，正文实为AI；列有数学分析、高代、程序设计与离散数学周学时。",
    58: "正文直接讨论AI课程互不替代及转入失败后补修通修微积分。",
    59: "正文以个人经验描述AI面试偏闲聊但仍可能淘汰申请者。",
    64: "个人光电概述转述准入原文件的三选一课程要求；不得将该二手资料标为OFFICIAL。",
    66: "个人概述称学院看重数学物理成绩，面试官可能看第一学期并问第二学期期中成绩。",
    67: "个人概述列出2026级光材面试自述及一分钟准备后的题目示例。",
    68: "2026个人电子指南列出六门课总评均不低于70分。",
    70: "指南写明一分钟陈述后轮流提问，没有3–4分钟提问时长依据。",
    71: "指南写有往年卷、课后习题与考前习题课；原‘考前指导’需要改写。",
    72: "指南2026版列出六门一层次数学/物理/电路课及70分总评要求。",
    73: "指南描述2026年5月底面试、一分钟陈述及老师轮流提问。",
    76: "指南列出五门至少修一门、四门对外、鼓楼一学分且不限制分流。",
    77: "指南同一章节支持六门准入课70分及面试陈述和提问范围。",
    78: "南赫个人手册的培养体系节列出六类课程；附录基于2023级培养方案。",
    79: "手册微积分II节讨论计算与应用、作业/quiz和微积分I基础。",
    80: "手册选课建议写明25以内为宜、最多30学分、考试课10门以内。",
    81: "手册大气电学节列出观测、数值模拟、少量室内实验和摄像机/天线阵。",
}

REFERENCE_ANSWERS = {
    10: "据2026年个人汉文分流指南，文学院不接受大二学年的汉语言文学转专业申请；请以当年学院通知核实。",
    11: "据以2024级课程为参考的个人化生手册，大一上主要有微积分I（二层次）、大学化学实验基础、普通生物学（上）和大学化学A。",
    12: "据该个人化生手册，分流生科方向的大一下编程课为Python程序设计和C语言程序设计二选一。",
    17: "2026准入计划的2025级法学条目要求已修或在修法理学导论、刑法学（总论一）、民法学（总则）、宪法学任一门，并于学期末取得学分。",
    23: "2026准入计划中2024级软件工程条目列出计算系统基础、C语言程序设计基础、软件工程与计算I、离散数学，要求其中至少两门取得学分；2025级条目另有要求。",
    26: "2026准入计划的计算机学院准入方法包括资格审核后笔试、机试、面试；三项中任一不及格者不予录取。",
    27: "2026准入计划中2024级软件工程条目要求第一层次微积分I、微积分II和线性代数取得学分，并在四门软件工程学科基础课中至少两门取得学分。",
    28: "2026准入计划中2025级社会工作条目要求申请时已取得社会与心理科学导论学分，并在修社会学概论、社会工作概论、心理学概论（上）中的任一门。",
    43: "据个人地学指南，转专业选课应对应目标专业要求的数学层次；若目标专业大一需要线代，宜提前安排上学期修读。",
    56: "据更新至2025年的个人法学指南，多数笔试考生选择民法与刑法，作者建议大一下重视这两门，结合教材、笔记和往年题复习。",
    67: "据2026年个人光电概述，该次光材二次拔尖面试先自我介绍，之后谈高中印象深刻的事，再从多个问题中选答并有一分钟准备；举例涉及光学隐身、信息材料、海市蜃楼和冷热杯破裂。",
    70: "据2026年个人转电子指南，面试先限时一分钟自我陈述，再由老师轮流提问，可能涉及大学物理、电路分析、模拟电路和C语言；资料未给出固定提问时长。",
    72: "据2026年个人转电子指南，大一转电子的六门准入课是一层次微积分I/II、大学物理I/II、电路分析、模拟电路，六门总评都不得低于70分。",
    73: "据2026年个人转电子指南，当年面试约在5月底；流程为一分钟个人陈述后老师轮流提问，问题可能涉及大物、电分、模电或C语言。",
    76: "据2026年个人转电子指南，五门专业导学课中至少修一门，四门对外开放；课程在鼓楼开设、计一学分，选修某门不限制后续分流方向。",
    77: "据2026年个人转电子指南，六门准入课总评均不低于70分；面试包括一分钟陈述和老师提问，可能问大物、电分、模电或C语言。",
    80: "据个人南赫手册，一学期选课以25学分以内为宜、最多30学分，考试课程建议不超过10门。",
}


def main() -> None:
    if hashlib.sha256(DATASET.read_bytes()).hexdigest() != FROZEN_TEST_CASES_SHA256:
        raise SystemExit("Frozen test-cases.json changed; refusing to write Gold")
    cases = json.loads(DATASET.read_text(encoding="utf-8"))
    gold = json.loads(GOLD.read_text(encoding="utf-8"))
    if len(cases) != 82 or len(gold["cases"]) != 82:
        raise SystemExit("Expected 82 frozen cases")
    pending = {row["caseId"] for row in gold["cases"] if row["reviewStatus"] == "NEEDS_REVIEW"}
    if len(pending) != 58:
        raise SystemExit(f"Expected exactly 58 pending cases; found {len(pending)}")
    positive = {f"TRAG-{case:03d}": document
                for document, case_ids in POSITIVE_BY_DOCUMENT.items() for case in case_ids}
    negative = {f"TRAG-{case:03d}" for case in NEGATIVE_REASONS}
    if pending != set(positive) | negative or set(positive) & negative:
        raise SystemExit("Review decisions do not cover each pending case exactly once")
    source_rows = {row["sourceDocumentId"]: row
                   for row in json.loads(SOURCE_MAP.read_text(encoding="utf-8"))}
    if set(source_rows) != set(EVIDENCE_IDS):
        raise SystemExit("Source map differs from the production Evidence ID audit")
    source_cases = {row["caseId"]: row for row in cases}
    at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    for row in gold["cases"]:
        case_id = row["caseId"]
        if case_id not in pending:
            continue
        number = int(case_id[-3:])
        row.update(reviewStatus="CONFIRMED", reviewSource="evidence_verified_full_review",
                   reviewedAt=at, referenceAnswer=REFERENCE_ANSWERS.get(number),
                   suggestedAnswerable=None, suggestedExpectedDocumentIds=[])
        if case_id in negative:
            row.update(answerable=False, expectedDocumentIds=[], expectedFactsStatus="NEEDS_FIX",
                       expectedFactsOverride=[], verifiedEvidence=[],
                       reviewedEvidenceScope={"documentRole": "EVIDENCE", "documentIds": EVIDENCE_IDS},
                       reviewerNotes=NEGATIVE_REASONS[number],
                       notes="冻结知识库无足够 Evidence；该判断不代表现实世界无答案。")
            continue
        document_id = positive[case_id]
        info = source_rows[document_id]
        if not Path(info["sourceFile"]).is_file() or info["sourceType"] not in {"PERSONAL", "OFFICIAL_PDF"}:
            raise SystemExit(f"Unusable Evidence metadata for {case_id}")
        facts = CORRECTED_FACTS.get(number, source_cases[case_id]["expectedFacts"])
        scope = PERSONAL_SCOPE.get(document_id) if info["sourceType"] == "PERSONAL" else OFFICIAL_SCOPE.get(number)
        if not scope:
            raise SystemExit(f"Missing source/cohort scope for {case_id}")
        resolved = [scope + fact for fact in facts]
        if not resolved or any(not fact.strip() for fact in resolved):
            raise SystemExit(f"No resolved Gold facts for {case_id}")
        row.update(answerable=True, expectedDocumentIds=[document_id], expectedFactsStatus="SUPPORTED",
                   expectedFactsOverride=resolved,
                   verifiedEvidence=[{"documentId": document_id, "title": info["title"],
                                      "documentRole": "EVIDENCE", "sourceType": info["sourceType"],
                                      "year": info["year"], "department": info["department"]}],
                   reviewerNotes=SUPPORT[number],
                   notes="依据当前 Evidence 原文；个人资料和年级适用范围均在完整 fact override 中限定。")
    counts = Counter(row["reviewStatus"] for row in gold["cases"])
    if counts != {"CONFIRMED": 82}:
        raise SystemExit(f"Unexpected final status counts: {counts}")
    gold["policy"] = ("CONFIRMED requires manual review or evidence_verified review with current Evidence IDs; "
                      "evidence-relative refusals require the audited Evidence scope.")
    GOLD.write_text(json.dumps(gold, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# Full Gold Evidence review", "",
             "Frozen production DocumentRole audit: Evidence IDs 1–5, 7–21; Canonical IDs 6, 22–28.",
             "The 24 existing confirmations are retained. The remaining 58 decisions are recorded below.", "",
             "| Case | Answerable | Evidence ID | SourceType | Fact override | Basis |",
             "|---|---|---:|---|---|---|"]
    for row in gold["cases"]:
        if row.get("reviewSource") != "evidence_verified_full_review":
            continue
        evidence = row["verifiedEvidence"]
        lines.append(f"| {row['caseId']} | {str(row['answerable']).lower()} | "
                     f"{evidence[0]['documentId'] if evidence else '—'} | "
                     f"{evidence[0]['sourceType'] if evidence else '—'} | "
                     f"{'yes' if row['answerable'] else 'empty'} | {row['reviewerNotes']} |")
    AUDIT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"total": 82, "newlyReviewed": 58, "confirmed": 82,
                      "answerable": sum(row["answerable"] is True for row in gold["cases"]),
                      "unanswerable": sum(row["answerable"] is False for row in gold["cases"]),
                      "referenceAnswers": sum(bool(row.get("referenceAnswer")) for row in gold["cases"])},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()

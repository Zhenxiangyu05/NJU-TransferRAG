# Public Source Audit (read-only, 2026-10-08)

The 20 current Evidence document files were hashed on the production host; only metadata and SHA-256 digests were returned. No document bytes were copied. Original publication URLs are not recorded in the available metadata. Public redistribution permission is unconfirmed for every item, so all remain `PENDING` (default deny).

| Document ID | Title | SourceType | FileType | SHA-256 | FileExists | Original URL | PublicAuthorizationStatus |
|---:|---|---|---|---|---|---|---|
| 1 | 2026转软件工程常见问题 | PERSONAL | MD | `22aa8f44419d06a9edc1835d0327d0553bd4cffdd104cf330f4d24dfe989ea31` | yes | not recorded | PENDING |
| 2 | 2026转软件工程机考准备 | PERSONAL | MD | `c6c0e88b3a46d6be2d5a945e76b4a3d16dc83e649cd3bd3919f21a057c0eae61` | yes | not recorded | PENDING |
| 3 | 2026转软件工程面试情况 | PERSONAL | MD | `a93d4a8c5ac8a67227f6c5e78dcd35c50568d134c4de5eb53b06b335081d3f82` | yes | not recorded | PENDING |
| 4 | 2026转软件工程申请要求 | PERSONAL | MD | `55b5ee13d39f548350346b020cd64a8ee99ca77a2740e3ac758e047bde52af45` | yes | not recorded | PENDING |
| 5 | 2026转软件工程时间安排 | PERSONAL | MD | `d452a4dd67ab371a0008c32989f46067fadd4c955dbd3b4bf30e4061d9a04aca` | yes | not recorded | PENDING |
| 7 | 2026智能科学与技术培养方案 | OFFICIAL_PDF | PDF | `074bb87bcc6c74e84b4e1807c9fe1ba65228a16cc377241a32b0f27ee3fe7712` | yes | not recorded | PENDING |
| 8 | 法学院转专业指南 | PERSONAL | DOCX | `8585a9535efba6d5c0e6b499d3632dc5f70095143793c2201be2ce1aab675419` | yes | not recorded | PENDING |
| 9 | 文学院汉语言文学转专业指南 | PERSONAL | PDF | `19d632697a3c68655d4ce438337074218f59c909fb17f00e964ea358876ee885` | yes | not recorded | PENDING |
| 10 | 人文大类汉语言文学分流指南 | PERSONAL | PDF | `36b8000b768409cf50cae5071d69b427b0198d2663f5cdf354e081df4e060802` | yes | not recorded | PENDING |
| 11 | 化学与生命科学类生存手册 | PERSONAL | PDF | `12c005d80cd9108dba6599f6638fb4c97e822284bf6b8a13a8f992d80aea0551` | yes | not recorded | PENDING |
| 12 | 地球科学与资源环境大类生存指南 | PERSONAL | DOCX | `53773e79996cfcc63b0ba073b12fe284debcf5ab880657560fef84c8a0ac2656` | yes | not recorded | PENDING |
| 13 | 南京大学赫尔辛基大气学院生存手册 | PERSONAL | PDF | `b58bca5bef6c447ea1ae7261086faf52191febbe4a6798b923518ba6bf3c50ec` | yes | not recorded | PENDING |
| 14 | 转电子指南 | PERSONAL | PDF | `e181b2239197dd655e8d36fb05801cd93abf4ac4d5d42675be7e7279febe8ba9` | yes | not recorded | PENDING |
| 15 | 转光电试验班概述 | PERSONAL | DOCX | `69a1bd202611539f8834ffdeaf6f59d242c57c3292100756bf11b90ad5794e14` | yes | not recorded | PENDING |
| 16 | 转计算机科学与技术专业的难处 | PERSONAL | MD | `65f5731314fc2e7e84afff20a0c03d285b01af1da4a86e6b827e8510e9f25b81` | yes | not recorded | PENDING |
| 17 | 技术科学试验班生存指南 | PERSONAL | PDF | `3b850451f106002b7359f8d4b3dd8bf67fbdfaeff7136f9ddb58d6d6f4cfb61b` | yes | not recorded | PENDING |
| 18 | 数学学院生存手册 | PERSONAL | PDF | `900a19682437b3dbda16d39fbbb190afbbb549e8dbe79dda6ce69becce039316` | yes | not recorded | PENDING |
| 19 | 数理科学类生存指南 | PERSONAL | PDF | `aed662f1f6fdfb2106805a151e2cf42fed724ecb863f2fc61477dbeb61365875` | yes | not recorded | PENDING |
| 20 | 南京大学2026转专业准入计划表 | OFFICIAL_PDF | PDF | `3f33f4904e980f559ee408d1c17b3b918be11473025bddee8e20b5c69849b47e` | yes | not recorded | PENDING |
| 21 | 匡亚明学院生存指南 | PERSONAL | PDF | `0700571edd4c9f68bc3d1ea4d526c35e5b3746828d7cd038255117a06906545c` | yes | not recorded | PENDING |

## Database role audit

A read-only SQL query for `document_role` on these 20 IDs first failed when attempted through the MySQL container's local socket (`root@localhost`, `ERROR 1045`). The running application environment credential matched its service env file. Reusing that in-memory process credential over the MySQL TCP path succeeded: the schema contains `document_role` and all 20 target rows are `EVIDENCE`. No credential, account, or database state was changed. The new reader independently rechecks `DocumentRole.EVIDENCE` on every request.

## Proposed Nginx diff (not applied)

Production currently has a specific RAG location, explicit 404 rules for `/api/documents` and a `^~ /api/` 404 fallback; admin locations remain loopback-only and Basic Auth protected. The narrow exception needed for this feature is:

```diff
     location = /api/rag/ask {
         proxy_pass http://127.0.0.1:8080;
     }
+    location ^~ /api/public/sources/ {
+        limit_except GET HEAD { deny all; }
+        proxy_pass http://127.0.0.1:8080;
+        proxy_set_header Host $host;
+        proxy_set_header X-Real-IP $remote_addr;
+        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
+        proxy_set_header X-Forwarded-Proto $scheme;
+    }
     location ^~ /api/documents/ { return 404; }
     location ^~ /api/ { return 404; }
```

This is only a GET/HEAD proxy; the backend remains the authorization boundary for every arbitrary numeric ID and checks the approved manifest, SHA-256, confined upload path, and `DocumentRole.EVIDENCE`. No client-supplied file path is accepted. The `/api/documents/`, `/admin/`, and `/admin/api/` protections are unchanged. Do not apply this snippet before authorization review and deployment approval.

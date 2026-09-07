package com.yu.transferrag.tool;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class GithubMarkdownImportTool {

    private static final String DEFAULT_BACKEND_BASE_URL = "http://localhost:8080";
    private static final String DEPARTMENT = "软件学院";
    private static final int YEAR = 2026;
    private static final String SOURCE_TYPE = "GITHUB";
    private static final String GITHUB_API_VERSION = "2026-03-10";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient githubClient;
    private final RestClient downloadClient;
    private final RestClient backendClient;

    public GithubMarkdownImportTool(String backendBaseUrl, String githubToken) {
        RestClient.Builder githubClientBuilder = RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "transfer-rag-github-importer")
                .defaultHeader("X-GitHub-Api-Version", GITHUB_API_VERSION);

        if (githubToken != null && !githubToken.isBlank()) {
            githubClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken.trim());
        }

        this.githubClient = githubClientBuilder.build();
        this.downloadClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, "transfer-rag-github-importer")
                .build();
        this.backendClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(backendBaseUrl))
                .build();
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.out.println("Usage: GithubMarkdownImportTool <github-repository-url> [backend-base-url]");
            System.out.println("Example: GithubMarkdownImportTool "
                    + "https://github.com/Zhenxiangyu05/NJU-SE-Transfer-Guide http://localhost:8080");
            return;
        }

        String repositoryUrl = args[0];
        String backendBaseUrl = args.length >= 2 ? args[1] : DEFAULT_BACKEND_BASE_URL;
        String githubToken = System.getenv("GITHUB_TOKEN");

        GithubMarkdownImportTool tool = new GithubMarkdownImportTool(backendBaseUrl, githubToken);
        tool.importRepository(repositoryUrl);
    }

    public void importRepository(String repositoryUrl) {
        GitHubRepository repository = parseRepositoryUrl(repositoryUrl);
        String docsApiUrl = "https://api.github.com/repos/%s/%s/contents/docs"
                .formatted(repository.owner(), repository.name());

        List<GitHubMarkdownFile> markdownFiles = new ArrayList<>();
        collectMarkdownFiles(docsApiUrl, markdownFiles);
        markdownFiles.sort(Comparator.comparing(GitHubMarkdownFile::path));

        System.out.printf("Found %d Markdown file(s) under docs/.%n", markdownFiles.size());

        int successCount = 0;
        for (GitHubMarkdownFile markdownFile : markdownFiles) {
            ImportResult result = importOne(markdownFile);
            printResult(result);
            if (result.success()) {
                successCount++;
            }
        }

        System.out.printf("Import finished: total=%d, success=%d, failed=%d%n",
                markdownFiles.size(), successCount, markdownFiles.size() - successCount);
    }

    private void collectMarkdownFiles(String directoryApiUrl, List<GitHubMarkdownFile> result) {
        List<Map<String, Object>> entries = githubClient.get()
                .uri(directoryApiUrl)
                .retrieve()
                .body(LIST_OF_MAPS_TYPE);

        if (entries == null) {
            throw new IllegalStateException("GitHub API returned an empty response for: " + directoryApiUrl);
        }

        for (Map<String, Object> entry : entries) {
            String type = asString(entry.get("type"));
            String name = asString(entry.get("name"));
            String path = asString(entry.get("path"));

            if ("dir".equals(type)) {
                String childApiUrl = asString(entry.get("url"));
                if (childApiUrl != null) {
                    collectMarkdownFiles(childApiUrl, result);
                }
            } else if ("file".equals(type) && name != null && name.toLowerCase().endsWith(".md")) {
                String downloadUrl = asString(entry.get("download_url"));
                if (downloadUrl != null) {
                    result.add(new GitHubMarkdownFile(path, name, downloadUrl));
                }
            }
        }
    }

    private ImportResult importOne(GitHubMarkdownFile markdownFile) {
        Long documentId = null;
        Integer chunkCount = null;
        Integer indexedChunks = null;

        try {
            byte[] content = downloadClient.get()
                    .uri(markdownFile.downloadUrl())
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw new IllegalStateException("Downloaded file is empty");
            }

            Map<String, Object> uploadResponse = upload(markdownFile.name(), content);
            documentId = toLong(uploadResponse.get("id"), "upload response id");

            chunkCount = backendClient.post()
                    .uri("/api/documents/{id}/chunks", documentId)
                    .retrieve()
                    .body(Integer.class);
            if (chunkCount == null) {
                throw new IllegalStateException("Chunk endpoint returned an empty response");
            }

            Map<String, Object> indexResponse = backendClient.post()
                    .uri("/api/documents/{id}/index", documentId)
                    .retrieve()
                    .body(MAP_TYPE);
            if (indexResponse == null) {
                throw new IllegalStateException("Index endpoint returned an empty response");
            }
            indexedChunks = toInteger(indexResponse.get("indexedChunks"), "index response indexedChunks");

            return new ImportResult(markdownFile.path(), documentId, chunkCount, indexedChunks, true, null);
        } catch (Exception exception) {
            return new ImportResult(
                    markdownFile.path(),
                    documentId,
                    chunkCount,
                    indexedChunks,
                    false,
                    rootMessage(exception)
            );
        }
    }

    private Map<String, Object> upload(String fileName, byte[] content) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new NamedByteArrayResource(content, fileName))
                .contentType(MediaType.parseMediaType("text/markdown"));
        bodyBuilder.part("title", titleFromFileName(fileName));
        bodyBuilder.part("department", DEPARTMENT);
        bodyBuilder.part("year", Integer.toString(YEAR));
        bodyBuilder.part("sourceType", SOURCE_TYPE);

        Map<String, Object> response = backendClient.post()
                .uri("/api/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(bodyBuilder.build())
                .retrieve()
                .body(MAP_TYPE);

        if (response == null) {
            throw new IllegalStateException("Upload endpoint returned an empty response");
        }
        return response;
    }

    private static GitHubRepository parseRepositoryUrl(String repositoryUrl) {
        URI uri;
        try {
            uri = URI.create(repositoryUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repositoryUrl, exception);
        }

        if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Repository URL must use github.com");
        }

        String[] segments = uri.getPath().split("/");
        List<String> nonEmptySegments = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isBlank()) {
                nonEmptySegments.add(segment);
            }
        }
        if (nonEmptySegments.size() < 2) {
            throw new IllegalArgumentException("Repository URL must contain owner and repository name");
        }

        String repositoryName = nonEmptySegments.get(1);
        if (repositoryName.endsWith(".git")) {
            repositoryName = repositoryName.substring(0, repositoryName.length() - 4);
        }
        return new GitHubRepository(nonEmptySegments.get(0), repositoryName);
    }

    private static String titleFromFileName(String fileName) {
        String title = fileName.substring(0, fileName.length() - 3)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        return title.isEmpty() ? fileName : title;
    }

    private static Long toLong(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(fieldName + " is not a valid Long: " + text, exception);
            }
        }
        throw new IllegalStateException(fieldName + " is missing or invalid");
    }

    private static Integer toInteger(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(fieldName + " is not a valid Integer: " + text, exception);
            }
        }
        throw new IllegalStateException(fieldName + " is missing or invalid");
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String removeTrailingSlash(String value) {
        String result = value == null || value.isBlank() ? DEFAULT_BACKEND_BASE_URL : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static void printResult(ImportResult result) {
        System.out.printf(
                "file=%s, documentId=%s, chunks=%s, indexedChunks=%s, success=%s%s%n",
                result.fileName(),
                result.documentId(),
                result.chunkCount(),
                result.indexedChunks(),
                result.success(),
                result.error() == null ? "" : ", error=" + result.error()
        );
    }

    private record GitHubRepository(String owner, String name) {
    }

    private record GitHubMarkdownFile(String path, String name, String downloadUrl) {
    }

    private record ImportResult(
            String fileName,
            Long documentId,
            Integer chunkCount,
            Integer indexedChunks,
            boolean success,
            String error
    ) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}

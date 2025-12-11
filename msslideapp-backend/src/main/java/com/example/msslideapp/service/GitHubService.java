package com.example.msslideapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
public class GitHubService {

    @Value("${github.token}")
    private String token;

    @Value("${github.repo.owner}")
    private String repoOwner;

    @Value("${github.repo.name}")
    private String repoName;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubService() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        this.restTemplate = new RestTemplate(requestFactory);
    }

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Get the SHA of a specific branch
     */
    private String getBranchSha(String branchName) throws Exception {
        String url = String.format("%s/repos/%s/%s/git/refs/heads/%s", GITHUB_API_BASE, repoOwner, repoName,
                branchName);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            return json.get("object").get("sha").asText();
        }

        throw new RuntimeException("Failed to get branch SHA for " + branchName + ": " + response.getBody());
    }

    /**
     * Get the default branch SHA
     */
    private String getDefaultBranchSha() throws Exception {
        return getBranchSha("main");
    }

    /**
     * Create a new branch from main
     */
    public void createBranch(String branchName) throws Exception {
        String baseSha = getDefaultBranchSha();
        String url = String.format("%s/repos/%s/%s/git/refs", GITHUB_API_BASE, repoOwner, repoName);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("ref", "refs/heads/" + branchName);
        payload.put("sha", baseSha);

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new RuntimeException("Failed to create branch: " + response.getBody());
        }
    }

    /**
     * Create a new blob (file content) in GitHub
     */
    private String createBlob(byte[] content) throws Exception {
        String url = String.format("%s/repos/%s/%s/git/blobs", GITHUB_API_BASE, repoOwner, repoName);

        String base64Content = Base64.getEncoder().encodeToString(content);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("content", base64Content);
        payload.put("encoding", "base64");

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            JsonNode json = mapper.readTree(response.getBody());
            return json.get("sha").asText();
        }

        throw new RuntimeException("Failed to create blob: " + response.getBody());
    }

    /**
     * Create a new tree with the file
     */
    private String createTree(String baseSha, String filePath, String blobSha) throws Exception {
        String url = String.format("%s/repos/%s/%s/git/trees", GITHUB_API_BASE, repoOwner, repoName);

        ObjectNode treeItem = mapper.createObjectNode();
        treeItem.put("path", filePath);
        treeItem.put("mode", "100644");
        treeItem.put("type", "blob");
        treeItem.put("sha", blobSha);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("base_tree", baseSha);
        ArrayNode treeArray = mapper.createArrayNode();
        treeArray.add(treeItem);
        payload.set("tree", treeArray);

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            JsonNode json = mapper.readTree(response.getBody());
            return json.get("sha").asText();
        }

        throw new RuntimeException("Failed to create tree: " + response.getBody());
    }

    /**
     * Create a commit
     */
    private String createCommit(String message, String treeSha, String parentSha, String author) throws Exception {
        String url = String.format("%s/repos/%s/%s/git/commits", GITHUB_API_BASE, repoOwner, repoName);

        ObjectNode authorNode = mapper.createObjectNode();
        authorNode.put("name", author == null ? "System" : author);
        authorNode.put("email", (author == null ? "system" : author) + "@example.com");

        ArrayNode parentsArray = mapper.createArrayNode();
        parentsArray.add(parentSha);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("message", message);
        payload.put("tree", treeSha);
        payload.set("parents", parentsArray);
        payload.set("author", authorNode);
        payload.set("committer", authorNode);

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            JsonNode json = mapper.readTree(response.getBody());
            return json.get("sha").asText();
        }

        throw new RuntimeException("Failed to create commit: " + response.getBody());
    }

    /**
     * Update branch reference to point to new commit
     */
    private void updateBranchRef(String branch, String commitSha) throws Exception {
        String url = String.format("%s/repos/%s/%s/git/refs/heads/%s", GITHUB_API_BASE, repoOwner, repoName, branch);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("sha", commitSha);
        payload.put("force", false);

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to update branch ref: " + response.getBody());
        }
    }

    /**
     * Get file content from a specific branch
     */
    public String getFileContent(String filePath, String branchName) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/{path}?ref={ref}",
                    GITHUB_API_BASE, repoOwner, repoName);

            HttpEntity<String> entity = new HttpEntity<>(createHeaders());

            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("path", filePath);
            uriVariables.put("ref", branchName);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class,
                    uriVariables);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode json = mapper.readTree(response.getBody());
                String content = json.get("content").asText();
                // Content is base64 encoded with newlines
                content = content.replaceAll("\\n", "").replaceAll("\\r", "");
                return new String(Base64.getDecoder().decode(content));
            }
        } catch (Exception e) {
            // File might not exist
            System.err.println("Error fetching file content for " + filePath + ": " + e.getMessage());
            return null;
        }
        return null;
    }

    /**
     * Get list of files in a directory
     */
    public List<String> getRepositoryFiles(String path) {
        try {
            String url = String.format("%s/repos/%s/%s/contents/{path}", GITHUB_API_BASE, repoOwner, repoName);
            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("path", path);

            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class,
                    uriVariables);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode json = mapper.readTree(response.getBody());
                List<String> files = new ArrayList<>();
                if (json.isArray()) {
                    for (JsonNode node : json) {
                        files.add(node.get("name").asText());
                    }
                }
                return files;
            }
        } catch (Exception e) {
            System.err.println("Error listing repo files: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Commit a file to GitHub repository
     */
    public String commitFile(File file, String filePath, String message, String author, String branchName)
            throws Exception {
        byte[] content = Files.readAllBytes(file.toPath());
        return commitFile(content, filePath, message, author, branchName);
    }

    /**
     * Commit content to GitHub repository
     */
    public String commitFile(String content, String filePath, String message, String author, String branchName)
            throws Exception {
        return commitFile(content.getBytes("UTF-8"), filePath, message, author, branchName);
    }

    /**
     * Commit content to GitHub repository
     */
    public String commitFile(byte[] content, String filePath, String message, String author, String branchName)
            throws Exception {
        // Get current branch SHA
        String baseSha = getBranchSha(branchName);

        // Create blob
        String blobSha = createBlob(content);

        // Create tree
        String treeSha = createTree(baseSha, filePath, blobSha);

        // Create commit
        String commitSha = createCommit(message, treeSha, baseSha, author);

        // Update branch ref
        updateBranchRef(branchName, commitSha);

        return commitSha;
    }

    /**
     * Create a Pull Request
     */
    public int createPullRequest(String title, String body, String headBranch, List<String> reviewers)
            throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls", GITHUB_API_BASE, repoOwner, repoName);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", headBranch);
        payload.put("base", "main");

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            JsonNode json = mapper.readTree(response.getBody());
            int prNumber = json.get("number").asInt();

            // Request reviewers if provided
            if (reviewers != null && !reviewers.isEmpty()) {
                requestReviewers(prNumber, reviewers);
            }

            return prNumber;
        }

        throw new RuntimeException("Failed to create PR: " + response.getBody());
    }

    /**
     * Request reviewers for a Pull Request
     */
    private void requestReviewers(int prNumber, List<String> reviewers) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d/requested_reviewers",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        ObjectNode payload = mapper.createObjectNode();
        payload.set("reviewers", mapper.valueToTree(reviewers));

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    /**
     * Get repository collaborators
     */
    public List<Map<String, String>> getCollaborators() throws Exception {
        String url = String.format("%s/repos/%s/%s/collaborators", GITHUB_API_BASE, repoOwner, repoName);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            List<Map<String, String>> collaborators = new ArrayList<Map<String, String>>();

            for (JsonNode user : json) {
                Map<String, String> collab = new HashMap<String, String>();
                collab.put("login", user.get("login").asText());
                collab.put("avatar_url", user.has("avatar_url") ? user.get("avatar_url").asText() : "");
                collaborators.add(collab);
            }

            return collaborators;
        }

        throw new RuntimeException("Failed to get collaborators: " + response.getBody());
    }

    /**
     * Get commit history with optional search
     */
    public List<Map<String, Object>> getCommitHistory(String searchQuery) throws Exception {
        String url = String.format("%s/repos/%s/%s/commits", GITHUB_API_BASE, repoOwner, repoName);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            List<Map<String, Object>> commits = new ArrayList<Map<String, Object>>();

            for (JsonNode commit : json) {
                String message = commit.get("commit").get("message").asText();

                // Filter by search query if provided
                if (searchQuery != null && !searchQuery.isEmpty() &&
                        !message.toLowerCase().contains(searchQuery.toLowerCase())) {
                    continue;
                }

                Map<String, Object> commitData = new HashMap<String, Object>();
                commitData.put("sha", commit.get("sha").asText());
                commitData.put("message", message);
                commitData.put("author", commit.get("commit").get("author").get("name").asText());
                commitData.put("date", commit.get("commit").get("author").get("date").asText());
                commitData.put("url", commit.get("html_url").asText());

                commits.add(commitData);
            }

            return commits;
        }

        throw new RuntimeException("Failed to get commit history: " + response.getBody());
    }

    /**
     * Get commit details (files changed)
     */
    public Map<String, Object> getCommitDetails(String sha) throws Exception {
        String url = String.format("%s/repos/%s/%s/commits/%s", GITHUB_API_BASE, repoOwner, repoName, sha);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());

            Map<String, Object> commitDetails = new HashMap<String, Object>();
            commitDetails.put("sha", json.get("sha").asText());
            commitDetails.put("message", json.get("commit").get("message").asText());
            commitDetails.put("author", json.get("commit").get("author").get("name").asText());
            commitDetails.put("date", json.get("commit").get("author").get("date").asText());

            List<Map<String, String>> files = new ArrayList<Map<String, String>>();
            if (json.has("files")) {
                for (JsonNode file : json.get("files")) {
                    Map<String, String> fileData = new HashMap<String, String>();
                    fileData.put("filename", file.get("filename").asText());
                    fileData.put("status", file.get("status").asText());
                    fileData.put("additions", String.valueOf(file.get("additions").asInt()));
                    fileData.put("deletions", String.valueOf(file.get("deletions").asInt()));
                    files.add(fileData);
                }
            }
            commitDetails.put("files", files);

            List<String> parents = new ArrayList<>();
            if (json.has("parents")) {
                for (JsonNode parent : json.get("parents")) {
                    parents.add(parent.get("sha").asText());
                }
            }
            commitDetails.put("parents", parents);

            return commitDetails;
        }

        throw new RuntimeException("Failed to get commit details: " + response.getBody());
    }

    /**
     * Get Pull Request details
     */
    public Map<String, Object> getPullRequestDetails(int prNumber) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            Map<String, Object> prDetails = new HashMap<>();
            prDetails.put("number", json.get("number").asInt());
            prDetails.put("title", json.get("title").asText());
            prDetails.put("head_branch", json.get("head").get("ref").asText());
            prDetails.put("base_branch", json.get("base").get("ref").asText());
            prDetails.put("head_sha", json.get("head").get("sha").asText());
            prDetails.put("base_sha", json.get("base").get("sha").asText());
            return prDetails;
        }

        throw new RuntimeException("Failed to get PR details: " + response.getBody());
    }

    /**
     * Get files changed in a Pull Request
     */
    public List<String> getPullRequestFiles(int prNumber) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d/files",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            List<String> files = new ArrayList<>();

            for (JsonNode file : json) {
                files.add(file.get("filename").asText());
            }

            return files;
        }

        throw new RuntimeException("Failed to get PR files: " + response.getBody());
    }

    /**
     * Get Pull Requests (for approvals)
     */
    public List<Map<String, Object>> getPullRequests(String state) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls?state=%s",
                GITHUB_API_BASE, repoOwner, repoName, state);

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode json = mapper.readTree(response.getBody());
            List<Map<String, Object>> prs = new ArrayList<Map<String, Object>>();

            for (JsonNode pr : json) {
                Map<String, Object> prData = new HashMap<String, Object>();
                prData.put("number", pr.get("number").asInt());
                prData.put("title", pr.get("title").asText());
                prData.put("body", pr.has("body") && !pr.get("body").isNull() ? pr.get("body").asText() : "");
                prData.put("state", pr.get("state").asText());
                prData.put("created_at", pr.get("created_at").asText());
                prData.put("user", pr.get("user").get("login").asText());
                prData.put("html_url", pr.get("html_url").asText());

                prs.add(prData);
            }

            return prs;
        }

        throw new RuntimeException("Failed to get pull requests: " + response.getBody());
    }

    /**
     * Approve a Pull Request
     */
    public void approvePullRequest(int prNumber, String comment) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("event", "APPROVE");
        if (comment != null && !comment.isEmpty()) {
            payload.put("body", comment);
        }

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Failed to approve PR: " + response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException.UnprocessableEntity e) {
            if (e.getResponseBodyAsString().contains("Can not approve your own pull request")) {
                System.out.println("Warning: Cannot approve own PR. Proceeding...");
                return;
            }
            throw e;
        }
    }

    /**
     * Reject a Pull Request
     */
    public void rejectPullRequest(int prNumber, String comment) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d/reviews",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("event", "REQUEST_CHANGES");
        payload.put("body", comment != null ? comment : "Changes requested");

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                System.err.println("Failed to submit rejection review: " + response.getBody());
            }
        } catch (Exception e) {
            // Ignore errors when rejecting (e.g. rejecting own PR), but still proceed to
            // close
            System.out
                    .println("Warning: Could not submit rejection review (likely own PR). Proceeding to close. Error: "
                            + e.getMessage());
        }

        // Close the PR
        closePullRequest(prNumber);
    }

    /**
     * Close a Pull Request
     */
    private void closePullRequest(int prNumber) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("state", "closed");

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
    }

    /**
     * Merge a Pull Request
     */
    public void mergePullRequest(int prNumber, String commitMessage) throws Exception {
        String url = String.format("%s/repos/%s/%s/pulls/%d/merge",
                GITHUB_API_BASE, repoOwner, repoName, prNumber);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("commit_message", commitMessage);
        payload.put("merge_method", "squash");

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to merge PR: " + response.getBody());
        }
    }
}

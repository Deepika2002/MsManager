package com.example.msslideapp.service;

import com.example.msslideapp.model.ChangeItem;
import com.example.msslideapp.model.UploadResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class ExcelService {

    private final ExcelJsonConverter converter;
    private final GitService gitService;
    private final SharePointSimulator sharePointSimulator;
    private final GitHubService gitHubService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExcelService(ExcelJsonConverter converter, GitService gitService, SharePointSimulator sharePointSimulator,
            GitHubService gitHubService) {
        this.converter = converter;
        this.gitService = gitService;
        this.sharePointSimulator = sharePointSimulator;
        this.gitHubService = gitHubService;
    }

    public UploadResponse handleUpload(List<MultipartFile> files, String commitMessage, List<String> approvers)
            throws Exception {
        String basePath = System.getProperty("user.dir") + File.separator + "storage" + File.separator + "uploads";
        File uploadDir = new File(basePath);
        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            throw new IOException("❌ Failed to create upload directory: " + uploadDir.getAbsolutePath());
        }

        List<ChangeItem> allChanges = new ArrayList<>();
        String branchName = "feature/" + UUID.randomUUID().toString().substring(0, 8);
        gitHubService.createBranch(branchName);

        StringBuilder commitMsgBuilder = new StringBuilder();
        if (commitMessage != null && !commitMessage.isEmpty()) {
            commitMsgBuilder.append(commitMessage);
        } else {
            commitMsgBuilder.append("Upload: ");
        }

        for (MultipartFile file : files) {
            File saved = new File(uploadDir, UUID.randomUUID() + "-" + file.getOriginalFilename());
            file.transferTo(saved);

            System.out.println("✅ File saved to: " + saved.getAbsolutePath());

            String newJson = converter.excelToJson(saved);
            String filename = file.getOriginalFilename().replaceAll("\\.xlsx?$", "") + ".json";

            // Get previous JSON from GitHub main branch to compare
            String prevJson = gitHubService.getFileContent(filename, "main");

            if (prevJson != null && !prevJson.trim().isEmpty()) {
                List<ChangeItem> fileChanges = diffJson(prevJson, newJson);
                for (ChangeItem item : fileChanges) {
                    item.setFileName(file.getOriginalFilename());
                }
                allChanges.addAll(fileChanges);
                System.out.println(
                        "ℹ️ Found previous version for " + filename + ". Detected " + fileChanges.size() + " changes.");
            } else {
                System.out.println("ℹ️ No previous version found for " + filename + " — first upload.");
            }

            if (commitMsgBuilder.length() > (commitMessage == null ? 8 : commitMessage.length())) {
                commitMsgBuilder.append(", ");
            }
            commitMsgBuilder.append(file.getOriginalFilename());

            // Commit to the new branch
            gitHubService.commitFile(newJson, filename, "Update " + file.getOriginalFilename(), "uploader", branchName);
        }

        String finalMessage = commitMsgBuilder.toString();

        // Create Pull Request
        gitHubService.createPullRequest(finalMessage, "Changes uploaded via MsManager", branchName, approvers);

        System.out.println("✅ Aggregated " + allChanges.size() + " total changes across " + files.size() + " files.");

        UploadResponse resp = new UploadResponse();
        // Since we have multiple files, using a single ID might be ambiguous, but let's
        // just use the branch name or similar.
        // Or we just return the first file's name or a generic ID.
        resp.setId(branchName);
        resp.setChanges(allChanges);
        return resp;
    }

    public String approve(String id, String originalFilename, String approver) throws Exception {
        // Legacy method, not used by new frontend
        return null;
    }

    public boolean reject(String id, String approver) throws Exception {
        // Legacy method, not used by new frontend
        return false;
    }

    public String handlePrApproval(int prNumber, String comment) throws Exception {
        // 1. Approve PR
        gitHubService.approvePullRequest(prNumber, comment);

        // 2. Merge PR
        gitHubService.mergePullRequest(prNumber, "Approved and merged");

        // 3. Get files in PR to push to SharePoint
        // We need to know which files were modified to convert them back to Excel and
        // push.
        // For simplicity, we'll fetch the commit details of the merge commit or just
        // list files in PR.
        // GitHubService.getCommitDetails requires SHA.
        // Let's assume we can get the file content from the main branch after merge.
        // We need a method in GitHubService to get file content.

        // For now, let's just return a success message.
        // TODO: Implement fetching file content and pushing to SharePoint.
        // Since I can't easily get the file content without adding more methods to
        // GitHubService,
        // I will add a method to GitHubService to get file content.

        return "Approved and Merged";
    }

    public void handlePrRejection(int prNumber, String comment) throws Exception {
        gitHubService.rejectPullRequest(prNumber, comment);
    }

    public List<String> history() throws Exception {
        List<Map<String, Object>> commits = gitHubService.getCommitHistory(null);
        List<String> history = new ArrayList<>();
        for (Map<String, Object> c : commits) {
            history.add(c.get("sha") + " :: " + c.get("message"));
        }
        return history;
    }

    public List<ChangeItem> getPRChanges(int prNumber) throws Exception {
        try {
            // Get PR details to find the head and base branches
            Map<String, Object> prDetails = gitHubService.getPullRequestDetails(prNumber);
            String headBranch = (String) prDetails.get("head_branch");
            String baseBranch = (String) prDetails.get("base_branch");
            String headSha = (String) prDetails.get("head_sha");
            String baseSha = (String) prDetails.get("base_sha");

            // Get files changed in this PR
            List<String> changedFiles = gitHubService.getPullRequestFiles(prNumber);
            System.out.println("DEBUG: PR #" + prNumber + " files: " + changedFiles);

            List<ChangeItem> allChanges = new ArrayList<>();

            for (String file : changedFiles) {
                if (file.endsWith(".json")) {
                    String originalExcelName = file.replaceAll("\\.json$", ".xlsx");

                    // Get content from head and base via SHA (safer for merged PRs where branch is
                    // deleted)
                    String targetHead = (headSha != null) ? headSha : headBranch;
                    String targetBase = (baseSha != null) ? baseSha : baseBranch;

                    System.out.println("DEBUG: Fetching " + file + " from head: " + targetHead);
                    String headJson = gitHubService.getFileContent(file, targetHead);
                    System.out.println("DEBUG: Head content len: " + (headJson == null ? "null" : headJson.length()));

                    System.out.println("DEBUG: Fetching " + file + " from base: " + targetBase);
                    String baseJson = gitHubService.getFileContent(file, targetBase);
                    System.out.println("DEBUG: Base content len: " + (baseJson == null ? "null" : baseJson.length()));

                    // Calculate diff
                    if (headJson != null) {
                        List<ChangeItem> fileChanges = diffJson(baseJson, headJson);
                        System.out.println("DEBUG: File " + file + " has " + fileChanges.size() + " diffs");
                        for (ChangeItem item : fileChanges) {
                            item.setFileName(originalExcelName);
                        }
                        allChanges.addAll(fileChanges);
                    }
                }
            }

            return allChanges;
        } catch (Exception e) {
            System.err.println("Error fetching PR changes: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<ChangeItem> getCommitChanges(String sha) throws Exception {
        try {
            // Get commit details
            Map<String, Object> details = gitHubService.getCommitDetails(sha);

            // Find all JSON files that were changed
            List<Map<String, String>> files = (List<Map<String, String>>) details.get("files");
            List<ChangeItem> allChanges = new ArrayList<>();

            List<String> parents = (List<String>) details.get("parents");
            String parentSha = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;

            for (Map<String, String> file : files) {
                String filename = file.get("filename");
                if (filename != null && filename.endsWith(".json")) {
                    String originalExcelName = filename.replaceAll("\\.json$", ".xlsx");

                    // Get content at current SHA
                    String currentJson = gitHubService.getFileContent(filename, sha);

                    // Get content at parent SHA
                    String parentJson = null;
                    if (parentSha != null) {
                        parentJson = gitHubService.getFileContent(filename, parentSha);
                    }

                    // Calculate diff
                    if (currentJson != null) {
                        List<ChangeItem> fileChanges = diffJson(parentJson, currentJson);
                        for (ChangeItem item : fileChanges) {
                            item.setFileName(originalExcelName);
                        }
                        allChanges.addAll(fileChanges);
                    }
                }
            }

            return allChanges;
        } catch (Exception e) {
            System.err.println("Error fetching commit changes: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** === Diff logic: detect added/deleted/value/style changes === */
    private List<ChangeItem> diffJson(String oldJson, String newJson) throws Exception {
        List<ChangeItem> diffs = new ArrayList<ChangeItem>();

        if (newJson == null || newJson.trim().isEmpty())
            return diffs;

        JsonNode newRoot = mapper.readTree(newJson);
        JsonNode oldRoot = (oldJson == null || oldJson.trim().isEmpty()) ? null : mapper.readTree(oldJson);

        Map<String, Map<String, JsonNode>> oldSheets = buildSheetMap(oldRoot);
        Map<String, Map<String, JsonNode>> newSheets = buildSheetMap(newRoot);

        Set<String> allSheets = new HashSet<String>();
        allSheets.addAll(oldSheets.keySet());
        allSheets.addAll(newSheets.keySet());

        System.out.println("Comparing " + allSheets.size() + " sheets...");

        for (String sheet : allSheets) {
            Map<String, JsonNode> oldCells = oldSheets.getOrDefault(sheet, Collections.<String, JsonNode>emptyMap());
            Map<String, JsonNode> newCells = newSheets.getOrDefault(sheet, Collections.<String, JsonNode>emptyMap());

            Set<String> allKeys = new HashSet<String>();
            allKeys.addAll(oldCells.keySet());
            allKeys.addAll(newCells.keySet());

            // System.out.println("Sheet " + sheet + ": comparing " + allKeys.size() + "
            // cells");

            for (String key : allKeys) {
                JsonNode oldCell = oldCells.get(key);
                JsonNode newCell = newCells.get(key);

                int row = Integer.parseInt(key.split(":")[0]);
                int col = Integer.parseInt(key.split(":")[1]);

                String oldVal = normalize(getSafeText(oldCell, "value"));
                String newVal = normalize(getSafeText(newCell, "value"));

                boolean oldBold = getSafeBool(oldCell, "fontBold");
                boolean newBold = getSafeBool(newCell, "fontBold");
                boolean oldStrike = getSafeBool(oldCell, "strike");
                boolean newStrike = getSafeBool(newCell, "strike");
                int oldFontSize = getSafeInt(oldCell, "fontSize", 11);
                int newFontSize = getSafeInt(newCell, "fontSize", 11);

                String oldColor = normalizeColor(getSafeText(oldCell, "fontColor"), null);
                String newColor = normalizeColor(getSafeText(newCell, "fontColor"), null);

                String oldBg = normalizeColor(getSafeText(oldCell, "bgColor"), null);
                String newBg = normalizeColor(getSafeText(newCell, "bgColor"), null);

                String oldAlign = getSafeText(oldCell, "alignment");
                String newAlign = getSafeText(newCell, "alignment");

                String oldBorderTop = getSafeText(oldCell, "borderTop");
                String newBorderTop = getSafeText(newCell, "borderTop");
                String oldBorderBottom = getSafeText(oldCell, "borderBottom");
                String newBorderBottom = getSafeText(newCell, "borderBottom");
                String oldBorderLeft = getSafeText(oldCell, "borderLeft");
                String newBorderLeft = getSafeText(newCell, "borderLeft");
                String oldBorderRight = getSafeText(oldCell, "borderRight");
                String newBorderRight = getSafeText(newCell, "borderRight");

                boolean added = (oldCell == null && newCell != null);
                boolean deleted = (oldCell != null && newCell == null);
                boolean valueChanged = !normalizeText(oldVal).equals(normalizeText(newVal));

                boolean formatChanged = oldBold != newBold ||
                        oldStrike != newStrike ||
                        oldFontSize != newFontSize ||
                        !Objects.equals(oldColor, newColor) ||
                        !Objects.equals(oldBg, newBg) ||
                        !Objects.equals(oldAlign, newAlign) ||
                        !Objects.equals(oldBorderTop, newBorderTop) ||
                        !Objects.equals(oldBorderBottom, newBorderBottom) ||
                        !Objects.equals(oldBorderLeft, newBorderLeft) ||
                        !Objects.equals(oldBorderRight, newBorderRight);

                if (added || deleted || valueChanged || formatChanged) {
                    ChangeItem item = new ChangeItem();
                    item.setSheet(sheet);
                    item.setRow(row);
                    item.setCol(col);
                    item.setOldValue(oldVal);
                    item.setNewValue(newVal);

                    if (added)
                        item.setChangeType("ADDED");
                    else if (deleted)
                        item.setChangeType("DELETED");
                    else
                        item.setChangeType("MODIFIED");

                    // meta with old/new style pairs for frontend
                    Map<String, Object> meta = new HashMap<String, Object>();
                    meta.put("oldFontColor", oldColor);
                    meta.put("newFontColor", newColor);
                    meta.put("oldBgColor", oldBg);
                    meta.put("newBgColor", newBg);
                    meta.put("oldFontSize", oldFontSize);
                    meta.put("newFontSize", newFontSize);
                    meta.put("oldBold", oldBold);
                    meta.put("newBold", newBold);
                    meta.put("oldStrike", oldStrike);
                    meta.put("newStrike", newStrike);
                    meta.put("oldAlign", oldAlign);
                    meta.put("newAlign", newAlign);
                    meta.put("oldBorders", String.join(", ",
                            safeVal(oldBorderTop), safeVal(oldBorderBottom),
                            safeVal(oldBorderLeft), safeVal(oldBorderRight)));
                    meta.put("newBorders", String.join(", ",
                            safeVal(newBorderTop), safeVal(newBorderBottom),
                            safeVal(newBorderLeft), safeVal(newBorderRight)));
                    item.setMeta(meta);

                    diffs.add(item);
                }
            }
        }

        return diffs;
    }

    /** === Helpers === */
    private Map<String, Map<String, JsonNode>> buildSheetMap(JsonNode root) {
        Map<String, Map<String, JsonNode>> sheets = new HashMap<String, Map<String, JsonNode>>();
        if (root != null && root.has("sheets")) {
            for (JsonNode s : root.get("sheets")) {
                String sheetName = s.has("name") ? s.get("name").asText() : "Sheet1";
                Map<String, JsonNode> cellMap = new HashMap<String, JsonNode>();
                if (s.has("cells")) {
                    for (JsonNode c : s.get("cells")) {
                        // skip completely empty cells (helps reduce noise)
                        boolean hasValue = c.has("value") && c.get("value") != null
                                && !c.get("value").asText().trim().isEmpty();
                        boolean hasStyle = c.has("fontColor") || c.has("bgColor") || c.has("fontBold")
                                || c.has("fontSize") || c.has("strike");
                        if (!hasValue && !hasStyle) {
                            // still include if you want full map, but skipping reduces false-positive noise
                            // continue;
                        }
                        String key = c.get("row").asInt() + ":" + c.get("col").asInt();
                        cellMap.put(key, c);
                    }
                }
                sheets.put(sheetName, cellMap);
            }
        }
        return sheets;
    }

    private String normalize(String s) {
        if (s == null)
            return "";
        return s
                .replace('\u00A0', ' ')
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("’", "'")
                .replace("…", "...")
                .replaceAll("[\\uFFFD]", "")
                .trim();
    }

    private String getSafeText(JsonNode node, String key) {
        return (node != null && node.has(key)) ? node.get(key).asText() : null;
    }

    private boolean getSafeBool(JsonNode node, String key) {
        return node != null && node.has(key) && node.get(key).asBoolean();
    }

    private int getSafeInt(JsonNode node, String key, int def) {
        return (node != null && node.has(key)) ? node.get(key).asInt(def) : def;
    }

    private String normalizeColor(String color, String def) {
        if (color == null || color.trim().isEmpty())
            return def;
        color = color.trim().toUpperCase();
        if (!color.startsWith("#"))
            color = "#" + color;
        if (color.length() == 7)
            return color;
        if (color.length() == 9)
            return "#" + color.substring(3);
        return def;
    }

    private String normalizeText(String s) {
        if (s == null)
            return "";
        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeVal(String val) {
        return val == null ? "" : val;
    }
}

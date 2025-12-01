package com.example.msslideapp.controller;

import com.example.msslideapp.model.UploadResponse;
import com.example.msslideapp.service.ExcelService;
import com.example.msslideapp.service.GitHubService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UploadController {

    private ExcelService excelService;
    private GitHubService gitHubService;

    public UploadController(ExcelService excelService, GitHubService gitHubService) {
        this.excelService = excelService;
        this.gitHubService = gitHubService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "commitMessage", required = false) String commitMessage,
            @RequestParam(value = "approvers", required = false) List<String> approvers) throws Exception {
        return excelService.handleUpload(file, commitMessage, approvers);
    }

    @GetMapping("/history")
    public java.util.List<String> history() throws Exception {
        return excelService.history();
    }

    @GetMapping("/collaborators")
    public List<Map<String, String>> getCollaborators() throws Exception {
        return gitHubService.getCollaborators();
    }

    @GetMapping("/commits")
    public List<Map<String, Object>> getCommits(
            @RequestParam(value = "search", required = false) String search) throws Exception {
        return gitHubService.getCommitHistory(search);
    }

    @GetMapping("/commits/{sha}")
    public Map<String, Object> getCommitDetails(@PathVariable String sha) throws Exception {
        return gitHubService.getCommitDetails(sha);
    }

    @GetMapping("/commits/{sha}/changes")
    public Object getCommitChanges(@PathVariable String sha) throws Exception {
        return excelService.getCommitChanges(sha);
    }
}

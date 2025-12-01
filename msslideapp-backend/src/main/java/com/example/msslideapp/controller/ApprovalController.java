package com.example.msslideapp.controller;

import com.example.msslideapp.model.ApproveRequest;
import com.example.msslideapp.service.ExcelService;
import com.example.msslideapp.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApprovalController {
    private ExcelService excelService;
    private GitHubService gitHubService;

    public ApprovalController(ExcelService excelService, GitHubService gitHubService) {
        this.excelService = excelService;
        this.gitHubService = gitHubService;
    }

    @PostMapping("/approve")
    public java.util.Map<String, String> approve(@RequestBody ApproveRequest req) throws Exception {
        String sharePointPath = excelService.approve(req.getId(), req.getId(), req.getApprover());
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        m.put("status", "approved");
        m.put("sharepointPath", sharePointPath);
        return m;
    }

    @PostMapping("/reject")
    public java.util.Map<String, String> reject(@RequestBody ApproveRequest req) throws Exception {
        boolean ok = excelService.reject(req.getId(), req.getApprover());
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        m.put("status", ok ? "reverted" : "nothing to revert");
        return m;
    }

    @GetMapping("/approvals/pending")
    public List<Map<String, Object>> getPendingApprovals() throws Exception {
        // Get all open PRs
        return gitHubService.getPullRequests("open");
    }

    @GetMapping("/approvals/sent")
    public List<Map<String, Object>> getSentApprovals() throws Exception {
        // Get all PRs (open and closed)
        return gitHubService.getPullRequests("all");
    }

    @PostMapping("/approvals/{prNumber}/approve")
    public Map<String, String> approvePR(
            @PathVariable int prNumber,
            @RequestBody(required = false) Map<String, String> body) throws Exception {
        String comment = body != null ? body.get("comment") : null;
        String message = excelService.handlePrApproval(prNumber, comment);

        Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "approved");
        response.put("message", message);
        return response;
    }

    @PostMapping("/approvals/{prNumber}/reject")
    public Map<String, String> rejectPR(
            @PathVariable int prNumber,
            @RequestBody Map<String, String> body) throws Exception {
        String comment = body.get("comment");
        excelService.handlePrRejection(prNumber, comment);

        Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "rejected");
        response.put("message", "Pull request rejected and closed");
        return response;
    }

    @GetMapping("/approvals/{prNumber}/changes")
    public Object getPRChanges(@PathVariable int prNumber) throws Exception {
        return excelService.getPRChanges(prNumber);
    }
}

package com.example.msslideapp.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;


import java.io.*;
import java.util.*;

@Service
public class GitService {
    private File repoDir = new File("storage/json-repo");
   
    @Value("${git.remote.url:}")   // read from application.properties (fallback = empty)
    private String remoteUrl;

    @Value("${git.token:}")        // read from application.properties (fallback = empty)
    private String token;

    public GitService() throws Exception {

        if (!repoDir.exists()) {
            repoDir.mkdirs();
            Git.init().setDirectory(repoDir).call().close();
        }
    }

    private Git open() throws Exception {
        return Git.open(repoDir);
    }

    public String commitJson(String filename, String jsonContent, String message, String author) throws Exception {
        File f = new File(repoDir, filename);
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(jsonContent);
        }
        Git git = open();
        git.add().addFilepattern(filename).call();
        RevCommit commit = git.commit().setMessage(message).setAuthor(author == null ? "system" : author, author == null ? "system@example.com" : author + "@example.com").call();
        // optionally push
        if (remoteUrl != null && !remoteUrl.isEmpty() && token != null && !token.isEmpty()) {
            try {
                UsernamePasswordCredentialsProvider cp = new UsernamePasswordCredentialsProvider(token, "");
                git.push().setCredentialsProvider(cp).setRemote(remoteUrl).call();
            } catch (Exception ex) {
                // ignore push failures; log if needed
            }
        }
        git.close();
        return commit.getName();
    }

    public List<String> listHistory() throws Exception {
        Git git = open();
        List<String> history = new ArrayList<>();
        try {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit c : commits) {
                history.add(c.getName() + " :: " + c.getShortMessage());
            }
        } catch (org.eclipse.jgit.api.errors.NoHeadException e) {
            // 🩹 fix: empty repo, so no history yet
            System.out.println("ℹ️ No commits yet, returning empty history.");
        }
        git.close();
        return history;
    }

    public String getLatestJson(String filename) throws Exception {
        File f = new File(repoDir, filename);
        if (!f.exists()) return null;
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
    }

    public boolean revertLast() throws Exception {
        Git git = open();
        Iterable<RevCommit> commits = git.log().setMaxCount(2).call();
        List<RevCommit> list = new ArrayList<RevCommit>();
        for (RevCommit c : commits) list.add(c);
        if (list.size() < 2) {
            git.close();
            return false;
        }
        String to = list.get(1).getName();
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef(to).call();
        git.close();
        return true;
    }

    public File getRepoDir() {
        return repoDir;
    }
}

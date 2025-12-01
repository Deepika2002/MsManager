package com.example.msslideapp.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class SharePointSimulator {

    private File spFolder = new File("simulated-sharepoint");

    public SharePointSimulator() {
        if (!spFolder.exists()) spFolder.mkdirs();
    }

    public String pushToSharePoint(File excelFile, String originalName) throws Exception {
        String t = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File dest = new File(spFolder, originalName + "-" + t + ".xlsx");
        java.nio.file.Files.copy(excelFile.toPath(), dest.toPath());
        return dest.getAbsolutePath();
    }
}

package com.sys4life.inventorytmg.controller;

import com.sys4life.inventorytmg.service.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@AllArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    @GetMapping("/")
    public String home(Model model) {
        return "index";
    }

    @PostMapping("/process-file")
    public String downloadFile(@RequestParam("file") MultipartFile file, HttpServletResponse response) throws IOException {
        response.setContentType("application/octet-stream");
        DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        String currentDateTime = dateFormatter.format(new Date());
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=outputInventory_" + currentDateTime + ".xlsx";
        response.setHeader(headerKey, headerValue);
        fileService.processFile(file.getInputStream(), response);
        return "index";
    }
}

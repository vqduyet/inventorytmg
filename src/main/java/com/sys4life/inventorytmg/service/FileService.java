package com.sys4life.inventorytmg.service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

public interface FileService {
    void processFile(InputStream file, HttpServletResponse httpServletResponse) throws IOException;
}

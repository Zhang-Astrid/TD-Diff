package com.sismics.docs.core.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class DocxTextExtractor {
    public static String extract(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph para : paragraphs) {
                sb.append(para.getText()).append('\n');
            }
            return sb.toString();
        }
    }
}

package com.qb.ai.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.StringJoiner;

/**
 * Extracts plain text from a .docx file using Apache POI.
 */
@Service
public class DocxExtractorService {

    /**
     * Extracts all text from a .docx multipart file.
     * Includes body paragraphs and table cell content.
     *
     * @param file the uploaded .docx file
     * @return extracted plain text with paragraphs separated by newlines
     * @throws IOException              if reading the file fails
     * @throws IllegalArgumentException if the file is null, empty, or not a .docx
     */
    public String extractText(MultipartFile file) throws IOException {
        validateFile(file);

        StringJoiner result = new StringJoiner("\n");

        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            // Extract body paragraphs
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    result.add(text);
                }
            }

            // Extract table cell content
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            String text = paragraph.getText();
                            if (text != null && !text.isBlank()) {
                                result.add(text);
                            }
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean validByName = originalFilename != null && originalFilename.toLowerCase().endsWith(".docx");
        boolean validByType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType);

        if (!validByName && !validByType) {
            throw new IllegalArgumentException("Only .docx files are supported");
        }
    }
}

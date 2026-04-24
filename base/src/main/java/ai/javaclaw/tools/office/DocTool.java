package ai.javaclaw.tools.office;

import org.springframework.ai.tool.annotation.Tool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.*;

public class DocTool {

    private final String baseDir;

    public DocTool(String baseDir) {
        this.baseDir = baseDir;
    }

    @Tool(description = """
            Handles document creation and conversion tasks.

            ## Capabilities:
            - Create DOCX documents with text content
            - Convert DOCX to PDF
            - Create PDF documents

            ## When to Use:
            - When user asks to generate resumes, reports, or documents
            - When converting Word documents to PDF
            - When exporting content into a structured document format

            ## Parameters:
            - operation: One of [create_docx, create_pdf, docx_to_pdf]
            - inputPath: Path to input file (for conversions)
            - outputFileName: Name of output file
            - content: Text content for document generation
            """)
    public String documentOperation(String operation, String inputPath, String outputFileName, String content) {
        try {
            switch (operation.toLowerCase()) {

                case "create_docx":
                    return createDocx(outputFileName, content);

                case "create_pdf":
                    return createPdf(outputFileName, content);

                case "docx_to_pdf":
                    return convertDocxToPdf(inputPath);

                default:
                    return "Unsupported operation: " + operation;
            }

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String createDocx(String fileName, String content) throws Exception {
        XWPFDocument doc = new XWPFDocument();

        for (String line : content.split("\n")) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(line);
        }

        File file = new File(baseDir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            doc.write(out);
        }

        return "DOCX created: " + file.getAbsolutePath();
    }

    private String createPdf(String fileName, String content) throws Exception {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());

        File file = new File(baseDir, fileName);
        document.save(file);
        document.close();

        return "PDF created: " + file.getAbsolutePath();
    }

    private String convertDocxToPdf(String inputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "soffice", "--headless",
                "--convert-to", "pdf",
                inputPath,
                "--outdir", baseDir
        );
        pb.start().waitFor();
        return "Converted DOCX to PDF";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseDir = "./workspace";

        public Builder baseDir(String baseDir) {
            this.baseDir = baseDir;
            return this;
        }

        public DocTool build() {
            return new DocTool(baseDir);
        }
    }
}
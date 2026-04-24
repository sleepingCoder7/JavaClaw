package ai.javaclaw.tools.office;

import org.springframework.ai.tool.annotation.Tool;

import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

public class MediaTool {

    private final String baseDir;

    public MediaTool(String baseDir) {
        this.baseDir = baseDir;
    }

    @Tool(description = """
            Handles image and media transformations.

            ## Capabilities:
            - Convert multiple images into a single PDF

            ## When to Use:
            - When user wants to merge images into a document
            - When generating PDFs from image collections

            ## Parameters:
            - operation: images_to_pdf
            - inputPaths: List of image file paths
            - outputFileName: Output PDF file
            """)
    public String mediaOperation(String operation, List<String> inputPaths, String outputFileName) {
        try {
            if ("images_to_pdf".equalsIgnoreCase(operation)) {
                return imagesToPdf(inputPaths, outputFileName);
            }
            return "Unsupported operation";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String imagesToPdf(List<String> imagePaths, String outputFile) throws Exception {
        PDDocument doc = new PDDocument();

        for (String path : imagePaths) {
            BufferedImage image = ImageIO.read(new File(path));
            PDPage page = new PDPage();
            doc.addPage(page);

            var pdImage = LosslessFactory.createFromImage(doc, image);

            var cs = new PDPageContentStream(doc, page);
            cs.drawImage(pdImage, 0, 0);
            cs.close();
        }

        File file = new File(baseDir, outputFile);
        doc.save(file);
        doc.close();

        return "PDF created from images: " + file.getAbsolutePath();
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

        public MediaTool build() {
            return new MediaTool(baseDir);
        }
    }
}
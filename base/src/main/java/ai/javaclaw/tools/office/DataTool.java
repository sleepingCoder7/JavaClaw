package ai.javaclaw.tools.office;

import org.springframework.ai.tool.annotation.Tool;

import java.io.FileReader;
import java.util.List;

import com.opencsv.CSVReader;
import org.apache.poi.xssf.usermodel.*;

public class DataTool {

    private final String baseDir;

    public DataTool(String baseDir) {
        this.baseDir = baseDir;
    }

    @Tool(description = """
            Handles structured data operations.

            ## Capabilities:
            - Analyze CSV files
            - Analyze Excel (XLSX) files

            ## When to Use:
            - When user asks for insights from CSV or Excel
            - When summarizing datasets
            - When counting rows/columns or extracting statistics

            ## Parameters:
            - operation: One of [analyze_csv, analyze_excel]
            - filePath: Path to the file
            """)
    public String dataOperation(String operation, String filePath) {
        try {
            switch (operation.toLowerCase()) {

                case "analyze_csv":
                    return analyzeCsv(filePath);

                case "analyze_excel":
                    return analyzeExcel(filePath);

                default:
                    return "Unsupported operation";
            }

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String analyzeCsv(String filePath) throws Exception {
        CSVReader reader = new CSVReader(new FileReader(filePath));
        List<String[]> rows = reader.readAll();

        return "CSV rows: " + rows.size() +
               ", columns: " + rows.get(0).length;
    }

    private String analyzeExcel(String filePath) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook(filePath);
        XSSFSheet sheet = workbook.getSheetAt(0);

        return "Excel rows: " + sheet.getPhysicalNumberOfRows();
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

        public DataTool build() {
            return new DataTool(baseDir);
        }
    }
}
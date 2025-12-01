package com.example.msslideapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class ExcelJsonConverter {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Converts Excel → JSON
     */
    public String excelToJson(File excelFile) throws Exception {
        ZipSecureFile.setMinInflateRatio(0.0001);
        FileInputStream fis = new FileInputStream(excelFile);
        XSSFWorkbook wb = new XSSFWorkbook(fis);
        fis.close();

        List<Object> sheets = new ArrayList<Object>();

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            XSSFSheet sheet = wb.getSheetAt(i);
            Map<String, Object> sheetMap = new HashMap<String, Object>();
            String sheetName = sheet.getSheetName();
            sheetMap.put("name", sheetName != null ? sheetName : "Sheet1");

            List<Map<String, Object>> cells = new ArrayList<Map<String, Object>>();
            for (Row row : sheet) {
                if (row == null) continue;
                for (Cell cell : row) {
                    if (cell == null) continue;
                    Map<String, Object> cellMap = new HashMap<String, Object>();
                    cellMap.put("row", row.getRowNum());
                    cellMap.put("col", cell.getColumnIndex());
                    String rawVal = getCellString(cell);
                    cellMap.put("value", rawVal == null ? "" : rawVal);

                    CellStyle cs = cell.getCellStyle();
                    if (cs != null) {
                        XSSFCellStyle xcs = (XSSFCellStyle) cs;
                        XSSFFont font = xcs.getFont();

                        // Font properties
                        if (font != null) {
                            cellMap.put("fontBold", font.getBold());
                            // fontHeightInPoints might be short -> store as int
                            cellMap.put("fontSize", font.getFontHeightInPoints());
                            cellMap.put("italic", font.getItalic());
                            // strikeout
                            cellMap.put("strike", font.getStrikeout());
                            // underline: convert to boolean
                            try {
                                cellMap.put("underline", font.getUnderline() != Font.U_NONE);
                            } catch (Exception e) {
                                // ignore if not available
                            }
                            // font color
                            XSSFColor fColor = null;
                            try {
                                fColor = (font instanceof XSSFFont) ? ((XSSFFont) font).getXSSFColor() : null;
                            } catch (Exception ignored) {}
                            if (fColor != null) {
                                String hex = fColor.getARGBHex();
                                if (hex != null && hex.length() >= 6) {
                                    cellMap.put("fontColor", "#" + hex.substring(hex.length() - 6));
                                }
                            }
                        }

                        // Background color (handle theme, index, ARGB)
                        XSSFColor bg = null;
                        try {
                            bg = xcs.getFillForegroundXSSFColor();
                        } catch (Exception ignored) {}
                        if (bg != null) {
                            String hex = bg.getARGBHex();
                            if (hex != null && hex.length() >= 6) {
                                cellMap.put("bgColor", "#" + hex.substring(hex.length() - 6));
                            }
                        }

                        // Alignment
                        try {
                            HorizontalAlignment ha = xcs.getAlignment();
                            cellMap.put("alignment", ha == null ? "GENERAL" : ha.name());
                        } catch (Exception ignored) {}

                        // Borders
                        try {
                            cellMap.put("borderTop", xcs.getBorderTop() == null ? "NONE" : xcs.getBorderTop().name());
                            cellMap.put("borderBottom", xcs.getBorderBottom() == null ? "NONE" : xcs.getBorderBottom().name());
                            cellMap.put("borderLeft", xcs.getBorderLeft() == null ? "NONE" : xcs.getBorderLeft().name());
                            cellMap.put("borderRight", xcs.getBorderRight() == null ? "NONE" : xcs.getBorderRight().name());
                        } catch (Exception ignored) {}
                    }

                    cells.add(cellMap);
                }
            }

            sheetMap.put("cells", cells);
            sheets.add(sheetMap);
        }

        wb.close();

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("sheets", sheets);

        // PRINT JSON for debugging — useful to inspect the exact JSON the backend committed
        String out = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        // System.out.println("=== EXTRACTED EXCEL JSON START ===");
        // System.out.println(out);
        // System.out.println("=== EXTRACTED EXCEL JSON END ===");

        return out;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return normalizeCellText(cell.getStringCellValue());
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return normalizeCellText(String.valueOf(cell.getDateCellValue()));
                    }
                    // avoid scientific notation; keep as plain number string
                    double numeric = cell.getNumericCellValue();
                    if (Math.floor(numeric) == numeric) {
                        // integer
                        return normalizeCellText(String.valueOf((long) numeric));
                    } else {
                        return normalizeCellText(String.valueOf(numeric));
                    }
                case BOOLEAN:
                    return normalizeCellText(String.valueOf(cell.getBooleanCellValue()));
                case FORMULA:
                    try {
                        // prefer cached string result
                        return normalizeCellText(cell.getStringCellValue());
                    } catch (Exception e) {
                        try {
                            double d = cell.getNumericCellValue();
                            if (Math.floor(d) == d) {
                                return normalizeCellText(String.valueOf((long) d));
                            } else {
                                return normalizeCellText(String.valueOf(d));
                            }
                        } catch (Exception ex) {
                            return "";
                        }
                    }
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    // Normalize text extracted from cell (replace NBSP etc.)
    private String normalizeCellText(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ')
                .replace("\uFFFD", "")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("’", "'")
                .replace("…", "...")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Converts JSON → Excel
     * (Recreates fonts, fills, borders, alignments safely with caching)
     */
    public void jsonToExcel(String json, File outFile) throws Exception {
        Map root = mapper.readValue(json, Map.class);
        XSSFWorkbook wb = new XSSFWorkbook();

        // Cache to prevent 64k style overflow
        Map<String, XSSFCellStyle> styleCache = new HashMap<String, XSSFCellStyle>();

        List sheets = (List) root.get("sheets");
        for (Object s : sheets) {
            Map sMap = (Map) s;
            String name = (String) sMap.get("name");
            XSSFSheet sheet = wb.createSheet(name != null ? name : "Sheet1");

            List cells = (List) sMap.get("cells");
            for (Object o : cells) {
                Map cellMap = (Map) o;
                int r = ((Number) cellMap.get("row")).intValue();
                int c = ((Number) cellMap.get("col")).intValue();
                String value = cellMap.get("value") != null ? String.valueOf(cellMap.get("value")) : "";

                XSSFRow row = sheet.getRow(r);
                if (row == null) row = sheet.createRow(r);
                XSSFCell cell = row.createCell(c);
                cell.setCellValue(value);

                // Style properties
                boolean fontBold = cellMap.get("fontBold") != null && (Boolean) cellMap.get("fontBold");
                short fontSize = cellMap.get("fontSize") != null
                        ? ((Number) cellMap.get("fontSize")).shortValue() : 11;
                String fontColor = (String) cellMap.get("fontColor");
                String bgColor = (String) cellMap.get("bgColor");
                String alignment = (String) cellMap.get("alignment");

                String borderTop = (String) cellMap.get("borderTop");
                String borderBottom = (String) cellMap.get("borderBottom");
                String borderLeft = (String) cellMap.get("borderLeft");
                String borderRight = (String) cellMap.get("borderRight");

                // Create a unique key for caching styles
                String styleKey = fontBold + "_" + fontSize + "_" + fontColor + "_" + bgColor + "_" + alignment + "_"
                        + borderTop + "_" + borderBottom + "_" + borderLeft + "_" + borderRight;

                XSSFCellStyle style = styleCache.get(styleKey);
                if (style == null) {
                    style = wb.createCellStyle();
                    XSSFFont font = wb.createFont();
                    font.setBold(fontBold);
                    font.setFontHeightInPoints(fontSize);

                    if (fontColor != null) {
                        try {
                            java.awt.Color awt = java.awt.Color.decode(fontColor);
                            font.setColor(new XSSFColor(awt, null));
                        } catch (Exception ignored) {}
                    }

                    style.setFont(font);

                    // Background color
                    if (bgColor != null) {
                        try {
                            style.setFillForegroundColor(new XSSFColor(java.awt.Color.decode(bgColor), null));
                            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        } catch (Exception ignored) {}
                    }

                    // Alignment
                    if (alignment != null) {
                        try {
                            style.setAlignment(HorizontalAlignment.valueOf(alignment));
                        } catch (Exception ignored) {}
                    }

                    // Borders
                    setBorder(style::setBorderTop, borderTop);
                    setBorder(style::setBorderBottom, borderBottom);
                    setBorder(style::setBorderLeft, borderLeft);
                    setBorder(style::setBorderRight, borderRight);

                    styleCache.put(styleKey, style);
                }

                cell.setCellStyle(style);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            wb.write(fos);
        }
        wb.close();
    }

    private void setBorder(java.util.function.Consumer<BorderStyle> setter, String borderName) {
        if (borderName == null) return;
        try {
            setter.accept(BorderStyle.valueOf(borderName));
        } catch (Exception ignored) {
            setter.accept(BorderStyle.NONE);
        }
    }
}

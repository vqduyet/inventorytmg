package com.sys4life.inventorytmg.service;

import com.sys4life.inventorytmg.dto.Issue;
import com.sys4life.inventorytmg.dto.Storage;
import com.sys4life.inventorytmg.dto.StorageStructure;
import com.sys4life.inventorytmg.dto.Transfer;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Override
    public void processFile(InputStream file, HttpServletResponse httpServletResponse) throws IOException {
        log.info("processFile - start");
        // read file
        String fileLocation = "classpath:inventory.xlsx";
        Map<String, StorageStructure> storageStructureByProductMap = readInventoryFile(file);
        // process the transfer list
        List<Transfer> transfers = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        processData(transfers, issues, storageStructureByProductMap);
        // print out the results: transfer and issues
        outputResults(transfers, issues, httpServletResponse);
    }

    private Map<String, StorageStructure> readInventoryFile(InputStream file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file);
        Sheet sheet = workbook.getSheetAt(0);
        List<String> headers = new ArrayList<>();
        int rowCount = 0;
        Map<String, StorageStructure> storageStructureByProductMap = new LinkedHashMap<>();
        StorageStructure storageStructure = null;
        for (Row row : sheet) {
            if (rowCount > 0) {
                storageStructure = StorageStructure.builder()
                        .negatives(new ArrayList<>())
                        .zeros(new ArrayList<>())
                        .positives(new ArrayList<>())
                        .positiveLimitOne(new ArrayList<>())
                        .positiveLimitTwo(new ArrayList<>())
                        .positiveLimitThree(new ArrayList<>())
                        .restrictives(new ArrayList<>())
                        .build();
            }
            int cellCount = 0;
            String productCode = "";
            for (Cell cell : row) {
                if (rowCount == 0) { //header
                    if (cellCount > 0) {
                        headers.add(cell.getStringCellValue());
                    }
                } else { //data
                    if (cellCount == 0) {
                        productCode = cell.getStringCellValue();
                    } else {
                        int quantity = (int) cell.getNumericCellValue();
                        int storageIndex = cellCount - 1;
                        if (isRestrictiveStorage(storageIndex, headers)) {
                            storageStructure.getRestrictives().add(Storage.builder()
                                    .name(headers.get(storageIndex))
                                    .quantity(quantity)
                                    .build());
                        } else {
                            if (quantity < 0) {
                                storageStructure.getNegatives().add(Storage.builder()
                                        .name(headers.get(storageIndex))
                                        .quantity(quantity)
                                        .build());
                            } else if (quantity == 0) {
                                storageStructure.getZeros().add(Storage.builder()
                                        .name(headers.get(storageIndex))
                                        .quantity(quantity)
                                        .build());
                            } else if (quantity > 0) {
                                if (isLimitThreeStorage(storageIndex, headers)) {
                                    storageStructure.getPositiveLimitThree().add(Storage.builder()
                                            .name(headers.get(storageIndex))
                                            .quantity(quantity)
                                            .build());
                                } else if (isLimitTwoStorage(storageIndex, headers)) {
                                    storageStructure.getPositiveLimitTwo().add(Storage.builder()
                                            .name(headers.get(storageIndex))
                                            .quantity(quantity)
                                            .build());
                                } else if (isLimitOneStorage(storageIndex, headers)) {
                                    storageStructure.getPositiveLimitOne().add(Storage.builder()
                                            .name(headers.get(storageIndex))
                                            .quantity(quantity)
                                            .build());
                                } else {
                                    storageStructure.getPositives().add(Storage.builder()
                                            .name(headers.get(storageIndex))
                                            .quantity(quantity)
                                            .build());
                                }
                            }
                        }
                    }
                }
                cellCount++;
            }
            if (rowCount > 0) {
                storageStructureByProductMap.put(productCode, storageStructure);
            }
            rowCount++;
        }
        return storageStructureByProductMap;
    }

    private void processData(List<Transfer> transfers, List<Issue> issues, Map<String, StorageStructure> storageStructureByProductMap) {
        for (Map.Entry<String, StorageStructure> entry : storageStructureByProductMap.entrySet()) {
            log.info("productCode {}", entry.getKey());
            StorageStructure structure = entry.getValue();
            // check if restrictives are negative
            if (structure.getRestrictives().stream().filter(x -> x.getQuantity() < 0).count() > 0) {
                Issue issue = new Issue(entry.getKey(), "KHO MAU DO AM");
                issues.add(issue);
                continue;
            }
            // else: processing
            log.info("productCode {} - negatives cnt {}", entry.getKey(), structure.getNegatives().size());
            List<Storage> allPositives = new ArrayList<>();
            allPositives.addAll(structure.getPositives());
            allPositives.addAll(structure.getPositiveLimitOne());
            allPositives.addAll(structure.getPositiveLimitTwo());
            allPositives.addAll(structure.getPositiveLimitThree());

            // check if negatives > sum of positives
            int totalNegativeQuantity = structure.getNegatives().stream()
                    .map(x -> x.getQuantity())
                    .reduce(0, (a, b) -> a + b);
            int totalPositiveQuantity = allPositives.stream()
                    .map(x -> x.getQuantity())
                    .reduce(0, (a, b) -> a + b);
            if ((totalPositiveQuantity + totalNegativeQuantity) < 0) {
                Issue issue = new Issue(entry.getKey(), "TONG DUONG NHO HON TONG AM");
                issues.add(issue);
                continue;
            }

            // process the transfer list
            for (Storage negative : structure.getNegatives()) {
                log.info("productCode {}, negative storage {} , quantity {}",
                        entry.getKey(), negative.getName(), negative.getQuantity());
                int negativeQuantityToTransfer = negative.getQuantity();
                allPositives.removeIf(p -> p.getQuantity() <= 0);
                for (Storage positive : allPositives) {
                    if (positive.getQuantity() >= negativeQuantityToTransfer*(-1)) {
                        int quantityToTransfer = negativeQuantityToTransfer*(-1);
                        Transfer transfer = Transfer.builder()
                                .productCode(entry.getKey())
                                .inStorage(negative.getName())
                                .inQuantity(quantityToTransfer)
                                .outStorage(positive.getName())
                                .outQuantity(positive.getQuantity())
                                .build();
                        transfers.add(transfer);
                        positive.setQuantity(positive.getQuantity() - quantityToTransfer);
                        break;
                    } else {
                        int quantityToTransfer = positive.getQuantity();
                        Transfer transfer = Transfer.builder()
                                .productCode(entry.getKey())
                                .inStorage(negative.getName())
                                .inQuantity(quantityToTransfer)
                                .outStorage(positive.getName())
                                .outQuantity(positive.getQuantity())
                                .build();
                        transfers.add(transfer);
                        positive.setQuantity(0);
                        negativeQuantityToTransfer = negativeQuantityToTransfer + quantityToTransfer;
                    }
                }
            }
        }
    }

    private void outputResults(List<Transfer> transfers, List<Issue> issues,
                               HttpServletResponse httpServletResponse) throws IOException {
        Workbook workbook = new XSSFWorkbook();

        Sheet sheet = workbook.createSheet("Transfers");
        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 6000);
        sheet.setColumnWidth(3, 6000);
        sheet.setColumnWidth(4, 4000);

        int startRow = prepareOutputForIssue(workbook, sheet, issues);
        prepareOutputForTransfer(workbook, sheet, transfers, startRow);

        ServletOutputStream outputStream = httpServletResponse.getOutputStream();
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
    }

    private int prepareOutputForIssue(Workbook workbook, Sheet sheet, List<Issue> issues) {
        if (issues.size() == 0) {
            return 0;
        }
        //header
        Row header = sheet.createRow(0);

        CellStyle headerStyle = workbook.createCellStyle();
        XSSFFont font = ((XSSFWorkbook) workbook).createFont();
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);
        headerStyle.setFont(font);

        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("MA_HANG");
        headerCell.setCellStyle(headerStyle);

        headerCell = header.createCell(1);
        headerCell.setCellValue("VAN_DE");
        headerCell.setCellStyle(headerStyle);

        //content
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        int rowCount = 1;
        Row row;
        for (Issue issue : issues) {
            row = sheet.createRow(rowCount++);
            int cellCount = 0;

            Cell cell = row.createCell(cellCount++);
            cell.setCellValue(issue.getProductCode());
            cell.setCellStyle(style);

            cell = row.createCell(cellCount++);
            cell.setCellValue(issue.getIssue());
            cell.setCellStyle(style);
        }
        return ++rowCount;
    }

    private void prepareOutputForTransfer(Workbook workbook, Sheet sheet, List<Transfer> transfers, int startRow) {
        //header
        Row header = sheet.createRow(startRow);

        CellStyle headerStyle = workbook.createCellStyle();
        XSSFFont font = ((XSSFWorkbook) workbook).createFont();
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);
        headerStyle.setFont(font);

        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("MA_KHO_XUAT");
        headerCell.setCellStyle(headerStyle);

        headerCell = header.createCell(1);
        headerCell.setCellValue("SO_LUONG");
        headerCell.setCellStyle(headerStyle);

        headerCell = header.createCell(2);
        headerCell.setCellValue("MA_KHO_NHAN");
        headerCell.setCellStyle(headerStyle);

        headerCell = header.createCell(3);
        headerCell.setCellValue("MA_HANG");
        headerCell.setCellStyle(headerStyle);

        headerCell = header.createCell(4);
        headerCell.setCellValue("SO_LUONG");
        headerCell.setCellStyle(headerStyle);

        //content
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        int rowCount = startRow + 1;
        Row row;
        for (Transfer transfer : transfers) {
            row = sheet.createRow(rowCount++);
            int cellCount = 0;

            Cell cell = row.createCell(cellCount++);
            cell.setCellValue(transfer.getOutStorage());
            cell.setCellStyle(style);

            cell = row.createCell(cellCount++);
            cell.setCellValue(transfer.getOutQuantity());
            cell.setCellStyle(style);

            cell = row.createCell(cellCount++);
            cell.setCellValue(transfer.getInStorage());
            cell.setCellStyle(style);

            cell = row.createCell(cellCount++);
            cell.setCellValue(transfer.getProductCode());
            cell.setCellStyle(style);

            cell = row.createCell(cellCount++);
            cell.setCellValue(transfer.getInQuantity());
            cell.setCellStyle(style);
        }
    }

    private boolean isRestrictiveStorage(int storageIndex, List<String> storageNames) {
        return Arrays.asList("HCM-0067_TON_CUOI", "MARETING_TON_CUOI")
                .contains(storageNames.get(storageIndex));
    }

    private boolean isLimitOneStorage(int storageIndex, List<String> storageNames) {
        return storageNames.get(storageIndex).startsWith("EVE");
    }

    private boolean isLimitTwoStorage(int storageIndex, List<String> storageNames) {
        return storageNames.get(storageIndex).startsWith("DLY")
                || (storageNames.get(storageIndex).startsWith("KH")
                && !storageNames.get(storageIndex).startsWith("KHOLOI"));
    }

    private boolean isLimitThreeStorage(int storageIndex, List<String> storageNames) {
        return storageNames.get(storageIndex).startsWith("KHOLOI");
    }
}

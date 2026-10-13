package org.dromara.mes.listener;

import jakarta.validation.Validation;
import org.apache.fesod.sheet.FesodSheet;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.mes.domain.vo.ProductExcelRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.IntStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 真实工作簿读写，包含前导零、行号和导入上限。 */
class ProductExcelTest {
    @Test
    void blankTemplateFormatsFutureInputAsText() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExcelBuilder.of(List.<ProductExcelRow>of(), ProductExcelRow.class)
            .registerWriteHandler(new ProductExcelTextHandler()).toResponse(response);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            for (int column = 0; column < 6; column++) {
                assertEquals("@", workbook.getSheetAt(0).getColumnStyle(column).getDataFormatString());
            }
        }
    }
    @Test
    void roundTripKeepsLeadingZeroIdentifiers() throws Exception {
        ProductExcelRow row = row("000001");
        row.setDrawingNo("00002");
        row.setCustomerPartNo("00003");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExcelBuilder.of(List.of(row), ProductExcelRow.class).toResponse(response);
        ProductImportListener listener = read(response.getContentAsByteArray());
        assertTrue(listener.getErrors().isEmpty());
        assertEquals("000001", listener.getRows().getFirst().getProductCode());
        assertEquals("00002", listener.getRows().getFirst().getDrawingNo());
        assertEquals("00003", listener.getRows().getFirst().getCustomerPartNo());
        fixture("valid.xlsx", response.getContentAsByteArray());
    }

    @Test
    void reportsInvalidRowsUsingRealExcelRowNumbers() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FesodSheet.write(bytes, ProductExcelRow.class).sheet().doWrite(List.of(row("A"), row(" ")));
        ProductImportListener listener = read(bytes.toByteArray());
        assertEquals(3, listener.getErrors().getFirst().rowNumber());
        assertTrue(listener.getErrors().getFirst().reason().contains("编码不能为空"));
        fixture("invalid.xlsx", bytes.toByteArray());
    }

    @Test
    void rejectsWrongHeadersAndOversizedBatch() {
        ByteArrayOutputStream wrong = new ByteArrayOutputStream();
        FesodSheet.write(wrong).head(List.of(List.of("错误列"))).sheet().doWrite(List.of(List.of("A")));
        assertFalse(read(wrong.toByteArray()).getErrors().isEmpty());
        fixture("wrong-header.xlsx", wrong.toByteArray());
        ByteArrayOutputStream large = new ByteArrayOutputStream();
        FesodSheet.write(large, ProductExcelRow.class).sheet().doWrite(IntStream.range(0, 5001).mapToObj(i -> row("P" + i)).toList());
        ProductImportListener listener = read(large.toByteArray());
        assertEquals(5000, listener.getRows().size());
        assertTrue(listener.getErrors().getLast().reason().contains("5000"));
        fixture("over-limit.xlsx", large.toByteArray());
        ByteArrayOutputStream race = new ByteArrayOutputStream();
        FesodSheet.write(race, ProductExcelRow.class).sheet().doWrite(IntStream.range(0, 1000)
            .mapToObj(i -> row(i == 999 ? "RACE-IMPORT" : "RACE-BATCH-" + i)).toList());
        fixture("race.xlsx", race.toByteArray());
    }

    private ProductImportListener read(byte[] bytes) {
        ProductImportListener listener = new ProductImportListener(Validation.buildDefaultValidatorFactory().getValidator());
        ExcelBuilder.read(new ByteArrayInputStream(bytes), ProductExcelRow.class).sheetNo(0).listener(listener).doRead();
        return listener;
    }

    private ProductExcelRow row(String code) {
        ProductExcelRow row = new ProductExcelRow(); row.setProductCode(code); row.setProductName("支架");
        return row;
    }

    private void fixture(String name, byte[] content) {
        try {
            Path directory = Path.of("target", "acceptance-fixtures");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), content);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}

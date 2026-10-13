package org.dromara.mes.listener;

import jakarta.validation.Validator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelDataConvertException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.core.DefaultExcelResult;
import org.dromara.common.excel.core.ExcelListener;
import org.dromara.common.excel.core.ExcelResult;
import org.dromara.mes.domain.vo.ProductExcelRow;
import org.dromara.mes.domain.vo.ProductImportResult.ImportError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 有界读取第一张表，校验阶段不产生任何数据库写入。 */
@Getter
@RequiredArgsConstructor
public class ProductImportListener extends AnalysisEventListener<ProductExcelRow> implements ExcelListener<ProductExcelRow> {
    public static final int MAX_ROWS = 5000;
    private static final List<String> HEADERS = List.of("产品编码", "产品名称", "规格", "材料牌号", "图号", "客户料号");
    private final Validator validator;
    private final List<ProductExcelRow> rows = new ArrayList<>();
    private final List<ImportError> errors = new ArrayList<>();
    private boolean headerValid = true;
    private boolean limitExceeded;

    @Override
    public void invokeHeadMap(Map<Integer, String> head, AnalysisContext context) {
        for (int i = 0; i < HEADERS.size(); i++) {
            if (!HEADERS.get(i).equals(StringUtils.trim(head.get(i)))) {
                headerValid = false;
                errors.add(new ImportError(1, "", "表头与模板不一致，请使用产品资料导入模板"));
                break;
            }
        }
    }

    @Override
    public void invoke(ProductExcelRow row, AnalysisContext context) {
        int rowNumber = context.readRowHolder().getRowIndex() + 1;
        if (rows.size() >= MAX_ROWS) {
            limitExceeded = true;
            errors.add(new ImportError(rowNumber, row.getProductCode(), "单次最多导入 5000 条产品"));
            return;
        }
        row.setRowNumber(rowNumber);
        row.setProductCode(StringUtils.trimToEmpty(row.getProductCode()));
        row.setProductName(StringUtils.trimToEmpty(row.getProductName()));
        row.setSpecification(StringUtils.trimToEmpty(row.getSpecification()));
        row.setMaterialGrade(StringUtils.trimToEmpty(row.getMaterialGrade()));
        row.setDrawingNo(StringUtils.trimToEmpty(row.getDrawingNo()));
        row.setCustomerPartNo(StringUtils.trimToEmpty(row.getCustomerPartNo()));
        rows.add(row);
        String message = validator.validate(row).stream().map(v -> v.getMessage()).sorted().collect(Collectors.joining("；"));
        if (!message.isEmpty()) {
            errors.add(new ImportError(rowNumber, row.getProductCode(), message));
        }
    }

    @Override
    public boolean hasNext(AnalysisContext context) {
        return headerValid && !limitExceeded;
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        if (exception instanceof ExcelDataConvertException conversion) {
            errors.add(new ImportError(conversion.getRowIndex() + 1, "", "第 " + (conversion.getColumnIndex() + 1) + " 列无法读取，请按文本填写"));
        } else {
            throw exception;
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 完成读取后由业务层统一查重及事务写入。
    }

    @Override
    public ExcelResult<ProductExcelRow> getExcelResult() {
        return new DefaultExcelResult<>(rows, errors.stream().map(ImportError::reason).toList());
    }
}

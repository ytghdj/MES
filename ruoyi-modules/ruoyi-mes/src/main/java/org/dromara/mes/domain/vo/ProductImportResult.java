package org.dromara.mes.domain.vo;

import java.util.List;

/** 整批导入结果：errors 非空时 importedCount 必须为零。 */
public record ProductImportResult(int importedCount, List<ImportError> errors) {
    public record ImportError(int rowNumber, String productCode, String reason) {}
}

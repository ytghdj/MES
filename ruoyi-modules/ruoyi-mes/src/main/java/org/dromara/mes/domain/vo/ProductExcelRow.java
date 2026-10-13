package org.dromara.mes.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnore;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ContentStyle;
import org.dromara.mes.domain.MesProduct;

/** 六列产品资料模板。所有单元格采用文本格式，保留料号前导零。 */
@Data
@ExcelIgnoreUnannotated
@ContentStyle(dataFormat = 49)
@AutoMapper(target = MesProduct.class)
public class ProductExcelRow {
    @ExcelProperty(value = "产品编码", index = 0)
    @NotBlank(message = "产品编码不能为空")
    @Size(max = 64, message = "产品编码最多 64 个字符")
    private String productCode;
    @ExcelProperty(value = "产品名称", index = 1)
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 128, message = "产品名称最多 128 个字符")
    private String productName;
    @ExcelProperty(value = "规格", index = 2)
    @Size(max = 255, message = "规格最多 255 个字符")
    private String specification;
    @ExcelProperty(value = "材料牌号", index = 3)
    @Size(max = 64, message = "材料牌号最多 64 个字符")
    private String materialGrade;
    @ExcelProperty(value = "图号", index = 4)
    @Size(max = 64, message = "图号最多 64 个字符")
    private String drawingNo;
    @ExcelProperty(value = "客户料号", index = 5)
    @Size(max = 64, message = "客户料号最多 64 个字符")
    private String customerPartNo;
    /** 实际 Excel 行号，空行不会改变报错定位。 */
    @ExcelIgnore
    private Integer rowNumber;
}

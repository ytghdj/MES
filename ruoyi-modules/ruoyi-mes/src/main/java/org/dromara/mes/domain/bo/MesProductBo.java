package org.dromara.mes.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.mes.domain.MesProduct;
import java.io.Serializable;

/** 产品编辑及查询参数；业务字段在校验前裁剪空格。 */
@Data
@AutoMapper(target = MesProduct.class, reverseConvertGenerate = false)
public class MesProductBo implements Serializable {
    @NotNull(message = "产品 ID 不能为空", groups = EditGroup.class)
    @Positive(groups = EditGroup.class)
    private Long id;
    /** 产品编码。 */
    @NotBlank(message = "产品编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "产品编码最多 64 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String productCode;
    /** 产品名称。 */
    @NotBlank(message = "产品名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 128, message = "产品名称最多 128 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String productName;
    /** 规格。 */
    @Size(max = 255, message = "规格最多 255 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String specification;
    /** 材料牌号。 */
    @Size(max = 64, message = "材料牌号最多 64 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String materialGrade;
    /** 图号。 */
    @Size(max = 64, message = "图号最多 64 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String drawingNo;
    /** 客户料号。 */
    @Size(max = 64, message = "客户料号最多 64 个字符", groups = {AddGroup.class, EditGroup.class, QueryGroup.class})
    private String customerPartNo;
    /** 状态查询条件。新增及编辑不能通过此字段更改启停状态。 */
    @Pattern(regexp = "[01]", message = "产品状态无效", groups = QueryGroup.class)
    private String status;
    /** 附件文件 ID，空字符串表示解除全部绑定。 */
    @Size(max = 128, groups = {AddGroup.class, EditGroup.class})
    private String attachmentIds;
    @NotNull(message = "版本号不能为空，请刷新后重试", groups = EditGroup.class)
    @Min(value = 0, groups = EditGroup.class)
    private Long version;
}

package org.dromara.mes.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** 产品主档，关联关系始终使用固定 ID。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_product")
public class MesProduct extends BaseEntity {
    /** 产品 ID。 */
    @TableId
    private Long id;
    /** 产品编码。 */
    private String productCode;
    /** 产品名称。 */
    private String productName;
    /** 规格。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specification;
    /** 材料牌号。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String materialGrade;
    /** 图号。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String drawingNo;
    /** 客户料号。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    /** 状态：0 启用、1 停用。 */
    private String status;
    /** 逗号分隔的 OSS 文件 ID，不存访问 URL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String attachmentIds;
    /** 并发编辑版本。 */
    @Version
    private Long version;
    /** 逻辑删除标志。 */
    @TableLogic
    private String delFlag;
}

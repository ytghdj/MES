package org.dromara.mes.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.mes.domain.MesProduct;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 产品列表及详情。附件明细仅在详情中加载。 */
@Data
@AutoMapper(target = MesProduct.class)
public class MesProductVo implements Serializable {
    private Long id;
    /** 产品编码。 */
    private String productCode;
    /** 产品名称。 */
    private String productName;
    /** 规格。 */
    private String specification;
    /** 材料牌号。 */
    private String materialGrade;
    /** 图号。 */
    private String drawingNo;
    /** 客户料号。 */
    private String customerPartNo;
    private String status;
    private String attachmentIds;
    private Long version;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private List<ProductAttachmentVo> attachments;
    public int getAttachmentCount() {
        return attachmentIds == null || attachmentIds.isBlank() ? 0 : attachmentIds.split(",").length;
    }
}

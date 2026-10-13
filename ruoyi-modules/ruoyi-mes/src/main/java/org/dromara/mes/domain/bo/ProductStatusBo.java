package org.dromara.mes.domain.bo;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 使用独立的状态参数，避免绕过版本校验。 */
@Data
public class ProductStatusBo {
    @NotNull @Positive
    private Long id;
    @NotNull @Pattern(regexp = "[01]", message = "产品状态无效")
    private String status;
    @NotNull(message = "版本号不能为空，请刷新后重试") @Min(0)
    private Long version;
}

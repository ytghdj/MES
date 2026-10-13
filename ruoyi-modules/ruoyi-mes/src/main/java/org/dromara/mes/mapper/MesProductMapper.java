package org.dromara.mes.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.mes.domain.MesProduct;
import org.dromara.mes.domain.vo.MesProductVo;
import java.util.Collection;
import java.util.List;

/** 产品 Mapper。编码查重显式包含逻辑删除记录。 */
public interface MesProductMapper extends BaseMapperPlus<MesProduct, MesProductVo> {
    @Select("SELECT COUNT(*) FROM mes_product WHERE product_code = #{code} AND (#{id,jdbcType=BIGINT} IS NULL OR id != #{id,jdbcType=BIGINT})")
    long countCode(@Param("code") String code, @Param("id") Long id);

    @Select({"<script>SELECT product_code FROM mes_product WHERE product_code IN",
        "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>"})
    List<String> existingCodes(@Param("codes") Collection<String> codes);
}

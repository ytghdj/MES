package org.dromara.mes.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.mes.domain.bo.MesProductBo;
import org.dromara.mes.domain.bo.ProductStatusBo;
import org.dromara.mes.domain.vo.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** 产品主档业务接口。 */
public interface IMesProductService {
    MesProductVo queryById(Long id);
    PageResult<MesProductVo> queryPageList(MesProductBo bo, PageQuery pageQuery);
    List<ProductExcelRow> exportRows(MesProductBo bo);
    void insertByBo(MesProductBo bo);
    void updateByBo(MesProductBo bo);
    void changeStatus(ProductStatusBo bo);
    void deleteByIds(Collection<Long> ids);
    ProductImportResult importData(MultipartFile file) throws IOException;
    ProductAttachmentVo uploadAttachment(MultipartFile file) throws IOException;
    ResponseEntity<byte[]> attachment(Long id, Long ossId, boolean preview);
}

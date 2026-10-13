package org.dromara.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.mes.domain.bo.MesProductBo;
import org.dromara.mes.domain.bo.ProductStatusBo;
import org.dromara.mes.domain.vo.*;
import org.dromara.mes.service.IMesProductService;
import org.dromara.mes.listener.ProductExcelTextHandler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 产品资料及其附件；业务用户无需全局 OSS 权限。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/mes/product")
public class MesProductController extends BaseController {
    private final IMesProductService productService;

    @SaCheckPermission("mes:product:list")
    @GetMapping("/list")
    public R<PageResult<MesProductVo>> list(MesProductBo bo, PageQuery pageQuery) {
        return R.ok(productService.queryPageList(bo, pageQuery));
    }

    @SaCheckPermission("mes:product:query")
    @GetMapping("/{id}")
    public R<MesProductVo> getInfo(@PathVariable Long id) {
        return R.ok(productService.queryById(id));
    }

    @SaCheckPermission("mes:product:add")
    @Log(title = "产品资料", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@RequestBody MesProductBo bo) {
        productService.insertByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("mes:product:edit")
    @Log(title = "产品资料", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@RequestBody MesProductBo bo) {
        productService.updateByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("mes:product:edit")
    @Log(title = "产品启停", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody ProductStatusBo bo) {
        productService.changeStatus(bo);
        return R.ok();
    }

    @SaCheckPermission("mes:product:remove")
    @Log(title = "产品资料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] ids) {
        productService.deleteByIds(List.of(ids));
        return R.ok();
    }

    @SaCheckPermission("mes:product:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelBuilder.of(List.<ProductExcelRow>of(), ProductExcelRow.class).sheetName("产品资料")
            .registerWriteHandler(new ProductExcelTextHandler()).toResponse(response);
    }

    @SaCheckPermission("mes:product:import")
    @Log(title = "产品资料", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ProductImportResult> importData(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(productService.importData(file));
    }

    @SaCheckPermission("mes:product:export")
    @Log(title = "产品资料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MesProductBo bo, HttpServletResponse response) {
        ExcelBuilder.of(productService.exportRows(bo), ProductExcelRow.class).sheetName("产品资料").toResponse(response);
    }

    @SaCheckPermission("mes:product:attachmentUpload")
    @Log(title = "产品图纸上传", businessType = BusinessType.INSERT)
    @PostMapping(value = "/attachment/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ProductAttachmentVo> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(productService.uploadAttachment(file));
    }

    @SaCheckPermission("mes:product:attachmentPreview")
    @GetMapping("/{id}/attachment/{ossId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id, @PathVariable Long ossId) {
        return productService.attachment(id, ossId, true);
    }

    @SaCheckPermission("mes:product:attachmentDownload")
    @GetMapping("/{id}/attachment/{ossId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long ossId) {
        return productService.attachment(id, ossId, false);
    }
}

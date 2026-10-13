package org.dromara.mes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.excel.utils.ExcelBuilder;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.query.QueryBuilder;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.mes.domain.MesProduct;
import org.dromara.mes.domain.bo.MesProductBo;
import org.dromara.mes.domain.bo.ProductStatusBo;
import org.dromara.mes.domain.vo.*;
import org.dromara.mes.domain.vo.ProductImportResult.ImportError;
import org.dromara.mes.listener.ProductImportListener;
import org.dromara.mes.mapper.MesProductMapper;
import org.dromara.mes.service.IMesProductService;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** 产品主档：全局共享，权限由控制器限定，不按创建部门过滤。 */
@Service
@RequiredArgsConstructor
public class MesProductServiceImpl implements IMesProductService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final String FILE_BUSINESS = "mes_product";
    private final MesProductMapper productMapper;
    private final ISysOssService ossService;
    private final Validator validator;
    private final PlatformTransactionManager transactionManager;

    @Override
    public MesProductVo queryById(Long id) {
        MesProductVo result = MapstructUtils.convert(requiredProduct(id), MesProductVo.class);
        List<Long> ids = attachmentIds(result.getAttachmentIds());
        result.setAttachments(ids.isEmpty() ? List.of() : ossService.listByIds(ids).stream().map(this::attachmentVo).toList());
        return result;
    }

    @Override
    public PageResult<MesProductVo> queryPageList(MesProductBo bo, PageQuery pageQuery) {
        Page<MesProductVo> page = productMapper.selectVoPage(pageQuery.build(), query(bo));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public List<ProductExcelRow> exportRows(MesProductBo bo) {
        return MapstructUtils.convert(productMapper.selectList(query(bo)), ProductExcelRow.class);
    }

    private LambdaQueryWrapper<MesProduct> query(MesProductBo bo) {
        normalize(bo);
        validate(bo, QueryGroup.class);
        return QueryBuilder.lambda(MesProduct.class)
            .likeIfText(MesProduct::getProductCode, bo.getProductCode())
            .likeIfText(MesProduct::getProductName, bo.getProductName())
            .likeIfText(MesProduct::getSpecification, bo.getSpecification())
            .likeIfText(MesProduct::getMaterialGrade, bo.getMaterialGrade())
            .likeIfText(MesProduct::getDrawingNo, bo.getDrawingNo())
            .likeIfText(MesProduct::getCustomerPartNo, bo.getCustomerPartNo())
            .eqIfText(MesProduct::getStatus, bo.getStatus())
            .orderByDesc(MesProduct::getCreateTime, MesProduct::getId).build();
    }

    @Override
    public void insertByBo(MesProductBo bo) {
        normalize(bo);
        validate(bo, AddGroup.class);
        checkCode(bo.getProductCode(), null);
        validateAttachments(bo, List.of());
        MesProduct product = MapstructUtils.convert(bo, MesProduct.class);
        product.setId(null);
        product.setStatus("0");
        product.setVersion(0L);
        product.setDelFlag("0");
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException("产品编码已存在（包括已删除的产品）");
        }
    }

    @Override
    public void updateByBo(MesProductBo bo) {
        normalize(bo);
        validate(bo, EditGroup.class);
        MesProduct current = requiredProduct(bo.getId());
        checkCode(bo.getProductCode(), bo.getId());
        validateAttachments(bo, attachmentIds(current.getAttachmentIds()));
        MesProduct product = MapstructUtils.convert(bo, MesProduct.class);
        product.setStatus(current.getStatus());
        try {
            checkUpdated(productMapper.updateById(product));
        } catch (DuplicateKeyException ex) {
            throw new ServiceException("产品编码已存在（包括已删除的产品）");
        }
    }

    @Override
    public void changeStatus(ProductStatusBo bo) {
        validate(bo);
        MesProduct product = requiredProduct(bo.getId());
        product.setStatus(bo.getStatus());
        product.setVersion(bo.getVersion());
        checkUpdated(productMapper.updateById(product));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.isEmpty() || productMapper.selectByIds(uniqueIds).size() != uniqueIds.size()) {
            throw new ServiceException("部分产品不存在或已删除，请刷新列表");
        }
        productMapper.deleteByIds(uniqueIds);
        // 只逻辑删除产品，保留所有 OSS 文件。
    }

    private void checkCode(String code, Long id) {
        if (productMapper.countCode(code, id) > 0) {
            throw new ServiceException("产品编码已存在（包括已删除的产品）");
        }
    }

    private void checkUpdated(int rows) {
        if (rows != 1) {
            throw new ServiceException("产品已被其他人修改或删除，请刷新后重试");
        }
    }

    private MesProduct requiredProduct(Long id) {
        MesProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new ServiceException("产品不存在或已删除");
        }
        return product;
    }

    private void normalize(MesProductBo bo) {
        bo.setProductCode(StringUtils.trimToEmpty(bo.getProductCode()));
        bo.setProductName(StringUtils.trimToEmpty(bo.getProductName()));
        bo.setSpecification(StringUtils.trimToEmpty(bo.getSpecification()));
        bo.setMaterialGrade(StringUtils.trimToEmpty(bo.getMaterialGrade()));
        bo.setDrawingNo(StringUtils.trimToEmpty(bo.getDrawingNo()));
        bo.setCustomerPartNo(StringUtils.trimToEmpty(bo.getCustomerPartNo()));
        bo.setStatus(StringUtils.trimToNull(bo.getStatus()));
    }

    private <T> void validate(T value, Class<?>... groups) {
        var violations = validator.validate(value, groups);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @Override
    public ProductImportResult importData(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE || !StringUtils.endsWithIgnoreCase(file.getOriginalFilename(), ".xlsx")) {
            return new ProductImportResult(0, List.of(new ImportError(1, "", "请上传不超过 10 MB 的 .xlsx 文件")));
        }
        ProductImportListener listener = new ProductImportListener(validator);
        try (var stream = file.getInputStream()) {
            ExcelBuilder.read(stream, ProductExcelRow.class).sheetNo(0).ignoreEmptyRow(true).autoTrim(true).listener(listener).doRead();
        } catch (Exception ex) {
            return new ProductImportResult(0, List.of(new ImportError(1, "", "无法读取 Excel，请使用产品资料模板")));
        }
        return importRows(listener.getRows(), new ArrayList<>(listener.getErrors()));
    }

    /** 先聚合全部错误，数据库写入统一置于事务中，唯一冲突不能留下半批数据。 */
    ProductImportResult importRows(List<ProductExcelRow> rows, List<ImportError> errors) {
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add(new ImportError(2, "", "文件没有产品数据"));
        }
        Set<String> codes = new HashSet<>();
        for (ProductExcelRow row : rows) {
            if (!codes.add(row.getProductCode().toLowerCase(Locale.ROOT))) {
                errors.add(new ImportError(row.getRowNumber(), row.getProductCode(), "文件内产品编码重复"));
            }
        }
        existingCodeErrors(rows, errors);
        if (!errors.isEmpty()) {
            return new ProductImportResult(0, errors);
        }
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                for (ProductExcelRow row : rows) {
                    MesProduct product = MapstructUtils.convert(row, MesProduct.class);
                    product.setStatus("0");
                    product.setAttachmentIds("");
                    product.setVersion(0L);
                    product.setDelFlag("0");
                    productMapper.insert(product);
                }
            });
        } catch (DuplicateKeyException ex) {
            existingCodeErrors(rows, errors);
            if (errors.isEmpty()) {
                errors.add(new ImportError(rows.getFirst().getRowNumber(), rows.getFirst().getProductCode(), "并发导入编码冲突，整批未写入，请重试"));
            }
            return new ProductImportResult(0, errors);
        }
        return new ProductImportResult(rows.size(), List.of());
    }

    private void existingCodeErrors(List<ProductExcelRow> rows, List<ImportError> errors) {
        Set<String> requested = rows.stream().map(ProductExcelRow::getProductCode).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        if (requested.isEmpty()) {
            return;
        }
        Set<String> existing = productMapper.existingCodes(requested).stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        rows.stream().filter(row -> existing.contains(row.getProductCode().toLowerCase(Locale.ROOT)))
            .forEach(row -> errors.add(new ImportError(row.getRowNumber(), row.getProductCode(), "产品编码已存在（包括已删除产品），不允许覆盖")));
    }

    private List<Long> attachmentIds(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        try {
            List<Long> ids = Arrays.stream(value.split(",", -1)).map(String::trim).map(Long::valueOf).toList();
            if (ids.size() > 5 || new HashSet<>(ids).size() != ids.size() || ids.stream().anyMatch(id -> id <= 0)) {
                throw new ServiceException("附件最多 5 个，且不能重复");
            }
            return ids;
        } catch (NumberFormatException ex) {
            throw new ServiceException("附件编号无效");
        }
    }

    private void validateAttachments(MesProductBo bo, List<Long> retainedIds) {
        List<Long> ids = attachmentIds(bo.getAttachmentIds());
        for (Long id : ids) {
            SysOssVo file = ossService.getById(id);
            SysOssExt ext = file == null ? null : JsonUtils.parseObject(file.getExt1(), SysOssExt.class);
            if (ext == null || !FILE_BUSINESS.equals(ext.getBizType())
                || (!retainedIds.contains(id) && !Objects.equals(file.getCreateBy(), LoginHelper.getUserId()))) {
                throw new ServiceException("附件不存在或不是本人上传的产品文件");
            }
        }
        bo.setAttachmentIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    @Override
    public ProductAttachmentVo uploadAttachment(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("附件不能为空，且单文件不能超过 10 MB");
        }
        String name = file.getOriginalFilename();
        String type = contentType(name);
        byte[] header;
        try (var input = file.getInputStream()) {
            header = input.readNBytes(8);
        }
        boolean valid = switch (type) {
            case "application/pdf" -> header.length >= 5 && new String(header, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "image/png" -> Arrays.equals(header, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            default -> header.length >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216 && (header[2] & 255) == 255;
        };
        if (!valid) {
            throw new ServiceException("文件内容与 PDF/图片格式不匹配");
        }
        SysOssExt ext = new SysOssExt();
        ext.setBizType(FILE_BUSINESS);
        ext.setSource("productUpload");
        return attachmentVo(ossService.upload(file, ext));
    }

    private String contentType(String name) {
        String extension = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> throw new ServiceException("仅支持 PDF、PNG、JPG/JPEG 文件");
        };
    }

    private ProductAttachmentVo attachmentVo(SysOssVo file) {
        SysOssExt ext = JsonUtils.parseObject(file.getExt1(), SysOssExt.class);
        return new ProductAttachmentVo(file.getOssId(), file.getOriginalName(), ext == null ? null : ext.getFileSize(), contentType(file.getOriginalName()));
    }

    @Override
    public ResponseEntity<byte[]> attachment(Long id, Long ossId, boolean preview) {
        MesProduct product = requiredProduct(id);
        if (!attachmentIds(product.getAttachmentIds()).contains(ossId)) {
            throw new ServiceException("该附件不属于此产品");
        }
        SysOssVo file = ossService.getById(ossId);
        if (file == null) {
            throw new ServiceException("附件文件已不存在");
        }
        ResponseEntity<byte[]> response = ossService.download(ossId);
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(response.getHeaders());
        headers.setContentType(MediaType.parseMediaType(contentType(file.getOriginalName())));
        headers.set("X-Content-Type-Options", "nosniff");
        if (preview) {
            headers.setContentDisposition(ContentDisposition.inline().filename(file.getOriginalName(), StandardCharsets.UTF_8).build());
        }
        return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
    }
}

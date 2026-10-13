package org.dromara.mes.service.impl;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import io.github.linpeilie.Converter;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import cn.hutool.extra.spring.SpringUtil;
import org.dromara.mes.domain.MesProduct;
import org.dromara.mes.domain.bo.MesProductBo;
import org.dromara.mes.domain.bo.ProductStatusBo;
import org.dromara.mes.domain.vo.ProductExcelRow;
import org.dromara.mes.mapper.MesProductMapper;
import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 数据边界、并发失败和整批回滚的业务回归。 */
class MesProductServiceTest {
    private static Validator validator;
    private MesProductMapper mapper;
    private ISysOssService oss;
    private PlatformTransactionManager tx;
    private MesProductServiceImpl service;

    @BeforeAll
    static void initializeConversion() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        Converter converter = mock(Converter.class);
        when(converter.convert(any(Object.class), any(Class.class))).thenAnswer(call -> {
            Object target = ((Class<?>) call.getArgument(1)).getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(call.getArgument(0), target);
            return target;
        });
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
            MapstructUtils.convert(new MesProductBo(), MesProduct.class);
        }
    }

    @BeforeEach
    void setup() {
        mapper = mock(MesProductMapper.class);
        oss = mock(ISysOssService.class);
        tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.existingCodes(anyCollection())).thenReturn(List.of());
        service = new MesProductServiceImpl(mapper, oss, validator, tx);
    }

    @Test
    void trimsFieldsAndSetsDefaultsWithoutAcceptingClientIdOrStatus() {
        MesProductBo bo = product("  0001  ", "  支架  ");
        bo.setId(99L);
        bo.setStatus("1");
        bo.setSpecification("  10×20  ");
        service.insertByBo(bo);
        var captor = org.mockito.ArgumentCaptor.forClass(MesProduct.class);
        verify(mapper).insert(captor.capture());
        assertEquals("0001", captor.getValue().getProductCode());
        assertEquals("支架", captor.getValue().getProductName());
        assertEquals("10×20", captor.getValue().getSpecification());
        assertEquals("0", captor.getValue().getStatus());
        assertEquals(0L, captor.getValue().getVersion());
        assertNull(captor.getValue().getId());
    }

    @Test
    void rejectsBlankAndOverlongFieldsBeforeWriting() {
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.insertByBo(product(" ", "支架")));
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.insertByBo(product("A".repeat(65), "支架")));
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> service.insertByBo(product("A", " ")));
        verify(mapper, never()).insert(any(MesProduct.class));
    }

    @Test
    void deletedCodesRemainReservedAndDatabaseRacesAreReported() {
        when(mapper.countCode("a", null)).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.insertByBo(product("a", "支架")));
        when(mapper.insert(any(MesProduct.class))).thenThrow(new DuplicateKeyException("race"));
        assertThrows(ServiceException.class, () -> service.insertByBo(product("b", "支架")));
    }

    @Test
    void editingKeepsIdAndClearsOptionalFields() {
        MesProduct current = new MesProduct();
        current.setId(10L);
        current.setStatus("1");
        current.setAttachmentIds("");
        when(mapper.selectById(10L)).thenReturn(current);
        when(mapper.updateById(any(MesProduct.class))).thenReturn(1);
        MesProductBo bo = product("new-code", "支架");
        bo.setId(10L);
        bo.setVersion(3L);
        service.updateByBo(bo);
        var captor = org.mockito.ArgumentCaptor.forClass(MesProduct.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertEquals("new-code", captor.getValue().getProductCode());
        assertEquals("", captor.getValue().getSpecification());
        assertEquals("1", captor.getValue().getStatus());
    }

    @Test
    void staleEditsAndStatusChangesFailWithoutDeletingFiles() {
        MesProduct current = new MesProduct();
        current.setId(10L);
        current.setStatus("0");
        current.setAttachmentIds("");
        when(mapper.selectById(10L)).thenReturn(current);
        MesProductBo edit = product("A", "支架");
        edit.setId(10L);
        edit.setVersion(0L);
        assertThrows(ServiceException.class, () -> service.updateByBo(edit));
        ProductStatusBo status = new ProductStatusBo();
        status.setId(10L); status.setStatus("1"); status.setVersion(0L);
        assertThrows(ServiceException.class, () -> service.changeStatus(status));
        verifyNoInteractions(oss);
    }

    @Test
    void rejectsDuplicateCsvAndMissingAttachments() {
        MesProductBo bo = product("A", "支架");
        bo.setAttachmentIds("1,1");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        bo.setAttachmentIds("1,2,3,4,5,6");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        bo.setAttachmentIds("not-an-id");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        bo.setAttachmentIds("100");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verify(mapper, never()).insert(any(MesProduct.class));
    }

    @Test
    void cannotPreviewAnotherProductsFile() {
        MesProduct product = new MesProduct();
        product.setAttachmentIds("10");
        when(mapper.selectById(1L)).thenReturn(product);
        assertThrows(ServiceException.class, () -> service.attachment(1L, 20L, true));
        verifyNoInteractions(oss);
    }

    @Test
    void previewPreservesBytesAndCopiesDownloadHeaders() {
        MesProduct product = new MesProduct(); product.setAttachmentIds("10");
        when(mapper.selectById(1L)).thenReturn(product);
        var file = new org.dromara.system.domain.vo.SysOssVo();
        file.setOriginalName("drawing.png");
        when(oss.getById(10L)).thenReturn(file);
        byte[] bytes = {1, 2, 3};
        when(oss.download(10L)).thenReturn(org.springframework.http.ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.IMAGE_PNG).header("Content-Disposition", "attachment").body(bytes));
        var result = service.attachment(1L, 10L, true);
        assertArrayEquals(bytes, result.getBody());
        assertTrue(result.getHeaders().getContentDisposition().isInline());
        assertEquals("nosniff", result.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void validatesActualFileSignaturesAndSizeBeforeUploading() {
        assertThrows(ServiceException.class, () -> service.uploadAttachment(new MockMultipartFile("file", "test.exe", "image/png", new byte[]{1})));
        assertThrows(ServiceException.class, () -> service.uploadAttachment(new MockMultipartFile("file", "test.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8))));
        assertThrows(ServiceException.class, () -> service.uploadAttachment(new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])));
        verifyNoInteractions(oss);
    }

    @Test
    void duplicateRowsAndExistingCodesRejectTheEntireBatch() {
        var result = service.importRows(List.of(row("AbC", 2), row("abc", 4)), new ArrayList<>());
        assertEquals(0, result.importedCount());
        assertEquals(4, result.errors().getFirst().rowNumber());
        when(mapper.existingCodes(anyCollection())).thenReturn(List.of("AbC"));
        result = service.importRows(List.of(row("abc", 2)), new ArrayList<>());
        assertEquals(0, result.importedCount());
        assertFalse(result.errors().isEmpty());
        verify(mapper, never()).insert(any(MesProduct.class));
    }

    @Test
    void concurrentImportConflictRollsBackAndReportsZero() {
        when(mapper.insert(any(MesProduct.class))).thenReturn(1).thenThrow(new DuplicateKeyException("race"));
        var result = service.importRows(List.of(row("A", 2), row("B", 3)), new ArrayList<>());
        assertEquals(0, result.importedCount());
        assertFalse(result.errors().isEmpty());
        verify(tx).rollback(any());
        verify(tx, never()).commit(any());
    }

    @Test
    void validImportCommitsAndDoesNotOverwriteFiles() {
        var result = service.importRows(List.of(row("0001", 2)), new ArrayList<>());
        assertEquals(1, result.importedCount());
        assertTrue(result.errors().isEmpty());
        verify(tx).commit(any());
        verifyNoInteractions(oss);
    }

    private static MesProductBo product(String code, String name) {
        MesProductBo bo = new MesProductBo();
        bo.setProductCode(code); bo.setProductName(name);
        return bo;
    }

    private static ProductExcelRow row(String code, int number) {
        ProductExcelRow row = new ProductExcelRow();
        row.setProductCode(code); row.setProductName("支架"); row.setRowNumber(number);
        return row;
    }
}

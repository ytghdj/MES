package org.dromara.mes.domain.vo;

/** 附件元数据，不暴露对象存储访问地址。 */
public record ProductAttachmentVo(Long ossId, String fileName, Long fileSize, String contentType) {}

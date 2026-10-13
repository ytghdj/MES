/** 产品业务字段。编号均按字符串处理，保留前导零。 */
export interface ProductFields {
  productCode: string;
  productName: string;
  specification: string;
  materialGrade: string;
  drawingNo: string;
  customerPartNo: string;
}

export interface ProductAttachment {
  ossId: string | number;
  fileName: string;
  fileSize?: number;
  contentType: string;
}

export interface ProductForm extends ProductFields, BaseEntity {
  id?: string | number;
  attachmentIds: string;
  version?: number;
}

export interface ProductVO extends ProductForm {
  id: string | number;
  version: number;
  status: '0' | '1';
  attachmentCount: number;
  attachments?: ProductAttachment[];
  createTime?: string;
  updateTime?: string;
}

export interface ProductQuery extends PageQuery, Partial<ProductFields> {
  status?: string;
}

export interface ProductImportResult {
  importedCount: number;
  errors: Array<{ rowNumber: number; productCode: string; reason: string }>;
}

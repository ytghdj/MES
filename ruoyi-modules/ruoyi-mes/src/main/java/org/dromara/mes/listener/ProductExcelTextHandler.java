package org.dromara.mes.listener;

import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;

/** 空模板的未来输入单元格也必须采用文本格式。 */
public class ProductExcelTextHandler implements SheetWriteHandler {
    @Override
    public void afterSheetCreate(WriteWorkbookHolder workbook, WriteSheetHolder sheet) {
        var style = workbook.getWorkbook().createCellStyle();
        style.setDataFormat((short) 49);
        for (int column = 0; column < 6; column++) {
            sheet.getSheet().setDefaultColumnStyle(column, style);
        }
    }
}

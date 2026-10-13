import org.apache.fesod.sheet.FesodSheet;
import org.dromara.mes.domain.vo.ProductExcelRow;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

/** Generate synthetic acceptance workbooks in memory without reading protected Office files. */
class MesWorkbookFixtures {
    public static void main(String[] args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        switch (args[0]) {
            case "valid.xlsx" -> {
                ProductExcelRow row = row("000001"); row.setDrawingNo("00002"); row.setCustomerPartNo("00003");
                FesodSheet.write(bytes, ProductExcelRow.class).sheet().doWrite(List.of(row));
            }
            case "invalid.xlsx" -> FesodSheet.write(bytes, ProductExcelRow.class).sheet().doWrite(List.of(row("A"), row(" ")));
            case "wrong-header.xlsx" -> FesodSheet.write(bytes).head(List.of(List.of("错误列"))).sheet().doWrite(List.of(List.of("A")));
            case "over-limit.xlsx" -> FesodSheet.write(bytes, ProductExcelRow.class).sheet()
                .doWrite(IntStream.range(0, 5001).mapToObj(i -> row("P" + i)).toList());
            case "race.xlsx" -> FesodSheet.write(bytes, ProductExcelRow.class).sheet()
                .doWrite(IntStream.range(0, 1000).mapToObj(i -> row(i == 999 ? "RACE-IMPORT" : "RACE-BATCH-" + i)).toList());
            default -> throw new IllegalArgumentException("Unknown fixture");
        }
        System.out.println(Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    private static ProductExcelRow row(String code) {
        ProductExcelRow row = new ProductExcelRow(); row.setProductCode(code); row.setProductName("支架"); return row;
    }
}

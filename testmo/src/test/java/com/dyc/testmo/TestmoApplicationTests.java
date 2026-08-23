package com.dyc.testmo;

import com.dyc.excel.autoconfigure.EasyExcelProperties;
import com.dyc.excel.service.ExcelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TestmoApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    ExcelService excelService;

    @Test
    void testExcel() throws IOException {
        testExportToStream();
    }




    public void testExportToStream() throws IOException {
        List<UserData> testData = new ArrayList<>();
        testData.add(new UserData(1, "张三", "zhangsan@example.com", 25));
        testData.add(new UserData(2, "李四", "lisi@example.com", 30));
        testData.add(new UserData(3, "王五", "wangwu@example.com", 28));
        testData.add(new UserData(4, "赵六", "zhaoliu@example.com", 35));
        testData.add(new UserData(5, "孙七", "sunqi@example.com", 22));

        EasyExcelProperties properties = new EasyExcelProperties();
        ExcelService excelService = new ExcelService(properties);

        String fileName = "test_export.xlsx";
        try (OutputStream outputStream = new FileOutputStream(fileName)) {
            excelService.exportToStream(outputStream, fileName, testData, UserData.class);
        }

        File exportedFile = new File(fileName);
        assertTrue(exportedFile.exists(), "导出的文件应该存在");
        assertTrue(exportedFile.length() > 0, "导出的文件大小应该大于0");

        System.out.println("✓ Excel 文件导出成功！");
        System.out.println("  文件名: " + fileName);
        System.out.println("  文件大小: " + exportedFile.length() + " bytes");
        System.out.println("  数据量: " + testData.size() + " 条");
    }
}

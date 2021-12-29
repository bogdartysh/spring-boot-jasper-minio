package com.github.bogdartysh.jasper.minio;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SpringBootApplication
@Slf4j
@RestController
public class Application {

    public static final String JASPER_REPORTS_TEMPLATES_BUCKET_NAME = "jasperreportstemplates";
    public static final String REPORT_JRXML = "report.jrxml";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @SneakyThrows
    JasperReport getJasperReport() {
        var vri =
                StreamSupport.stream(minioClient().listObjects(ListObjectsArgs.builder()
                        .bucket(JASPER_REPORTS_TEMPLATES_BUCKET_NAME)
                        .build()).spliterator(), false)
                        .map(ri -> {
                            try {
                                return ri.get();
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(ri -> !Objects.isNull(ri))
                        .filter(Item::isDir)
                        .filter(ri -> StringUtils.isNotBlank(ri.objectName()))
                        .filter(ri -> ri.objectName().matches("[0-9]+/"))
                        .sorted((o1, o2) -> StringUtils.compare(o2.objectName(), o1.objectName()))
                        .collect(Collectors.toList());

        for (var r : vri) {
            try {

                var mo = minioClient()
                        .getObject(GetObjectArgs.builder()
                                .bucket(JASPER_REPORTS_TEMPLATES_BUCKET_NAME)
                                .object(r.objectName() + REPORT_JRXML)
                                .build());
                return JasperCompileManager.compileReport(mo);
            } catch (ErrorResponseException e) {
                log.warn("failed to get [{}] from [{}] due tro [{}]", REPORT_JRXML, r.objectName(), e.getMessage(), e);
            }
        }

        return null;
    }

    @Bean
    @SneakyThrows
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minioadmin", "minioadmin")
                .build();
    }

    @GetMapping(path = "/country/{code}/{name}", produces = MediaType.APPLICATION_PDF_VALUE)
    @SneakyThrows
    byte[] getPdf(@PathVariable String code, @PathVariable String name) {
        var dataSource = new JRBeanCollectionDataSource(Collections.singletonList(Country.builder().code(code).name(name).build()));
        var jasperPrint = JasperFillManager.fillReport(getJasperReport(), new HashMap<>(), dataSource);
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }


    @Builder
    @Data
    public static class Country {
        String code;
        String name;
    }
}

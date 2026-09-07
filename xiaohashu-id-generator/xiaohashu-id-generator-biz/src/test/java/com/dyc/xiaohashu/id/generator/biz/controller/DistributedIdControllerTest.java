package com.dyc.xiaohashu.id.generator.biz.controller;

import com.dyc.controller.DistributedIdController;
import com.dyc.exception.GlobalExceptionHandler;
import com.dyc.service.DistributedIdGenerateService;
import com.dyc.xiaohashu.id.generator.dto.resp.BatchGenerateIdRspDTO;
import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DistributedIdControllerTest {

    private DistributedIdGenerateService distributedIdGenerateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        distributedIdGenerateService = mock(DistributedIdGenerateService.class);
        DistributedIdController controller = new DistributedIdController();
        ReflectionTestUtils.setField(controller, "distributedIdGenerateService", distributedIdGenerateService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void nextSnowflakeIdShouldReturnLongId() throws Exception {
        when(distributedIdGenerateService.nextId(IdGeneratorType.SNOWFLAKE)).thenReturn(912345678L);

        mockMvc.perform(get("/id-generator/snowflake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(912345678L));
    }

    @Test
    void nextIdShouldRouteRequestType() throws Exception {
        when(distributedIdGenerateService.nextId(IdGeneratorType.SEGMENT)).thenReturn(2001L);

        mockMvc.perform(post("/id-generator/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SEGMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(2001L));
    }

    @Test
    void batchGenerateIdsShouldReturnIds() throws Exception {
        BatchGenerateIdRspDTO response = BatchGenerateIdRspDTO.builder()
                .type(IdGeneratorType.SEGMENT_CHAIN)
                .size(2)
                .ids(List.of(301L, 302L))
                .build();
        when(distributedIdGenerateService.batchGenerateIds(eq(IdGeneratorType.SEGMENT_CHAIN), eq(2))).thenReturn(response);

        mockMvc.perform(post("/id-generator/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SEGMENT_CHAIN\",\"size\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("SEGMENT_CHAIN"))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.ids[0]").value(301L))
                .andExpect(jsonPath("$.data.ids[1]").value(302L));
    }

    @Test
    void batchGenerateIdsShouldValidateRequest() throws Exception {
        mockMvc.perform(post("/id-generator/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SEGMENT_CHAIN\",\"size\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ID-GENERATOR-10001"));
    }
}

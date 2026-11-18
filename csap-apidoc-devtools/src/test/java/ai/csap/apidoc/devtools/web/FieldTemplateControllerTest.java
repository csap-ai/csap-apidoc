package ai.csap.apidoc.devtools.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ai.csap.apidoc.devtools.FieldTemplateController;
import ai.csap.apidoc.template.FieldTemplate;
import ai.csap.apidoc.template.FieldTemplateService;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

class FieldTemplateControllerTest {

    private FieldTemplateService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(FieldTemplateService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FieldTemplateController(service)).build();
    }

    private static FieldTemplate template(String name, String source) {
        FieldTemplate t = new FieldTemplate();
        t.setName(name);
        t.setTitle(name + "标题");
        t.setSource(source);
        return t;
    }

    @Test
    void listsBuiltinAndProjectTemplatesWithSource() throws Exception {
        when(service.projectSupported()).thenReturn(true);
        when(service.list()).thenReturn(List.of(template("email", "builtin"), template("order-no", "project")));
        mvc.perform(get("/csap/yaml/fieldTemplates"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("0"))
           .andExpect(jsonPath("$.data.projectSupported").value(true))
           .andExpect(jsonPath("$.data.templates[0].name").value("email"))
           .andExpect(jsonPath("$.data.templates[0].source").value("builtin"))
           .andExpect(jsonPath("$.data.templates[1].source").value("project"));
    }

    @Test
    void savesAndDeletes() throws Exception {
        mvc.perform(post("/csap/yaml/fieldTemplates").contentType(MediaType.APPLICATION_JSON)
                                                     .content("{\"name\":\"order-no\",\"title\":\"订单号\",\"example\":\"A1\"}"))
           .andExpect(jsonPath("$.data").value(true));
        verify(service).saveProject(any(FieldTemplate.class));
        when(service.deleteProject("order-no")).thenReturn(true);
        mvc.perform(delete("/csap/yaml/fieldTemplates/order-no")).andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void notWritableReturns1007() throws Exception {
        doThrow(new RuleStorageNotWritableException("只读")).when(service).saveProject(any(FieldTemplate.class));
        mvc.perform(post("/csap/yaml/fieldTemplates").contentType(MediaType.APPLICATION_JSON)
                                                     .content("{\"name\":\"a\",\"title\":\"A\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("1007"))
           .andExpect(jsonPath("$.success").value(false));
    }
}

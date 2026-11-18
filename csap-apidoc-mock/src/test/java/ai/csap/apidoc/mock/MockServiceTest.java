package ai.csap.apidoc.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.type.ModelType;

class MockServiceTest {

    static CsapDocMethod method(String key, String path, RequestMethod verb, String... fields) {
        CsapDocModel model = new CsapDocModel();
        model.setModelType(ModelType.OBJECT);
        model.setName("R");
        model.setParameters(new ArrayList<>());
        for (String f : fields) {
            CsapDocParameter p = new CsapDocParameter();
            p.setName(f);
            p.setDataType("Integer");
            model.getParameters().add(p);
        }
        CsapDocMethod m = new CsapDocMethod();
        m.setKey(key);
        m.setApiPath(new String[]{"/api"});
        m.setPaths(new String[]{path});
        m.setMethods(new ArrayList<>(Collections.singletonList(verb)));
        m.setResponse(fields.length == 0 ? null : new ArrayList<>(Collections.singletonList(model)));
        return m;
    }

    static MockService service(MockProperties props, CsapDocMethod... methods) {
        CsapDocModelController c = new CsapDocModelController();
        c.setMethodList(new ArrayList<>(Arrays.asList(methods)));
        CsapDocResponse doc = new CsapDocResponse();
        doc.getApiList().add(c);
        return new MockService(props, () -> doc);
    }

    @Test
    void specificPathBeatsPlaceholderAndPlaceholderMatchesOneSegment() {
        MockService s = service(new MockProperties(),
                method("a.byId", "/users/{id}", RequestMethod.GET, "x"),
                method("a.me", "/users/me", RequestMethod.GET, "y"));
        assertEquals("a.me", s.handle("GET", "/api/users/me", null).getHeaders().get("X-CSAP-Mock-Operation"));
        assertEquals("a.byId", s.handle("GET", "/api/users/42", null).getHeaders().get("X-CSAP-Mock-Operation"));
        assertEquals(404, s.handle("GET", "/api/users/42/extra", null).getStatus());
        assertEquals(404, s.handle("GET", "/api/users", null).getStatus());
    }

    @Test
    void wrongMethodIs405WithAllow() {
        MockService s = service(new MockProperties(), method("a.save", "/save", RequestMethod.POST, "x"));
        MockService.Reply r = s.handle("GET", "/api/save", null);
        assertEquals(405, r.getStatus());
        assertEquals("POST", r.getHeaders().get("Allow"));
        assertTrue(r.getBody().contains("MOCK_METHOD_NOT_ALLOWED"));
    }

    @Test
    void hiddenMethodsAreNotMocked() {
        CsapDocMethod m = method("a.h", "/h", RequestMethod.GET, "x");
        m.setHidden(true);
        assertEquals(404, service(new MockProperties(), m).handle("GET", "/api/h", null).getStatus());
    }

    @Test
    void noResponseModelGivesEmptyBody() {
        MockService.Reply r = service(new MockProperties(), method("a.n", "/n", RequestMethod.GET)).handle("GET", "/api/n", null);
        assertEquals(200, r.getStatus());
        assertNull(r.getBody());
    }

    @Test
    void seedIsReproducibleAndRandomIsNot() {
        MockProperties p = new MockProperties();
        MockService s = service(p, method("a.x", "/x", RequestMethod.GET, "n"));
        assertEquals(s.handle("GET", "/api/x", null).getBody(), s.handle("GET", "/api/x", null).getBody());
        assertEquals("abc", s.handle("GET", "/api/x", "abc").getHeaders().get("X-CSAP-Mock-Seed"));
        p.setSeed("random");
        assertNotEquals(s.handle("GET", "/api/x", null).getHeaders().get("X-CSAP-Mock-Seed"),
                s.handle("GET", "/api/x", null).getHeaders().get("X-CSAP-Mock-Seed"));
    }
}

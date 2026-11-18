package ai.csap.apidoc.mcp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.mcp.protocol.JsonRpc;

class RedactorTest {

    @Test
    void masksSensitiveFieldsRecursively() throws Exception {
        Redactor r = new Redactor(Collections.singletonList("phone"), 512);
        String s = r.summarize(JsonRpc.MAPPER.readTree("{\"name\":\"a\",\"password\":\"p1\",\"user\":{\"accessToken\":\"t\","
                + "\"api_key\":\"k\",\"Phone\":\"138\"},\"list\":[{\"client-secret\":\"x\",\"ok\":1}],\"sessionId\":null}"));
        assertEquals("{\"name\":\"a\",\"password\":\"***\",\"user\":{\"accessToken\":\"***\",\"api_key\":\"***\",\"Phone\":\"***\"},"
                + "\"list\":[{\"client-secret\":\"***\",\"ok\":1}],\"sessionId\":null}", s);
        assertFalse(s.contains("p1"));
    }

    @Test
    void truncates() {
        Redactor r = new Redactor(null, 40);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append('x');
        }
        String s = r.summarizeText(sb.toString());
        assertTrue(s.startsWith("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx…(100 chars)"), s);
        // 结果文本是 JSON 时同样打码
        assertEquals("{\"token\":\"***\"}", r.summarizeText("{\"token\":\"abc\"}"));
    }

    @Test
    void ringBufferAndSqlite() throws Exception {
        AuditLog mem = new AuditLog(2, null, 100);
        for (int i = 0; i < 3; i++) {
            AuditEntry e = new AuditEntry();
            e.setTool("t" + i);
            e.setCaller(i == 2 ? "b" : "a");
            mem.record(e);
        }
        List<AuditEntry> all = mem.query(10, null, null);
        assertEquals(2, all.size());
        assertEquals("t2", all.get(0).getTool());
        assertEquals(1, mem.query(10, null, "a").size());

        File dir = Files.createTempDirectory("mcp-audit").toFile();
        File db = new File(dir, "audit.db");
        AuditLog disk = new AuditLog(1, db, 100);
        assertTrue(disk.isPersistent());
        for (int i = 0; i < 3; i++) {
            AuditEntry e = new AuditEntry();
            e.setTool("t" + i);
            e.setArguments("{\"password\":\"***\"}");
            disk.record(e);
        }
        List<AuditEntry> rows = disk.query(10, null, null);
        assertEquals(3, rows.size());
        assertEquals("t2", rows.get(0).getTool());
        assertEquals(1, disk.query(10, "t1", null).size());
        disk.close();
        // 重启后编号继续
        AuditLog again = new AuditLog(1, db, 100);
        AuditEntry e = new AuditEntry();
        e.setTool("t3");
        again.record(e);
        assertEquals(4, e.getId());
        again.close();
    }
}

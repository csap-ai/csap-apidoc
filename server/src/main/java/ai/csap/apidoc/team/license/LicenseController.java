package ai.csap.apidoc.team.license;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;

@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    private final LicenseService license;
    private final Access access;
    private final AuditService audit;

    public LicenseController(LicenseService license, Access access, AuditService audit) {
        this.license = license;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public Map<String, Object> get() {
        access.admin();
        return license.current().toMap();
    }

    @PostMapping
    public Map<String, Object> install(@RequestBody Map<String, Object> body) {
        access.admin();
        LicenseService.Info info = license.install(body.get("license") instanceof String s ? s : null);
        audit.log("license.update").reason(info.status() + " " + info.licensee() + " 上限 " + info.maxMembers()).save();
        return info.toMap();
    }
}

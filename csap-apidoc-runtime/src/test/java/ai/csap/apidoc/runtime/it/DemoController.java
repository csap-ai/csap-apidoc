package ai.csap.apidoc.runtime.it;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.runtime.filter.model.ApiResult;
import ai.csap.apidoc.runtime.filter.model.MatchPage;
import ai.csap.apidoc.runtime.filter.model.Team;

@RestController
public class DemoController {

    @GetMapping("/page")
    public MatchPage page() {
        return new MatchPage();
    }

    @GetMapping("/result")
    public ApiResult<MatchPage> result() {
        return new ApiResult<>(new MatchPage());
    }

    /**
     * 由 {@link WrappingAdvice} 包进 ApiResult
     */
    @GetMapping("/wrapped")
    public MatchPage wrapped() {
        return new MatchPage();
    }

    @GetMapping("/csapMode")
    public MatchPage csapMode() {
        return new MatchPage();
    }

    @GetMapping("/offMode")
    public MatchPage offMode() {
        return new MatchPage();
    }

    @GetMapping("/list")
    public List<Team> list() {
        return Arrays.asList(new Team(1, "a"), new Team(2, "b"));
    }

    @GetMapping("/noRules")
    public MatchPage noRules() {
        return new MatchPage();
    }

    @GetMapping("/text")
    public String text() {
        return "plain";
    }
}

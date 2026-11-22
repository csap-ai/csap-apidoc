package ai.csap.apidoc.team.change;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService comments;
    private final Access access;

    public CommentController(CommentService comments, Access access) {
        this.comments = comments;
        this.access = access;
    }

    @GetMapping("/changes/{id}/comments")
    public List<Map<String, Object>> list(@PathVariable long id) {
        return comments.list(access.user(), id);
    }

    @PostMapping("/changes/{id}/comments")
    public Map<String, Object> create(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return comments.create(access.user(), id, body.get("body"), body.get("itemId"));
    }

    @PatchMapping("/comments/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return comments.update(access.user(), id, body.get("body"));
    }

    @DeleteMapping("/comments/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        return comments.delete(access.user(), id);
    }
}

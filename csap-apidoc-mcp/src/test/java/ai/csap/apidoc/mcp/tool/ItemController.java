package ai.csap.apidoc.mcp.tool;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/items")
public class ItemController {

    public static class Item {
        private String name;
        private Integer qty;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }
    }

    public static class ItemQuery {
        private String keyword;
        private Integer page;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }
    }

    @GetMapping("/{id}")
    public Item get(@PathVariable("id") Long id, @RequestHeader("X-Tenant") String tenant,
                    @RequestParam(required = false) String fields) {
        return new Item();
    }

    @GetMapping
    public List<Item> search(ItemQuery query) {
        return null;
    }

    @PostMapping
    public Item create(@RequestBody Item item) {
        return item;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
    }

    @PostMapping("/upload")
    public void upload(@RequestParam MultipartFile file) {
    }
}

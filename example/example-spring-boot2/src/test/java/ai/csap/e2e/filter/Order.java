package ai.csap.e2e.filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel("订单")
public class Order {
    @ApiModelProperty(value = "ID", forceRep = true)
    private Long id = 1L;
    @ApiModelProperty("订单号")
    private String no = "NO-1";
    @ApiModelProperty("金额")
    private BigDecimal amount = new BigDecimal("9.90");
    @ApiModelProperty("内部字段")
    private String secret = "s";
    @ApiModelProperty("客户")
    private Customer customer = new Customer();
    @ApiModelProperty("明细")
    private List<Item> items = new ArrayList<>(List.of(new Item("A", 1), new Item("B", 2)));
    @ApiModelProperty("按仓库")
    private Map<String, Item> stock = new LinkedHashMap<>(Map.of("w1", new Item("A", 5)));

    public Long getId() {
        return id;
    }

    public String getNo() {
        return no;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSecret() {
        return secret;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<Item> getItems() {
        return items;
    }

    public Map<String, Item> getStock() {
        return stock;
    }

    @ApiModel("客户")
    public static class Customer {
        @ApiModelProperty("ID")
        private Long id = 9L;
        @ApiModelProperty("姓名")
        private String name = "张三";
        @ApiModelProperty("手机")
        private String phone = "138";

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }
    }

    @ApiModel("明细")
    public static class Item {
        @ApiModelProperty("SKU")
        private String sku;
        @ApiModelProperty("数量")
        private int qty;
        @ApiModelProperty("成本")
        private int cost = 3;

        public Item() {
        }

        public Item(String sku, int qty) {
            this.sku = sku;
            this.qty = qty;
        }

        public String getSku() {
            return sku;
        }

        public int getQty() {
            return qty;
        }

        public int getCost() {
            return cost;
        }
    }
}

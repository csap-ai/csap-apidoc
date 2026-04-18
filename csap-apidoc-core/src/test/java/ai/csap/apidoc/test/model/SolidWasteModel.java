package ai.csap.apidoc.test.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Description;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author admin
 * @dateTime 2021-05-26 15:58:42
 */
@ApiModel(description = "固废信息", value = "solidWaste对象")
@Description(value = "固废信息Model")
@NoArgsConstructor
@Accessors(chain = true)
@Data
@Builder
@AllArgsConstructor
public class SolidWasteModel {
    @ApiModelProperty(description = "固废ID", groups = {
            @Group(response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "固废ID")
    private Long id;
    @ApiModelProperty(description = "创建时间", value = "创建时间")
    private LocalDateTime createTime;
    @ApiModelProperty(description = "修改时间", value = "修改时间")
    private LocalDateTime updateTime;
    @ApiModelProperty(description = "固废名称", groups = {
            @Group(request = @Request(paramType = ParamType.QUERY), response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "固废名称")
    private String name;
    @ApiModelProperty(description = "需求类型1产废2物流3处置", groups = {
            @Group(request = @Request(paramType = ParamType.QUERY), response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "需求类型1产废2物流3处置")
    private Integer needType;
    @ApiModelProperty(description = "危废代码", groups = {
            @Group(request = @Request(paramType = ParamType.QUERY), response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "危废代码")
    private String code;
    @ApiModelProperty(description = "危废代码名称", groups = {
            @Group(response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "危废代码名称")
    private String codeName;
    @ApiModelProperty(description = "企业名称", groups = {
            @Group(request = @Request(paramType = ParamType.QUERY), response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "企业名称")
    private String companyName;
    @ApiModelProperty(description = "用户ID", value = "用户ID")
    private Long userId;
    @ApiModelProperty(description = "行业ID", groups = {
            @Group(request = @Request(paramType = ParamType.QUERY), value = "queryManagerListPage")}, value = "行业ID")
    private Long industryId;
    @ApiModelProperty(description = "行业名称", groups = {
            @Group(response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "行业名称")
    private String industryName;
    @ApiModelProperty(description = "发布时间", groups = {
            @Group(response = @Response, value = "queryManagerListPage"),
            @Group(response = @Response, value = "home3")}, value = "发布时间")
    private LocalDateTime publishTime;
    @ApiModelProperty(description = "地址区域ID", value = "地址区域ID")
    private Long regionId;
    @ApiModelProperty(description = "详细地址", value = "详细地址")
    private String address;
    @ApiModelProperty(description = "详细信息", value = "详细信息")
    private String detailInfo;
    @ApiModelProperty(description = "状态1下架2发布3完成4取消", value = "状态1下架2发布3完成4取消")
    private Integer status;
    @ApiModelProperty(description = "当前处理状态，即接单状态", value = "当前处理状态，即接单状态")
    private Integer handleStatus;
    @ApiModelProperty(description = "重量/吨", value = "重量/吨")
    private BigDecimal weight;
    @ApiModelProperty(description = "封面图片", value = "封面图片")
    private String logo;
    @ApiModelProperty(description = "详情图片", value = "详情图片")
    private String images;

}

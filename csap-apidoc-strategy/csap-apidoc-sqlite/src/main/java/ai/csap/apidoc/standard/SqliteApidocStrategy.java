package ai.csap.apidoc.standard;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.annotation.ApiAuthorization;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocMethodHeaders;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.TypeVariableModel;

import lombok.SneakyThrows;

/**
 * SQLite-backed apidoc strategy. Reads from a SQLite DB matching the project's schema.
 */
public class SqliteApidocStrategy implements ApidocStrategy {

    @Override
    public String getName() {
        return ApiStrategyType.SQL_LITE.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.SQL_LITE.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    @Override
    public CsapDocModelController controller(StrategyModel strategyModel, String tableName) {
        return null;
    }

    @Override
    public CsapDocMethod method(CsapDocModelController docController, Method method,
                                TypeVariableModel typeVariableModel,
                                StrategyModel strategyModel) {
        return null;
    }

    @Override
    public Boolean write(StandardProperties standardProperties) {
        return null;
    }

    @SneakyThrows
    protected LoadedData load() {
        String url = System.getProperty("csap.apidoc.sqlite.url", "jdbc:sqlite::memory:");
        Map<String, CsapDocModelController> controllerMap = new HashMap<>();
        Map<String, Map<String, CsapDocMethod>> methodMap = new HashMap<>();
        Map<String, CsapDocModel> paramMap = new HashMap<>();
        CsapApiInfo apiInfo = new CsapApiInfo();

        try (Connection conn = DriverManager.getConnection(url)) {
            // api_info
            String infoSql = "SELECT title,description,version,license,license_url,service_url," +
                    "authorization_type,contact_name,contact_email,contact_url FROM api_info LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(infoSql);
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    apiInfo.setTitle(rs.getString(1));
                    apiInfo.setDescription(rs.getString(2));
                    apiInfo.setVersion(rs.getString(3));
                    apiInfo.setLicense(rs.getString(4));
                    apiInfo.setLicenseUrl(rs.getString(5));
                    apiInfo.setServiceUrl(rs.getString(6));
                    String auth = rs.getString(7);
                    if (auth != null) {
                        try {
                            apiInfo.setAuthorizationType(ApiAuthorization.valueOf(auth));
                        } catch (Exception ignore) {
                        }
                    }
                    CsapApiInfo.Contact contact = new CsapApiInfo.Contact();
                    contact.setName(rs.getString(8));
                    contact.setEmail(rs.getString(9));
                    contact.setUrl(rs.getString(10));
                    apiInfo.setContact(contact);
                }
            }

            // controllers
            String ctrlSql = "SELECT id,name,simple_name,title,description,position,hidden," +
                    "status,protocols FROM controller";
            try (PreparedStatement ps = conn.prepareStatement(ctrlSql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long controllerId = rs.getLong("id");
                    CsapDocModelController c = new CsapDocModelController();
                    c.setName(rs.getString("name"));
                    c.setSimpleName(rs.getString("simple_name"));
                    c.setValue(rs.getString("title"));
                    c.setDescription(rs.getString("description"));
                    c.setPosition(rs.getInt("position"));
                    c.setHidden(rs.getInt("hidden") == 1);
                    c.setProtocols(null);
                    // paths
                    List<String> paths = new ArrayList<>();
                    try (PreparedStatement ps2 = conn.prepareStatement("SELECT path FROM controller_path WHERE controller_id=?")) {
                        ps2.setLong(1, controllerId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                paths.add(rs2.getString(1));
                            }
                        }
                    }
                    c.setPath(paths.toArray(new String[0]));
                    // groups
                    Set<String> groups = new HashSet<>();
                    try (PreparedStatement ps2 = conn.prepareStatement(
                            "SELECT g.name FROM controller_group cg JOIN doc_group g " +
                                    "ON g.id=cg.group_id WHERE cg.controller_id=?")) {
                        ps2.setLong(1, controllerId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                groups.add(rs2.getString(1));
                            }
                        }
                    }
                    c.setGroup(groups);
                    // versions
                    Set<String> versions = new HashSet<>();
                    try (PreparedStatement ps2 = conn.prepareStatement(
                            "SELECT v.name FROM controller_version cv JOIN doc_version v " +
                                    "ON v.id=cv.version_id WHERE cv.controller_id=?")) {
                        ps2.setLong(1, controllerId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                versions.add(rs2.getString(1));
                            }
                        }
                    }
                    c.setVersion(versions);
                    // tags
                    List<String> tags = new ArrayList<>();
                    try (PreparedStatement ps2 = conn.prepareStatement("SELECT tag FROM controller_tag WHERE controller_id=?")) {
                        ps2.setLong(1, controllerId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                tags.add(rs2.getString(1));
                            }
                        }
                    }
                    c.setTags(tags.toArray(new String[0]));
                    c.setMethodList(new ArrayList<>());
                    controllerMap.put(c.getName(), c);
                    methodMap.put(c.getName(), new HashMap<>());

                    // methods
                    String methodSql = "SELECT id,method_key,name,title,description,status,hidden," +
                            "param_type FROM api_method WHERE controller_id=?";
                    try (PreparedStatement ps2 = conn.prepareStatement(methodSql)) {
                        ps2.setLong(1, controllerId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                long methodId = rs2.getLong("id");
                                CsapDocMethod m = new CsapDocMethod();
                                m.setKey(rs2.getString("method_key"));
                                m.setName(rs2.getString("name"));
                                m.setValue(rs2.getString("title"));
                                m.setDescription(rs2.getString("description"));
                                m.setHidden(rs2.getInt("hidden") == 1);
                                m.setClassName(c.getName());
                                m.setSimpleName(c.getSimpleName());
                                String paramType = rs2.getString("param_type");
                                if (paramType != null) {
                                    try {
                                        m.setParamType(ParamType.valueOf(paramType));
                                    } catch (Exception ignore) {
                                    }
                                }
                                // http methods
                                List<RequestMethod> httpMethods = new ArrayList<>();
                                try (PreparedStatement ps3 = conn.prepareStatement("SELECT http_method FROM api_method_http WHERE method_id=?")) {
                                    ps3.setLong(1, methodId);
                                    try (ResultSet rs3 = ps3.executeQuery()) {
                                        while (rs3.next()) {
                                            String hm = rs3.getString(1);
                                            try {
                                                httpMethods.add(RequestMethod.valueOf(hm));
                                            } catch (Exception ignore) {
                                            }
                                        }
                                    }
                                }
                                m.setMethods(httpMethods);
                                // apiPath
                                List<String> apiPaths = new ArrayList<>();
                                try (PreparedStatement ps3 = conn.prepareStatement("SELECT api_path FROM api_method_api_path WHERE method_id=?")) {
                                    ps3.setLong(1, methodId);
                                    try (ResultSet rs3 = ps3.executeQuery()) {
                                        while (rs3.next()) {
                                            apiPaths.add(rs3.getString(1));
                                        }
                                    }
                                }
                                m.setApiPath(apiPaths.toArray(new String[0]));
                                // paths
                                List<String> methodPaths = new ArrayList<>();
                                try (PreparedStatement ps3 = conn.prepareStatement("SELECT path FROM api_method_path WHERE method_id=?")) {
                                    ps3.setLong(1, methodId);
                                    try (ResultSet rs3 = ps3.executeQuery()) {
                                        while (rs3.next()) {
                                            methodPaths.add(rs3.getString(1));
                                        }
                                    }
                                }
                                m.setPaths(methodPaths.toArray(new String[0]));
                                // headers
                                List<CsapDocMethodHeaders> headers = new ArrayList<>();
                                String headerSql = "SELECT header_key,header_value,description,required," +
                                        "example,position,hidden FROM api_method_header " +
                                        "WHERE method_id=? ORDER BY position";
                                try (PreparedStatement ps3 = conn.prepareStatement(headerSql)) {
                                    ps3.setLong(1, methodId);
                                    try (ResultSet rs3 = ps3.executeQuery()) {
                                        while (rs3.next()) {
                                            headers.add(CsapDocMethodHeaders.builder()
                                                    .key(rs3.getString(1))
                                                    .value(rs3.getString(2))
                                                    .description(rs3.getString(3))
                                                    .required(rs3.getInt(4) == 1)
                                                    .example(rs3.getString(5))
                                                    .position(rs3.getInt(6))
                                                    .hidden(rs3.getInt(7) == 1)
                                                    .build());
                                        }
                                    }
                                }
                                m.setMethodHeaders(headers);

                                // request/response models
                                m.setRequest(loadModels(conn, methodId, "REQ", paramMap));
                                m.setResponse(loadModels(conn, methodId, "RESP", paramMap));

                                controllerMap.get(c.getName()).getMethodList().add(m);
                                methodMap.get(c.getName()).put(m.getName(), m);
                            }
                        }
                    }
                }
            }
        }

        LoadedData data = new LoadedData();
        data.controllerMap = controllerMap;
        data.methodMap = methodMap;
        data.paramMap = paramMap;
        data.apiInfo = apiInfo;
        return data;
    }

    @Override
    public CsapDocResponse apidoc(String tableName, Boolean isParent, StrategyModel strategyModel, CsapDocResponse csapDocResponse) {
        LoadedData props = load();
        csapDocResponse
                .setApiInfo(props.apiInfo)
                .setApiList(new ArrayList<>(props.controllerMap.values()));
        csapDocResponse.sortApi();
        return csapDocResponse;
    }

    @SneakyThrows
    private List<CsapDocModel> loadModels(Connection conn, long methodId, String ioType,
                                          Map<String, CsapDocModel> paramMap) {
        List<CsapDocModel> models = new ArrayList<>();
        String modelSql = "SELECT id,name,title,description,model_type,FORCE,GLOBAL," +
                "method_param_name FROM api_model WHERE method_id=? AND io_type=?";
        try (PreparedStatement ps = conn.prepareStatement(modelSql)) {
            ps.setLong(1, methodId);
            ps.setString(2, ioType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long modelId = rs.getLong("id");
                    CsapDocModel model = new CsapDocModel();
                    model.setName(rs.getString("name"));
                    model.setValue(rs.getString("title"));
                    model.setDescription(rs.getString("description"));
                    model.setModelType(mapModelType(rs.getString("model_type")));
                    model.setForce(rs.getInt("force") == 1);
                    model.setGlobal(rs.getInt("global") == 1);
                    model.setMethodParamName(rs.getString("method_param_name"));
                    // groups/versions ignored for brevity; can be added similarly
                    // parameters tree
                    List<CsapDocParameter> params = loadParams(conn, modelId, null);
                    model.setParameters(params);
                    models.add(model);
                    paramMap.put(model.getName(), model);
                }
            }
        }
        return models;
    }

    @SneakyThrows
    private List<CsapDocParameter> loadParams(Connection conn, long modelId, Long parentId) {
        List<CsapDocParameter> list = new ArrayList<>();
        String fields = "id,name,key,key_name,value,description,example,data_type,long_data_type," +
                "model_type,param_type,required,hidden,force,default_value,position,decimals,length";
        String sql = parentId == null ?
                "select " + fields + " from api_param where model_id=? and parent_id is null order by position" :
                "select " + fields + " from api_param where model_id=? and parent_id=? order by position";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, modelId);
            if (parentId != null) {
                ps.setLong(2, parentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    CsapDocParameter p = new CsapDocParameter();
                    p.setName(rs.getString("name"));
                    p.setKey(rs.getString("key"));
                    p.setKeyName(rs.getString("key_name"));
                    p.setValue(rs.getString("value"));
                    p.setDescription(rs.getString("description"));
                    p.setExample(rs.getString("example"));
                    p.setDataType(rs.getString("data_type"));
                    p.setLongDataType(rs.getString("long_data_type"));
                    p.setModelType(mapModelType(rs.getString("model_type")));
                    String pt = rs.getString("param_type");
                    if (pt != null) {
                        try {
                            p.setParamType(ParamType.valueOf(pt));
                        } catch (Exception ignore) {
                        }
                    }
                    p.setRequired(rs.getInt("required") == 1);
                    p.setHidden(rs.getInt("hidden") == 1);
                    p.setForce(rs.getInt("force") == 1);
                    p.setDefaultValue(rs.getString("default_value"));
                    p.setPosition(rs.getInt("position"));
                    p.setDecimals(rs.getInt("decimals"));
                    p.setLength(rs.getInt("length"));

                    List<CsapDocParameter> children = loadParams(conn, modelId, id);
                    if (!children.isEmpty()) {
                        CsapDocModel childModel = CsapDocModel.builder()
                                .modelType(ModelType.OBJECT)
                                .parameters(children)
                                .build();
                        p.setChildren(childModel);
                    }
                    list.add(p);
                }
            }
        }
        return list;
    }

    private ModelType mapModelType(String mt) {
        if (mt == null) {
            return ModelType.BASE_DATA;
        }
        try {
            return ModelType.valueOf(mt);
        } catch (Exception e) {
            return ModelType.BASE_DATA;
        }
    }

    protected static class LoadedData {
        Map<String, CsapDocModelController> controllerMap;
        Map<String, Map<String, CsapDocMethod>> methodMap;
        Map<String, CsapDocModel> paramMap;
        CsapApiInfo apiInfo;
    }
}

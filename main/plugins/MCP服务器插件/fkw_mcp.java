import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.lang.Runnable;
import java.lang.Thread;
import java.net.ServerSocket;

// ===== mcp_utils.java =====
Map MCP_STATE = new LinkedHashMap();

void initMcpState() {
    MCP_STATE.put("serverSocket", null);
    MCP_STATE.put("running", Boolean.FALSE);
    MCP_STATE.put("config", null);
    MCP_STATE.put("threads", new ArrayList());
    MCP_STATE.put("logPath", pluginPath + "/logs/mcp-server.log");
}

String getSafeMyWxId() {
    try {
        return String.valueOf(getMyWxId());
    } catch (Exception e) {
        return "getMyWxId failed: " + e.getMessage();
    }
}

String readUtf8File(String path) {
    try {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        reader.close();
        return builder.toString();
    } catch (Exception e) {
        log("[FkwMcpPlugin] readUtf8File failed: " + e.getMessage());
        return null;
    }
}

void ensureParentDir(File file) {
    try {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    } catch (Exception e) {
        log("[FkwMcpPlugin] ensureParentDir failed: " + e.getMessage());
    }
}

void writeUtf8File(String path, String text) {
    try {
        File file = new File(path);
        ensureParentDir(file);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        writer.write(text == null ? "" : text);
        writer.flush();
        writer.close();
    } catch (Exception e) {
        log("[FkwMcpPlugin] writeUtf8File failed: " + e.getMessage());
    }
}

void appendUtf8File(String path, String text) {
    try {
        File file = new File(path);
        ensureParentDir(file);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
        writer.write(text == null ? "" : text);
        writer.flush();
        writer.close();
    } catch (Exception e) {
        log("[FkwMcpPlugin] appendUtf8File failed: " + e.getMessage());
    }
}

void appendLog(String text) {
    String safeText = text == null ? "null" : text;
    try {
        String logPath = String.valueOf(MCP_STATE.get("logPath"));
        String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        appendUtf8File(logPath, "[" + time + "] " + safeText + "\n");
    } catch (Exception e) {
        try {
            log("[FkwMcpPlugin] appendLog file failed: " + e.getMessage());
        } catch (Exception ignore1) {
        }
    }
    try {
        log(safeText);
    } catch (Exception ignore2) {
    }
}

String shortText(String text, int limit) {
    if (text == null) return "null";
    if (limit <= 0) return "";
    if (text.length() <= limit) return text;
    return text.substring(0, limit) + "...(" + text.length() + ")";
}

String previewValue(Object value, int limit) {
    String text = stringify(value);
    if (limit <= 0) return text;
    return shortText(text, limit);
}

String fullValue(Object value) {
    return stringify(value);
}

String stackTraceText(Exception e) {
    if (e == null) return "null";
    try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    } catch (Exception ignore) {
        return e.toString();
    }
}

String safeSocketAddress(Socket socket) {
    try {
        if (socket == null) return "null";
        return String.valueOf(socket.getRemoteSocketAddress());
    } catch (Exception e) {
        return "unknown";
    }
}

Map newMap() {
    return new LinkedHashMap();
}

List newList() {
    return new ArrayList();
}

String jsonEscape(String text) {
    if (text == null) return "";
    return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
}

String stringify(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return "\"" + jsonEscape((String) value) + "\"";
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    if (value instanceof Map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Object entryObj : ((Map) value).entrySet()) {
            Map.Entry entry = (Map.Entry) entryObj;
            if (!first) sb.append(",");
            first = false;
            sb.append(stringify(String.valueOf(entry.getKey())));
            sb.append(":");
            sb.append(stringify(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }
    if (value instanceof List) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List list = (List) value;
        int i = 0;
        while (i < list.size()) {
            if (i > 0) sb.append(",");
            sb.append(stringify(list.get(i)));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }
    return stringify(String.valueOf(value));
}

Object parseJson(String text) {
    if (text == null) return null;
    String trim = text.trim();
    if (trim.equals("")) return null;
    if (trim.startsWith("{")) {
        return jsonObjectToMap(new JSONObject(trim));
    }
    if (trim.startsWith("[")) {
        return jsonArrayToList(new JSONArray(trim));
    }
    return trim;
}

Map jsonObjectToMap(JSONObject object) {
    Map map = new LinkedHashMap();
    java.util.Iterator keys = object.keys();
    while (keys.hasNext()) {
        String key = String.valueOf(keys.next());
        Object value = object.opt(key);
        map.put(key, jsonValueToJava(value));
    }
    return map;
}

List jsonArrayToList(JSONArray array) {
    List list = new ArrayList();
    int i = 0;
    while (i < array.length()) {
        list.add(jsonValueToJava(array.opt(i)));
        i = i + 1;
    }
    return list;
}

Object jsonValueToJava(Object value) {
    if (value == null || value == JSONObject.NULL) return null;
    if (value instanceof JSONObject) return jsonObjectToMap((JSONObject) value);
    if (value instanceof JSONArray) return jsonArrayToList((JSONArray) value);
    return value;
}

Map successResponse(Object id, Object result) {
    Map resp = new LinkedHashMap();
    resp.put("jsonrpc", "2.0");
    resp.put("id", id);
    resp.put("result", result);
    return resp;
}

Map errorResponse(Object id, int code, String message) {
    Map error = new LinkedHashMap();
    error.put("code", code);
    error.put("message", message);
    Map resp = new LinkedHashMap();
    resp.put("jsonrpc", "2.0");
    resp.put("id", id);
    resp.put("error", error);
    return resp;
}

String readHttpBody(BufferedReader reader, int len) {
    char[] chars = new char[len];
    int read = 0;
    while (read < len) {
        int r = reader.read(chars, read, len - read);
        if (r < 0) break;
        read += r;
    }
    return new String(chars, 0, read);
}

String readHttpLine(InputStream input) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int b = -1;
    while ((b = input.read()) >= 0) {
        if (b == 10) break;
        if (b != 13) buffer.write(b);
    }
    if (b < 0 && buffer.size() == 0) return null;
    return new String(buffer.toByteArray(), "ISO-8859-1");
}

String readHttpBody(InputStream input, int len) throws Exception {
    if (len <= 0) return "";
    byte[] bytes = new byte[len];
    int read = 0;
    while (read < len) {
        int r = input.read(bytes, read, len - read);
        if (r < 0) break;
        read += r;
    }
    appendLog("[FkwMcpPlugin] body bytes read=" + read + "/" + len);
    return new String(bytes, 0, read, "UTF-8");
}

String urlDecode(String text) {
    try {
        return URLDecoder.decode(text, "UTF-8");
    } catch (Exception e) {
        return text;
    }
}

// ===== mcp_config.java =====
Map loadMcpConfig() {
    String configPath = pluginPath + "/mcp-config.json";
    appendLog("[FkwMcpPlugin] 读取配置文件: " + configPath);
    String text = readUtf8File(configPath);
    if (text == null || text.trim().equals("")) {
        appendLog("[FkwMcpPlugin] 配置文件不存在或为空，使用默认配置");
        Map defaults = newMap();
        defaults.put("host", "0.0.0.0");
        defaults.put("port", Long.valueOf(8888));
        defaults.put("path", "/mcp");
        Map servers = newMap();
        Map fkw = newMap();
        fkw.put("url", "http://localhost:8888/mcp");
        fkw.put("transport", "streamable-http");
        servers.put("Fkw-Mcp", fkw);
        defaults.put("mcpServers", servers);
        appendLog("[FkwMcpPlugin] 默认配置: " + stringify(defaults));
        return defaults;
    }
    appendLog("[FkwMcpPlugin] 配置文件内容: " + text);
    Object parsed = parseJson(text);
    if (!(parsed instanceof Map)) {
        throw new RuntimeException("配置文件根节点必须为JSON对象");
    }
    appendLog("[FkwMcpPlugin] 配置解析成功");
    return (Map) parsed;
}

long getServerPort() {
    Map config = (Map) MCP_STATE.get("config");
    Object port = config.get("port");
    if (port instanceof Number) return ((Number) port).longValue();
    return 8888;
}

String getServerPath() {
    Map config = (Map) MCP_STATE.get("config");
    Object path = config.get("path");
    return path == null ? "/mcp" : String.valueOf(path);
}

// ===== mcp_tools.java =====
String getMyNicknameSafe() {
    Map info = getMyUserInfo();
    if (info == null) return "";
    Object nickname = info.get("nickname");
    if (nickname == null) nickname = info.get("nickName");
    if (nickname == null) nickname = info.get("name");
    if (nickname == null) nickname = info.get("last_login_nick_name");
    if (nickname == null) nickname = info.get("login_user_name");
    return nickname == null ? "" : String.valueOf(nickname);
}

String nonEmptyString(Object value) {
    if (value == null) return "";
    return String.valueOf(value).trim();
}

Object requireArg(Map args, String key) {
    Object value = args.get(key);
    if (value == null) throw new RuntimeException("参数 `" + key + "` 不能为空");
    if (value instanceof String && nonEmptyString(value).equals("")) {
        throw new RuntimeException("参数 `" + key + "` 不能为空");
    }
    return value;
}

long getLongArg(Map args, String key) {
    Object value = requireArg(args, key);
    if (!(value instanceof Number)) {
        throw new RuntimeException("参数 `" + key + "` 必须为数字");
    }
    return ((Number) value).longValue();
}

int getIntArgOrDefault(Map args, String key, int def) {
    Object value = args.get(key);
    if (value == null) return def;
    if (!(value instanceof Number)) {
        throw new RuntimeException("参数 `" + key + "` 必须为数字");
    }
    int n = ((Number) value).intValue();
    if (n < 1) return def;
    return n;
}

boolean getBooleanArgOrDefault(Map args, String key, boolean def) {
    Object value = args.get(key);
    if (value == null) return def;
    if (value instanceof Boolean) return ((Boolean) value).booleanValue();
    return "true".equalsIgnoreCase(String.valueOf(value));
}

Object handleToolCall(String name, Map args) {
    if (name.equals("send_app_msg")) return toolSendAppMsg(args);
    if (name.equals("get_my_user_info")) return toolGetMyUserInfo();
    if (name.equals("get_friend_list")) return toolGetFriendList();
    if (name.equals("get_group_list")) return toolGetGroupList();
    if (name.equals("get_group_members")) return toolGetGroupMembers(args);
    if (name.equals("get_group_notice")) return toolGetGroupNotice(args);
    if (name.equals("get_messages")) return toolGetMessages(args);
    if (name.equals("get_msg_count")) return toolGetMsgCount(args);
    if (name.equals("get_app_info")) return toolGetAppInfo();
    if (name.equals("show_toast")) return toolShowToast(args);
    throw new RuntimeException("未知工具: " + name);
}

Map toolGetMyWxid() {
    Map result = newMap();
    result.put("wxid", getMyWxId());
    return result;
}

Map toolGetMyNickname() {
    Map result = newMap();
    result.put("nickname", getMyNicknameSafe());
    return result;
}

Map toolGetMyUserInfo() {
    Map info = getMyUserInfo();
    return info == null ? newMap() : info;
}

Map toolSendAppMsg(Map args) {
    String wxid = String.valueOf(requireArg(args, "wxid"));
    String content = String.valueOf(requireArg(args, "content"));
    boolean async = getBooleanArgOrDefault(args, "async", false);
    if (async) {
        return toolSendAppMsgAsync(wxid, content);
    }

    long startAt = System.currentTimeMillis();
    appendLog("[FkwMcpPlugin] send_app_msg start wxid=" + wxid + " messageType=text contentLen=" + content.length());
    sendText(wxid, content);
    appendLog("[FkwMcpPlugin] send_app_msg ok elapsedMs=" + (System.currentTimeMillis() - startAt));
    Map result = newMap();
    result.put("success", Boolean.TRUE);
    result.put("wxid", wxid);
    result.put("messageType", "text");
    result.put("contentLength", Long.valueOf(content.length()));
    return result;
}

Map toolSendAppMsgAsync(String wxid, String content) {
    long jobId = System.currentTimeMillis();
    final String jobWxid = wxid;
    final String jobContent = content;
    final long holderJobId = jobId;
    Thread t = new Thread(new Runnable() {
        public void run() {
            long startAt = System.currentTimeMillis();
            appendLog("[FkwMcpPlugin] async send_app_msg start jobId=" + holderJobId + " wxid=" + jobWxid + " messageType=text contentLen=" + jobContent.length());
            try {
                sendText(jobWxid, jobContent);
                appendLog("[FkwMcpPlugin] async send_app_msg ok jobId=" + holderJobId + " elapsedMs=" + (System.currentTimeMillis() - startAt));
            } catch (Exception e) {
                appendLog("[FkwMcpPlugin] async send_app_msg fail jobId=" + holderJobId + " " + shortText(stackTraceText(e), 500));
            }
        }
    });
    t.setName("FkwMcpSend-" + jobId);
    t.start();
    ((List) MCP_STATE.get("threads")).add(t);

    Map result = newMap();
    result.put("accepted", Boolean.TRUE);
    result.put("jobId", Long.valueOf(jobId));
    result.put("wxid", wxid);
    result.put("messageType", "text");
    result.put("contentLength", Long.valueOf(content.length()));
    result.put("content", content);
    return result;
}

List toolGetFriendList() {
    List source = getFriendList();
    return source == null ? newList() : source;
}

List toolGetGroupList() {
    List source = getGroupList();
    return source == null ? newList() : source;
}

List toolGetGroupMembers(Map args) {
    String chatroomId = String.valueOf(requireArg(args, "chatroom_id"));
    List members = getGroupMemberList(chatroomId);
    return members == null ? newList() : members;
}

Map toolGetGroupNotice(Map args) {
    String chatroomId = String.valueOf(requireArg(args, "chatroom_id"));
    Map result = newMap();
    result.put("chatroom_id", chatroomId);
    result.put("notice", getGroupNotice(chatroomId));
    return result;
}

List toolGetMessages(Map args) {
    String talker = String.valueOf(requireArg(args, "talker"));
    Object start = args.get("start_time");
    Object limitValue = args.get("limit");
    int limit = 50;
    if (limitValue instanceof Number) limit = ((Number) limitValue).intValue();
    if (limit < 1) limit = 50;
    if (limit > 300) limit = 300;
    List source = null;
    if (start instanceof Number) source = getMsgs(talker, ((Number) start).longValue());
    else source = getMsgs(talker, 0L);
    List filtered = newList();
    int size = source == null ? 0 : source.size();
    int i = 0;
    while (i < size) {
        Object item = source.get(i);
        if (start instanceof Number && item instanceof Map) {
            Map row = (Map) item;
            Object timeValue = row.get("createTime");
            if (timeValue == null) timeValue = row.get("time");
            if (timeValue instanceof Number) {
                long startTime = ((Number) start).longValue();
                if (((Number) timeValue).longValue() >= startTime) {
                    filtered.add(item);
                }
            } else {
                filtered.add(item);
            }
        } else {
            filtered.add(item);
        }
        i = i + 1;
    }
    if (filtered.size() <= limit) return filtered;
    List limited = newList();
    int from = filtered.size() - limit;
    int j = from;
    while (j < filtered.size()) {
        limited.add(filtered.get(j));
        j = j + 1;
    }
    appendLog("[FkwMcpPlugin] get_messages limited talker=" + talker + " sourceSize=" + filtered.size() + " returnSize=" + limited.size() + " limit=" + limit);
    return limited;
}

Map toolGetMsgCount(Map args) {
    String talker = String.valueOf(requireArg(args, "talker"));
    Map result = newMap();
    result.put("talker", talker);
    result.put("count", Long.valueOf(getMsgCount(talker)));
    return result;
}

Map toolGetAppInfo() {
    Map app = newMap();
    app.put("pluginName", pluginName);
    app.put("pluginVersion", pluginVersion);
    app.put("pluginAuthor", pluginAuthor);
    app.put("hostVerCode", Long.valueOf(hostVerCode));
    app.put("hostVerName", hostVerName);
    app.put("myWxId", getMyWxId());
    app.put("myNickname", getMyNicknameSafe());
    return app;
}

Map toolShowToast(Map args) {
    String text = String.valueOf(requireArg(args, "text"));
    long jobId = System.currentTimeMillis();
    final String jobText = text;
    final long holderJobId = jobId;
    Thread t = new Thread(new Runnable() {
        public void run() {
            long startAt = System.currentTimeMillis();
            appendLog("[FkwMcpPlugin] async show_toast start jobId=" + holderJobId + " textLen=" + jobText.length());
            try {
                toast(jobText);
                appendLog("[FkwMcpPlugin] async show_toast ok jobId=" + holderJobId + " elapsedMs=" + (System.currentTimeMillis() - startAt));
            } catch (Exception e) {
                appendLog("[FkwMcpPlugin] async show_toast fail jobId=" + holderJobId + " " + shortText(stackTraceText(e), 500));
            }
        }
    });
    t.setName("FkwMcpToast-" + jobId);
    t.start();
    ((List) MCP_STATE.get("threads")).add(t);
    Map result = newMap();
    result.put("accepted", Boolean.TRUE);
    result.put("jobId", Long.valueOf(jobId));
    result.put("text", text);
    return result;
}

List normalizeContactList(List source) {
    List result = newList();
    int size = source == null ? 0 : source.size();
    int i = 0;
    while (i < size) {
        Object item = source.get(i);
        if (item instanceof Map) result.add(normalizeContact((Map) item));
        i = i + 1;
    }
    return result;
}

Map normalizeContact(Map source) {
    Map row = newMap();
    String username = firstNonEmpty(source.get("username"), source.get("wxid"), source.get("talker"));
    String nickname = firstNonEmpty(source.get("nickname"), source.get("nickName"), source.get("name"));
    String remark = firstNonEmpty(source.get("conRemark"), source.get("remark"), source.get("displayName"));
    String alias = firstNonEmpty(source.get("alias"), source.get("wxAlias"));
    row.put("wxid", username);
    row.put("username", username);
    row.put("nickname", nickname);
    row.put("remark", remark);
    row.put("alias", alias);
    row.put("raw", source);
    return row;
}

Map normalizeGroup(Map source) {
    Map row = newMap();
    String chatroomId = firstNonEmpty(source.get("chatroom_id"), source.get("username"), source.get("wxid"), source.get("talker"));
    String name = firstNonEmpty(source.get("nickname"), source.get("nickName"), source.get("name"), source.get("remark"));
    row.put("chatroom_id", chatroomId);
    row.put("name", name);
    row.put("raw", source);
    return row;
}

List normalizeMessageList(List source) {
    List result = newList();
    int size = source == null ? 0 : source.size();
    int i = 0;
    while (i < size) {
        Object item = source.get(i);
        if (item instanceof Map) result.add(normalizeMessage((Map) item));
        i = i + 1;
    }
    return result;
}

Map normalizeMessage(Map source) {
    Map row = newMap();
    row.put("msgId", valueOrDefault(source.get("msgId"), source.get("msgid"), source.get("id")));
    row.put("talker", valueOrDefault(source.get("talker"), source.get("username")));
    row.put("content", valueOrDefault(source.get("content"), source.get("msg")));
    row.put("type", valueOrDefault(source.get("type"), source.get("msgType")));
    row.put("createTime", valueOrDefault(source.get("createTime"), source.get("time")));
    row.put("isSend", valueOrDefault(source.get("isSend"), source.get("fromSelf")));
    row.put("raw", source);
    return row;
}

String safeGroupMemberName(String chatroomId, String wxid) {
    try {
        String name = getUserName(chatroomId, wxid);
        if (!nonEmptyString(name).equals("")) return name;
    } catch (Exception ignore1) {
    }
    try {
        String name = getUserName(wxid);
        if (!nonEmptyString(name).equals("")) return name;
    } catch (Exception ignore2) {
    }
    return wxid;
}

String firstNonEmpty(Object a, Object b) {
    String sa = nonEmptyString(a);
    if (!sa.equals("")) return sa;
    return nonEmptyString(b);
}

String firstNonEmpty(Object a, Object b, Object c) {
    String s = firstNonEmpty(a, b);
    if (!s.equals("")) return s;
    return nonEmptyString(c);
}

String firstNonEmpty(Object a, Object b, Object c, Object d) {
    String s = firstNonEmpty(a, b, c);
    if (!s.equals("")) return s;
    return nonEmptyString(d);
}

Object valueOrDefault(Object a, Object b) {
    return a != null ? a : b;
}

Object valueOrDefault(Object a, Object b, Object c) {
    return a != null ? a : (b != null ? b : c);
}

Object valueOrDefault(Object a, Object b, Object c, Object d) {
    return a != null ? a : (b != null ? b : (c != null ? c : d));
}

List describeTools() {
    List tools = newList();
    List fields = null;

    fields = newList();
    fields.add(field("wxid", "string", true, "目标会话ID"));
    fields.add(field("content", "string", true, "要发送的普通文本内容"));
    fields.add(field("async", "boolean", false, "是否后台发送，默认 false"));
    tools.add(toolItem("send_app_msg", "发送普通文本消息", fields));

    tools.add(toolItem("get_my_user_info", "获取当前用户完整信息", newList()));
    tools.add(toolItem("get_friend_list", "获取好友列表", newList()));
    tools.add(toolItem("get_group_list", "获取群聊列表", newList()));

    fields = newList();
    fields.add(field("chatroom_id", "string", true, "群聊ID"));
    tools.add(toolItem("get_group_members", "获取指定群聊成员列表", fields));

    fields = newList();
    fields.add(field("chatroom_id", "string", true, "群聊ID"));
    tools.add(toolItem("get_group_notice", "获取指定群聊公告", fields));

    fields = newList();
    fields.add(field("talker", "string", true, "会话ID"));
    fields.add(field("start_time", "integer", false, "可选开始时间戳，毫秒"));
    fields.add(field("limit", "integer", false, "最多返回条数，默认50，最大300；返回项保持宿主原始消息结构"));
    tools.add(toolItem("get_messages", "获取指定对话消息列表，默认返回最近50条原始消息", fields));

    fields = newList();
    fields.add(field("talker", "string", true, "会话ID"));
    tools.add(toolItem("get_msg_count", "获取指定对话消息总数", fields));

    tools.add(toolItem("get_app_info", "获取当前应用信息", newList()));

    fields = newList();
    fields.add(field("text", "string", true, "要显示的提示内容"));
    tools.add(toolItem("show_toast", "在设备上弹出 Toast 提示", fields));

    return tools;
}

Map toolItem(String name, String description, List fields) {
    Map item = newMap();
    item.put("name", name);
    item.put("description", description);

    Map schema = newMap();
    schema.put("type", "object");
    Map properties = newMap();
    List required = newList();

    int size = fields == null ? 0 : fields.size();
    int i = 0;
    while (i < size) {
        Map field = (Map) fields.get(i);
        String fieldName = String.valueOf(field.get("name"));
        Map body = newMap();
        body.put("type", field.get("type"));
        body.put("description", field.get("description"));
        properties.put(fieldName, body);
        if (Boolean.TRUE.equals(field.get("required"))) required.add(fieldName);
        i = i + 1;
    }

    schema.put("properties", properties);
    schema.put("required", required);
    schema.put("additionalProperties", Boolean.FALSE);
    item.put("inputSchema", schema);
    return item;
}

Map field(String name, String type, boolean required, String description) {
    Map item = newMap();
    item.put("name", name);
    item.put("type", type);
    item.put("required", Boolean.valueOf(required));
    item.put("description", description);
    return item;
}

String formatToolText(String toolName, Object data) {
    return fullValue(data);
}

// ===== mcp_server.java =====
void startMcpServer() {
    if (Boolean.TRUE.equals(MCP_STATE.get("running"))) {
        appendLog("[FkwMcpPlugin] server already running");
        return;
    }

    try {
        Map config = loadMcpConfig();
        MCP_STATE.put("config", config);
        long port = getServerPort();
        String path = getServerPath();
        appendLog("[FkwMcpPlugin] start server port=" + port + " path=" + path);

        ServerSocket serverSocket = new ServerSocket((int) port);
        MCP_STATE.put("serverSocket", serverSocket);
        MCP_STATE.put("running", Boolean.TRUE);
        appendLog("[FkwMcpPlugin] ServerSocket created");

        final ServerSocket holderSocket = serverSocket;
        final long holderPort = port;
        Thread serverThread = new Thread(new Runnable() {
            public void run() {
                appendLog("[FkwMcpPlugin] accept thread started port=" + holderPort);
                while (Boolean.TRUE.equals(MCP_STATE.get("running"))) {
                    try {
                        Socket socket = holderSocket.accept();
                        appendLog("[FkwMcpPlugin] client accepted " + safeSocketAddress(socket));
                        handleClientAsync(socket);
                    } catch (Exception e) {
                        if (Boolean.TRUE.equals(MCP_STATE.get("running"))) {
                            appendLog("[FkwMcpPlugin] accept failed " + shortText(stackTraceText(e), 300));
                        }
                    }
                }
                appendLog("[FkwMcpPlugin] accept thread stopped");
            }
        });
        serverThread.setName("FkwMcpServer");
        serverThread.start();
        ((List) MCP_STATE.get("threads")).add(serverThread);
        appendLog("[FkwMcpPlugin] server started");
    } catch (Exception e) {
        appendLog("[FkwMcpPlugin] start failed " + shortText(stackTraceText(e), 500));
    }
}

void stopMcpServer() {
    appendLog("[FkwMcpPlugin] stop server");
    MCP_STATE.put("running", Boolean.FALSE);
    try {
        ServerSocket serverSocket = (ServerSocket) MCP_STATE.get("serverSocket");
        if (serverSocket != null) {
            serverSocket.close();
            appendLog("[FkwMcpPlugin] ServerSocket closed");
        } else {
            appendLog("[FkwMcpPlugin] ServerSocket is null");
        }
    } catch (Exception e) {
        appendLog("[FkwMcpPlugin] stop failed " + shortText(stackTraceText(e), 300));
    }
}

void handleClientAsync(Socket socket) {
    final Socket holderSocket = socket;
    Thread t = new Thread(new Runnable() {
        public void run() {
            try {
                handleClient(holderSocket);
            } catch (Exception e) {
                appendLog("[FkwMcpPlugin] client failed " + shortText(stackTraceText(e), 300));
                try {
                    holderSocket.close();
                } catch (Exception ignore) {
                }
            }
        }
    });
    t.setName("FkwMcpClient-" + System.currentTimeMillis());
    t.start();
    ((List) MCP_STATE.get("threads")).add(t);
}

void handleClient(Socket socket) throws Exception {
    InputStream input = socket.getInputStream();
    OutputStream output = socket.getOutputStream();

    String requestLine = readHttpLine(input);
    if (requestLine == null || requestLine.equals("")) {
        socket.close();
        return;
    }

    String method = "GET";
    String path = "/";
    String[] first = requestLine.split(" ");
    if (first.length >= 2) {
        method = first[0];
        path = first[1];
    }

    int contentLength = 0;
    String line = null;
    while ((line = readHttpLine(input)) != null) {
        if (line.equals("")) {
            break;
        }
        String lower = line.toLowerCase();
        if (lower.startsWith("content-length:")) {
            contentLength = Integer.parseInt(line.substring(line.indexOf(":") + 1).trim());
        }
    }

    appendLog("[FkwMcpPlugin] request " + method + " " + path + " len=" + contentLength);

    if (!path.equals(getServerPath())) {
        writeHttp(output, 404, "application/json; charset=UTF-8", "{\"error\":\"path not found\"}");
        socket.close();
        return;
    }

    if ("GET".equals(method)) {
        String body = "event: endpoint\n" +
                "data: {\"message\":\"MCP streamable-http ready, use POST with JSON-RPC.\"}\n\n";
        writeHttp(output, 200, "text/event-stream; charset=UTF-8", body);
        socket.close();
        return;
    }

    if (!"POST".equals(method)) {
        writeHttp(output, 405, "application/json; charset=UTF-8", "{\"error\":\"only GET and POST supported\"}");
        socket.close();
        return;
    }

    String body = readHttpBody(input, contentLength);
    appendLog("[FkwMcpPlugin] body " + shortText(body, 300));

    Map rpcResult = null;
    try {
        rpcResult = handleRpc(body);
    } catch (Exception e) {
        appendLog("[FkwMcpPlugin] rpc failed " + shortText(stackTraceText(e), 300));
        rpcResult = rpcResult(true, errorResponse(null, -32603, e.getMessage()));
    }

    if (rpcResult != null && Boolean.TRUE.equals(rpcResult.get("hasBody"))) {
        String responseText = stringify(rpcResult.get("payload"));
        appendLog("[FkwMcpPlugin] response len=" + responseText.length());
        writeHttp(output, 200, "application/json; charset=UTF-8", responseText);
    } else {
        writeHttp(output, 202, "application/json; charset=UTF-8", "");
    }

    socket.close();
}

void writeHttp(OutputStream output, int code, String contentType, String body) throws Exception {
    byte[] bytes = body.getBytes("UTF-8");
    String reason = code == 200 ? "OK" : (code == 202 ? "Accepted" : (code == 404 ? "Not Found" : "Error"));
    String headers = "HTTP/1.1 " + code + " " + reason + "\r\n" +
            "Content-Type: " + contentType + "\r\n" +
            "Content-Length: " + bytes.length + "\r\n" +
            "Connection: close\r\n" +
            "\r\n";
    output.write(headers.getBytes("UTF-8"));
    output.write(bytes);
    output.flush();
}

Map handleRpc(String text) {
    Object obj = parseJson(text);
    if (!(obj instanceof Map)) {
        return rpcResult(true, errorResponse(null, -32600, "request body must be a JSON object"));
    }

    Map req = (Map) obj;
    Object id = req.get("id");
    String method = req.get("method") == null ? null : String.valueOf(req.get("method"));
    Map params = req.get("params") instanceof Map ? (Map) req.get("params") : newMap();

    appendLog("[FkwMcpPlugin] rpc method=" + method + " id=" + id);

    if (method == null || method.equals("")) {
        return rpcResult(true, errorResponse(id, -32600, "method is required"));
    }

    if (method.equals("initialize")) {
        String clientProtocolVersion = params.get("protocolVersion") == null
                ? "2025-06-18"
                : String.valueOf(params.get("protocolVersion"));

        Map result = newMap();
        result.put("protocolVersion", clientProtocolVersion);

        Map capabilities = newMap();
        Map tools = newMap();
        tools.put("listChanged", Boolean.FALSE);
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        Map serverInfo = newMap();
        serverInfo.put("name", "fkw-mcp-plugin");
        serverInfo.put("version", pluginVersion);
        result.put("serverInfo", serverInfo);
        result.put("instructions", "This server runs in FkWeChat plugin environment. Use tools/list and tools/call.");
        return rpcResult(true, successResponse(id, result));
    }

    if (method.equals("notifications/initialized")) {
        return rpcResult(false, null);
    }

    if (method.equals("ping")) {
        Map pong = newMap();
        pong.put("ok", Boolean.TRUE);
        return rpcResult(true, successResponse(id, pong));
    }

    if (method.equals("tools/list")) {
        Map result = newMap();
        result.put("tools", describeTools());
        return rpcResult(true, successResponse(id, result));
    }

    if (method.equals("tools/call")) {
        String toolName = params.get("name") == null ? "" : String.valueOf(params.get("name"));
        Map args = params.get("arguments") instanceof Map ? (Map) params.get("arguments") : newMap();
        appendLog("[FkwMcpPlugin] tool " + toolName + " args=" + shortText(stringify(args), 200));
        try {
            long startAt = System.currentTimeMillis();
            Object data = handleToolCall(toolName, args);
            long elapsed = System.currentTimeMillis() - startAt;
            String fullText = fullValue(data);
            appendLog("[FkwMcpPlugin] tool ok " + toolName + " elapsedMs=" + elapsed + " textLen=" + fullText.length() + " data=" + dataSummary(data));

            Map result = newMap();
            List content = newList();
            Map textItem = newMap();
            textItem.put("type", "text");
            textItem.put("text", fullText);
            content.add(textItem);
            result.put("content", content);

            result.put("isError", Boolean.FALSE);
            return rpcResult(true, successResponse(id, result));
        } catch (Exception e) {
            appendLog("[FkwMcpPlugin] tool fail " + toolName + " " + shortText(stackTraceText(e), 300));
            return rpcResult(true, errorResponse(id, -32602, e.getMessage()));
        }
    }

    return rpcResult(true, errorResponse(id, -32601, "method not found: " + method));
}

String dataSummary(Object data) {
    if (data == null) return "null";
    if (data instanceof List) {
        return "list(size=" + ((List) data).size() + ")";
    }
    if (data instanceof Map) {
        return "map(keys=" + ((Map) data).keySet() + ")";
    }
    String text = String.valueOf(data);
    return shortText(text, 120);
}

Map rpcResult(boolean hasBody, Map payload) {
    Map result = newMap();
    result.put("hasBody", Boolean.valueOf(hasBody));
    result.put("payload", payload);
    return result;
}

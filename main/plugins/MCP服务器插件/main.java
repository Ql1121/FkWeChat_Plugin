loadJava("fkw_mcp");

void onLoad() {
    initMcpState();
    appendLog("[FkwMcpPlugin] onLoad start");
    appendLog("[FkwMcpPlugin] pluginName=" + pluginName);
    appendLog("[FkwMcpPlugin] pluginVersion=" + pluginVersion);
    appendLog("[FkwMcpPlugin] pluginAuthor=" + pluginAuthor);
    appendLog("[FkwMcpPlugin] pluginPath=" + pluginPath);
    appendLog("[FkwMcpPlugin] hostVerCode=" + hostVerCode);
    appendLog("[FkwMcpPlugin] hostVerName=" + hostVerName);
    appendLog("[FkwMcpPlugin] myWxId=" + getSafeMyWxId());
    startMcpServer();
    appendLog("[FkwMcpPlugin] onLoad done");
}

void onUnload() {
    stopMcpServer();
    appendLog("[FkwMcpPlugin] onUnload done");
}

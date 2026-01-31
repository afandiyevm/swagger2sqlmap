package swagger2sqlmap.sqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import swagger2sqlmap.model.EndpointRow;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

public class SqlmapScriptExporter {

  private final MontoyaApi api;
  private final Function<EndpointRow, HttpRequest> requestBuilder;
  private final List<EndpointRow> rows;
  private final SqlmapCommandBuilder.Options options;

  public SqlmapScriptExporter(
      MontoyaApi api,
      Function<EndpointRow, HttpRequest> requestBuilder,
      List<EndpointRow> rows,
      SqlmapCommandBuilder.Options options
  ) {
    this.api = api;
    this.requestBuilder = requestBuilder;
    this.rows = rows;
    this.options = options == null ? SqlmapCommandBuilder.Options.defaults() : options;
  }

  public void saveAsSh(Component parent) throws Exception {
    File out = chooseSaveFile(parent, "swagger2sqlmap.sh");
    if (out == null) return;

    StringBuilder sb = new StringBuilder();
    sb.append("#!/bin/bash\n");
    sb.append("set -e\n\n");

    for (EndpointRow r : rows) {
      String cmd = buildShellCommandFor(r);
      sb.append("echo ").append(shellQuote("=== " + r.method() + " " + r.path() + " ===")).append("\n");
      sb.append(cmd).append("\n\n");
    }

    Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
    api.logging().logToOutput("Saved .sh: " + out.getAbsolutePath());
  }

  public void saveAsPy(Component parent) throws Exception {
    File out = chooseSaveFile(parent, "swagger2sqlmap.py");
    if (out == null) return;

    StringBuilder sb = new StringBuilder();
    sb.append("#!/usr/bin/env python3\n");
    sb.append("import subprocess\n\n");
    sb.append("commands = [\n");

    for (EndpointRow r : rows) {
      String cmd = buildShellCommandFor(r);
      sb.append("  ").append(pyQuote(cmd)).append(",\n");
    }
    sb.append("]\n\n");
    sb.append("for c in commands:\n");
    sb.append("  print('RUN:', c)\n");
    sb.append("  subprocess.call(c, shell=True)\n");

    Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
    api.logging().logToOutput("Saved .py: " + out.getAbsolutePath());
  }

  public void saveAsPs1(Component parent) throws Exception {
    File out = chooseSaveFile(parent, "swagger2sqlmap.ps1");
    if (out == null) return;

    StringBuilder sb = new StringBuilder();
    sb.append("$ErrorActionPreference = 'Stop'\n\n");

    for (EndpointRow r : rows) {
      String cmd = buildShellCommandFor(r);
      sb.append("Write-Host ").append(psQuote("=== " + r.method() + " " + r.path() + " ===")).append("\n");
      sb.append(cmd).append("\n\n");
    }

    Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
    api.logging().logToOutput("Saved .ps1: " + out.getAbsolutePath());
  }

  // ================= internals =================

  private String buildShellCommandFor(EndpointRow r) {
    HttpRequest req = requestBuilder.apply(r);
    var cmd = SqlmapCommandBuilder.build(req, options);
    return SqlmapCommandBuilder.toShellCommand(cmd);
  }

  private File chooseSaveFile(Component parent, String defaultName) {
    JFileChooser fc = new JFileChooser();
    fc.setSelectedFile(new File(defaultName));
    if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
    return fc.getSelectedFile();
  }

  private static String shellQuote(String s) {
    if (s == null) return "''";
    return "'" + s.replace("'", "'\"'\"'") + "'";
  }

  private static String pyQuote(String s) {
    if (s == null) return "''";
    // python single-quote escaping
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  private static String psQuote(String s) {
    if (s == null) return "''";
    // PowerShell single quotes escape by doubling ''
    return "'" + s.replace("'", "''") + "'";
  }
}

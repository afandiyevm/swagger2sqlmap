package swagger2sqlmap.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import swagger2sqlmap.model.EndpointRow;
import swagger2sqlmap.sqlmap.SqlmapCommandBuilder;
import swagger2sqlmap.sqlmap.SqlmapScriptExporter;
import swagger2sqlmap.swagger.SwaggerParser;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public class Swagger2SqlmapUi {

  private final MontoyaApi api;

  // Root
  private final JPanel root = new JPanel(new BorderLayout(8, 8));
  private final JTabbedPane tabs = new JTabbedPane();

  // ========= Targets tab =========
  private final JPanel targetsRoot = new JPanel(new BorderLayout(8, 8));

  private final JTextField swaggerFileField = new JTextField();
  private final JTextField baseUrlField = new JTextField();

  private final JButton loadSwaggerBtn = new JButton("Import");
  private final JButton loadIntoTableBtn = new JButton("Load");
  private final JButton clearTargetsBtn = new JButton("Clear");

  private final JLabel targetsStatus = new JLabel("Ready");

  private final JTextField searchField = new JTextField();
  private final JComboBox<String> methodFilter = new JComboBox<>(
      new String[]{"ALL","GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS"}
  );

  private final EndpointsTableModel tableModel = new EndpointsTableModel();
  private final JTable table = new JTable(tableModel);
  private final TableRowSorter<EndpointsTableModel> sorter = new TableRowSorter<>(tableModel);

  private final HttpRequestEditor requestEditor;
  private final JSplitPane targetsSplit;

  // ========= Authorization tab =========
  private final JPanel authRoot = new JPanel(new BorderLayout(8, 8));
  private final JTextArea tokenArea = new JTextArea(5, 80);
  private final JButton insertFromClipboardBtn = new JButton("Insert from Clipboard");
  private final JButton loadFromHistoryBtn = new JButton("Load from Burp History");
  private final JLabel authStatus = new JLabel("No token loaded");

  // ========= Command Builder tab =========
  private final JPanel cmdRoot = new JPanel(new BorderLayout(8, 8));

  private final JTextArea sqlmapCommandArea = new JTextArea(7, 80);
  private final JButton buildSqlmapBtn = new JButton("Build command");
  private final JButton copySqlmapBtn = new JButton("Copy");
  private final JButton exportBtn = new JButton("Export ▼"); // dropdown

  private final JSpinner levelSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
  private final JSpinner riskSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 3, 1));
  private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
  private final JCheckBox batchCheck = new JCheckBox("--batch", true);
  private final JCheckBox randomAgentCheck = new JCheckBox("--random-agent", true);
  private final JCheckBox includeHeadersCheck = new JCheckBox("Include headers", true);
  private final JComboBox<String> headersModeCombo = new JComboBox<>(new String[]{"IMPORTANT_ONLY", "ALL"});
  private final JTextField extraArgsField = new JTextField();

  // ========= Logs tab =========
  private final JPanel logsRoot = new JPanel(new BorderLayout(8, 8));
  private final JTextArea logArea = new JTextArea(12, 80);
  private final JButton clearLogsBtn = new JButton("Clear logs");

  // ========= About tab =========
  private final JPanel aboutRoot = new JPanel(new BorderLayout(8, 8));
  private final JTextArea aboutArea = new JTextArea();

  // ========= State =========
  private File selectedSwaggerFile = null;
  private SwaggerParser.ParseResult parsed = null;

  public Swagger2SqlmapUi(MontoyaApi api) {
    this.api = api;
    this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);

    this.targetsSplit = new JSplitPane(
        JSplitPane.VERTICAL_SPLIT,
        buildTargetsTableBlock(),
        buildTargetsEditorBlock()
    );

    buildUi();
    applyDefaults();
    wire();
  }

  public JComponent getRoot() {
    return root;
  }

  // ================= UI build =================

  private void buildUi() {
    root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

    // Order as you requested: Targets | Authorization | Command Builder | Logs | About
    tabs.addTab("Targets", targetsRoot);
    tabs.addTab("Authorization", authRoot);
    tabs.addTab("Command Builder", cmdRoot);
    tabs.addTab("Logs", logsRoot);
    tabs.addTab("About", aboutRoot);

    buildTargetsTab();
    buildAuthorizationTab();
    buildCommandBuilderTab();
    buildLogsTab();
    buildAboutTab();

    root.add(tabs, BorderLayout.CENTER);
  }

  private void buildTargetsTab() {
    targetsRoot.removeAll();
    targetsRoot.add(buildTargetsTopPanel(), BorderLayout.NORTH);

    targetsSplit.setResizeWeight(0.55);
    targetsSplit.setDividerLocation(0.55);
    targetsSplit.setContinuousLayout(true);

    targetsRoot.add(targetsSplit, BorderLayout.CENTER);
  }

  private JComponent buildTargetsTopPanel() {
    JPanel top = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4,4,4,4);
    c.fill = GridBagConstraints.HORIZONTAL;

    // Row 0: swagger file
    c.gridx=0; c.gridy=0; c.weightx=0;
    top.add(new JLabel("Swagger/OpenAPI JSON:"), c);

    c.gridx=1; c.weightx=1;
    top.add(swaggerFileField, c);

    JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    btns.add(loadSwaggerBtn);
    btns.add(loadIntoTableBtn);
    btns.add(clearTargetsBtn);

    c.gridx=2; c.weightx=0;
    top.add(btns, c);

    // Row 1: base url
    c.gridx=0; c.gridy=1; c.weightx=0;
    top.add(new JLabel("Base URL:"), c);

    c.gridx=1; c.weightx=1; c.gridwidth=2;
    top.add(baseUrlField, c);

    // Row 2: filters
    c.gridx=0; c.gridy=2; c.weightx=0; c.gridwidth=1;
    top.add(new JLabel("Search:"), c);

    c.gridx=1; c.weightx=1;
    top.add(searchField, c);

    c.gridx=2; c.weightx=0;
    JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    filterRight.add(new JLabel("Method:"));
    filterRight.add(methodFilter);
    top.add(filterRight, c);

    // Row 3: status
    c.gridx=0; c.gridy=3; c.weightx=0;
    top.add(new JLabel("Status:"), c);

    c.gridx=1; c.weightx=1; c.gridwidth=2;
    top.add(targetsStatus, c);

    return top;
  }

  private JComponent buildTargetsTableBlock() {
    JPanel p = new JPanel(new BorderLayout(6,6));
    p.add(new JLabel("Endpoints:"), BorderLayout.NORTH);

    table.setRowSorter(sorter);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    p.add(new JScrollPane(table), BorderLayout.CENTER);
    return p;
  }

  private JComponent buildTargetsEditorBlock() {
    JPanel p = new JPanel(new BorderLayout(6,6));
    p.add(requestEditor.uiComponent(), BorderLayout.CENTER);
    return p;
  }

  private void buildAuthorizationTab() {
    authRoot.removeAll();

    JPanel p = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(8,8,8,8);
    c.fill = GridBagConstraints.HORIZONTAL;

    c.gridx=0; c.gridy=0; c.weightx=0;
    p.add(new JLabel("Authorization token (Bearer):"), c);

    c.gridx=1; c.weightx=1;
    p.add(new JScrollPane(tokenArea), c);

    c.gridx=0; c.gridy=1; c.weightx=0;
    p.add(new JLabel("Actions:"), c);

    c.gridx=1; c.weightx=1;
    JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    btns.add(insertFromClipboardBtn);
    btns.add(loadFromHistoryBtn);
    p.add(btns, c);

    c.gridx=0; c.gridy=2; c.weightx=0;
    p.add(new JLabel("Status:"), c);

    c.gridx=1; c.weightx=1;
    p.add(authStatus, c);

    authRoot.add(p, BorderLayout.NORTH);
  }

  private void buildCommandBuilderTab() {
    cmdRoot.removeAll();
    cmdRoot.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));

    JPanel top = new JPanel(new BorderLayout(8,8));
    top.add(buildCommandOptionsPanel(), BorderLayout.CENTER);
    top.add(buildCommandButtonsPanel(), BorderLayout.EAST);

    cmdRoot.add(top, BorderLayout.NORTH);

    sqlmapCommandArea.setEditable(false);
    sqlmapCommandArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    sqlmapCommandArea.setLineWrap(true);
    sqlmapCommandArea.setWrapStyleWord(true);

    cmdRoot.add(new JScrollPane(sqlmapCommandArea), BorderLayout.CENTER);

    JLabel hint = new JLabel("Tip: select an endpoint in Targets tab, then click Build command.");
    hint.setForeground(new Color(0x666666));
    cmdRoot.add(hint, BorderLayout.SOUTH);
  }

  private JComponent buildCommandOptionsPanel() {
    JPanel opts = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4,4,4,4);
    c.fill = GridBagConstraints.HORIZONTAL;

    int y = 0;

    c.gridx=0; c.gridy=y; c.weightx=0;
    opts.add(new JLabel("level:"), c);
    c.gridx=1; c.weightx=0;
    opts.add(levelSpinner, c);

    c.gridx=2; c.weightx=0;
    opts.add(new JLabel("risk:"), c);
    c.gridx=3; c.weightx=0;
    opts.add(riskSpinner, c);

    c.gridx=4; c.weightx=0;
    opts.add(new JLabel("threads:"), c);
    c.gridx=5; c.weightx=0;
    opts.add(threadsSpinner, c);

    c.gridx=6; c.weightx=0;
    opts.add(batchCheck, c);
    c.gridx=7; c.weightx=0;
    opts.add(randomAgentCheck, c);

    y++;

    c.gridx=0; c.gridy=y; c.weightx=0;
    opts.add(includeHeadersCheck, c);

    c.gridx=1; c.weightx=0;
    opts.add(new JLabel("Headers mode:"), c);

    c.gridx=2; c.weightx=0; c.gridwidth=2;
    opts.add(headersModeCombo, c);
    c.gridwidth=1;

    y++;

    c.gridx=0; c.gridy=y; c.weightx=0;
    opts.add(new JLabel("Extra args:"), c);

    c.gridx=1; c.weightx=1; c.gridwidth=7;
    opts.add(extraArgsField, c);

    return opts;
  }

  private JComponent buildCommandButtonsPanel() {
    JPanel btns = new JPanel(new GridLayout(0,1,6,6));
    btns.add(buildSqlmapBtn);
    btns.add(copySqlmapBtn);
    btns.add(exportBtn);
    return btns;
  }

  private void buildLogsTab() {
    logArea.setEditable(false);
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    top.add(clearLogsBtn);

    logsRoot.add(top, BorderLayout.NORTH);
    logsRoot.add(new JScrollPane(logArea), BorderLayout.CENTER);
  }

  private void buildAboutTab() {
    aboutRoot.removeAll();
    aboutRoot.setLayout(new BorderLayout(10, 10));

    // ================= TOP: Logo =================
    JLabel logoLabel = new JLabel();
    logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

    try {
        ImageIcon icon = new ImageIcon(
            getClass().getClassLoader().getResource("swagger2sqlmap.png")
        );
        logoLabel.setIcon(icon);
    } catch (Exception ignored) {
        logoLabel.setText("Swagger2Sqlmap");
        logoLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
    }

    aboutRoot.add(logoLabel, BorderLayout.NORTH);

    // ================= CENTER: Text =================
    JTextArea aboutText = new JTextArea();
    aboutText.setEditable(false);
    aboutText.setLineWrap(true);
    aboutText.setWrapStyleWord(true);
    aboutText.setFont(new Font("SansSerif", Font.PLAIN, 13));

    aboutText.setText("""
Swagger2Sqlmap is a Burp Suite extension designed to bridge the gap between OpenAPI / Swagger specifications and advanced SQL injection testing with sqlmap.

The extension allows security testers and red teamers to:
• Load Swagger / OpenAPI JSON files
• Automatically extract and normalize endpoints
• Generate realistic HTTP requests (including body templates)
• Send endpoints to Burp tools (Repeater, Intruder)
• Build ready-to-use sqlmap commands
• Export full sqlmap attack scripts (.sh, .py, .ps1)

This tool is intended for professional security testing and authorized penetration testing only.

Authors:
• Farkhad Askarov
• Murad Afandiyev

Version:
• Swagger2Sqlmap v1.0.0

GitHub:
https://github.com/afandiyevm/Swagger2Sqlmap
""");

    JScrollPane scroll = new JScrollPane(aboutText);
    scroll.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

    aboutRoot.add(scroll, BorderLayout.CENTER);
}


  private void applyDefaults() {
    swaggerFileField.setEditable(false);

    tokenArea.setLineWrap(true);
    tokenArea.setWrapStyleWord(true);
    tokenArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

    targetsStatus.setForeground(new Color(0x006400));
    authStatus.setForeground(new Color(0x006400));
  }

  // ================= Wiring =================

  private void wire() {
    // Filters
    Runnable apply = this::applyFilters;
    searchField.getDocument().addDocumentListener((SimpleDocumentListener) ev -> apply.run());
    methodFilter.addActionListener(e -> apply.run());

    // Row selection -> update request editor
    table.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      EndpointRow r = getSelectedEndpointRow();
      if (r == null) return;

      try {
        HttpRequest req = buildHttpRequest(r);
        requestEditor.setRequest(req);
        // when selection changes, clear last built command
        sqlmapCommandArea.setText("");
      } catch (Exception ex) {
        setTargetsStatus("Failed to build request: " + ex.getMessage(), false);
        logErr(ex);
      }
    });

    // Right-click menu: Send to Repeater/Intruder
    table.addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
      @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }

      private void maybeShow(MouseEvent e) {
        if (!e.isPopupTrigger()) return;

        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && row < table.getRowCount()) table.setRowSelectionInterval(row, row);

        EndpointRow r = getSelectedEndpointRow();
        if (r == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem toRepeater = new JMenuItem("Send to Repeater");
        toRepeater.addActionListener(ae -> sendToRepeater(r));
        menu.add(toRepeater);

        JMenuItem toIntruder = new JMenuItem("Send to Intruder");
        toIntruder.addActionListener(ae -> sendToIntruder(r));
        menu.add(toIntruder);

        menu.addSeparator();

        JMenuItem copyUrl = new JMenuItem("Copy full URL");
        copyUrl.addActionListener(ae -> copyToClipboard(joinUrlSafe(baseUrlField.getText(), r.path())));
        menu.add(copyUrl);

        menu.show(e.getComponent(), e.getX(), e.getY());
      }
    });

    // Targets buttons
    loadSwaggerBtn.addActionListener(e -> chooseAndParseSwagger());

    loadIntoTableBtn.addActionListener(e -> {
      if (parsed == null) {
        setTargetsStatus("Swagger not loaded. Click LOAD SWAGGER first.", false);
        log("LOAD INTO TABLE blocked: no parsed swagger");
        tabs.setSelectedComponent(logsRoot);
        return;
      }
      tableModel.setData(parsed.endpoints());
      setTargetsStatus("Loaded endpoints: " + parsed.endpoints().size(), true);
      log("Loaded endpoints into table: " + parsed.endpoints().size());
    });

    clearTargetsBtn.addActionListener(e -> clearTargets());

    // Authorization buttons
    insertFromClipboardBtn.addActionListener(e -> insertTokenFromClipboard());
    loadFromHistoryBtn.addActionListener(e -> loadTokenFromBurpHistory());

    // Logs buttons
    clearLogsBtn.addActionListener(e -> {
      logArea.setText("");
      api.logging().logToOutput("Logs cleared");
    });

    // Command builder buttons
    buildSqlmapBtn.addActionListener(e -> buildSqlmapCommandForSelected());
    copySqlmapBtn.addActionListener(e -> copySqlmapCommand());

    // Export dropdown
    exportBtn.addActionListener(e -> showExportMenu(exportBtn));
  }

  private void showExportMenu(Component anchor) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem sh = new JMenuItem("Save all requests as .sh");
    sh.addActionListener(ae -> exportAllAs("sh"));
    menu.add(sh);

    JMenuItem py = new JMenuItem("Save all requests as .py");
    py.addActionListener(ae -> exportAllAs("py"));
    menu.add(py);

    JMenuItem ps1 = new JMenuItem("Save all requests as .ps1");
    ps1.addActionListener(ae -> exportAllAs("ps1"));
    menu.add(ps1);

    menu.show(anchor, 0, anchor.getHeight());
  }

  private void exportAllAs(String kind) {
    if (tableModel.getRowCount() == 0) {
      log("Export blocked: table is empty (load swagger + load into table)");
      JOptionPane.showMessageDialog(root, "Table is empty. Load Swagger and Load into Table first.");
      return;
    }

    try {
      SqlmapCommandBuilder.Options opt = currentSqlmapOptions();
      List<EndpointRow> all = tableModel.getAll();

      SqlmapScriptExporter exporter = new SqlmapScriptExporter(api, this::buildHttpRequest, all, opt);

      switch (kind) {
        case "sh" -> exporter.saveAsSh(root);
        case "py" -> exporter.saveAsPy(root);
        case "ps1" -> exporter.saveAsPs1(root);
        default -> throw new IllegalArgumentException("Unknown export: " + kind);
      }

      tabs.setSelectedComponent(logsRoot);
    } catch (Exception ex) {
      logErr(ex);
      JOptionPane.showMessageDialog(root, "Export failed: " + ex.getMessage());
    }
  }

  // ================= Command Builder =================

  private SqlmapCommandBuilder.Options currentSqlmapOptions() {
    return new SqlmapCommandBuilder.Options(
        "sqlmap",
        includeHeadersCheck.isSelected(),
        "ALL".equals(String.valueOf(headersModeCombo.getSelectedItem()))
            ? SqlmapCommandBuilder.Options.HeadersMode.ALL
            : SqlmapCommandBuilder.Options.HeadersMode.IMPORTANT_ONLY,
        batchCheck.isSelected(),
        randomAgentCheck.isSelected(),
        (Integer) levelSpinner.getValue(),
        (Integer) riskSpinner.getValue(),
        (Integer) threadsSpinner.getValue(),
        null,
        null,
        safe(extraArgsField.getText())
    );
  }

  private void buildSqlmapCommandForSelected() {
    EndpointRow r = getSelectedEndpointRow();
    if (r == null) {
      JOptionPane.showMessageDialog(root, "Select an endpoint in Targets tab first.");
      return;
    }

    try {
      HttpRequest req = buildHttpRequest(r);
      var cmd = SqlmapCommandBuilder.build(req, currentSqlmapOptions());
      String shell = SqlmapCommandBuilder.toShellCommand(cmd);
      sqlmapCommandArea.setText(shell);
      log("Built sqlmap command for: " + r.method() + " " + r.path());
    } catch (Exception ex) {
      logErr(ex);
      JOptionPane.showMessageDialog(root, "Build failed: " + ex.getMessage());
    }
  }

  private void copySqlmapCommand() {
    String cmd = safe(sqlmapCommandArea.getText()).trim();
    if (cmd.isEmpty()) {
      buildSqlmapCommandForSelected();
      cmd = safe(sqlmapCommandArea.getText()).trim();
    }
    if (cmd.isEmpty()) return;

    copyToClipboard(cmd);
    log("Copied sqlmap command");
  }

  // ================= Swagger load & parse =================

  private void chooseAndParseSwagger() {
    JFileChooser fc = new JFileChooser();
    if (fc.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;

    selectedSwaggerFile = fc.getSelectedFile();
    swaggerFileField.setText(selectedSwaggerFile.getAbsolutePath());

    try {
      parsed = SwaggerParser.parse(selectedSwaggerFile);

      // auto base url, but user can edit
      if (parsed.baseUrl() != null && !parsed.baseUrl().isBlank()) {
        baseUrlField.setText(parsed.baseUrl());
      }

      setTargetsStatus("Swagger parsed OK. Endpoints found: " + parsed.endpoints().size(), true);
      log("Parsed swagger: " + selectedSwaggerFile.getName());
      log("Detected baseUrl: " + parsed.baseUrl());
      log("Endpoints: " + parsed.endpoints().size());

    } catch (Exception ex) {
      parsed = null;
      setTargetsStatus("Swagger parse failed: " + ex.getMessage(), false);
      logErr(ex);
      tabs.setSelectedComponent(logsRoot);
    }
  }

  private void clearTargets() {
    selectedSwaggerFile = null;
    parsed = null;

    swaggerFileField.setText("");
    baseUrlField.setText("");
    searchField.setText("");
    methodFilter.setSelectedItem("ALL");

    tableModel.setData(List.of());
    requestEditor.setRequest(HttpRequest.httpRequest(""));
    sqlmapCommandArea.setText("");

    setTargetsStatus("Targets cleared.", true);
    log("Targets cleared");
  }

  // ================= Authorization actions =================

  private void insertTokenFromClipboard() {
    try {
      String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
      if (clip == null) clip = "";
      clip = clip.trim();

      clip = clip.replaceFirst("(?i)^authorization\\s*:\\s*bearer\\s+", "");
      clip = clip.replaceFirst("(?i)^bearer\\s+", "");

      tokenArea.setText(clip);
      setAuthStatus(clip.isEmpty() ? "Clipboard empty" : "Token inserted from clipboard", !clip.isEmpty());
      log("Token inserted from clipboard (" + (clip.isEmpty() ? "empty" : "ok") + ")");
    } catch (Exception ex) {
      setAuthStatus("Clipboard read failed: " + ex.getMessage(), false);
      logErr(ex);
    }
  }

  private void loadTokenFromBurpHistory() {
    try {
      var history = api.proxy().history();
      for (int i = history.size() - 1; i >= 0; i--) {
        var entry = history.get(i);
        var req = entry.finalRequest();

        for (var h : req.headers()) {
          if ("Authorization".equalsIgnoreCase(h.name())) {
            String v = safe(h.value()).trim();
            if (v.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
              String token = v.substring("bearer ".length()).trim();
              tokenArea.setText(token);
              setAuthStatus("Token loaded from Burp History", true);
              log("Token loaded from history");
              return;
            }
          }
        }
      }
      setAuthStatus("No Bearer token found in Burp History", false);
      log("No token found in history");
    } catch (Exception ex) {
      setAuthStatus("History read failed: " + ex.getMessage(), false);
      logErr(ex);
    }
  }

  // ================= Burp actions =================

  private void sendToRepeater(EndpointRow r) {
    try {
      HttpRequest req = buildHttpRequest(r);
      String name = r.method().toUpperCase(Locale.ROOT) + " " + r.path();
      api.repeater().sendToRepeater(req, name);
      setTargetsStatus("Sent to Repeater: " + name, true);
      log("Send to Repeater: " + name);
    } catch (Exception ex) {
      setTargetsStatus("Send to Repeater failed: " + ex.getMessage(), false);
      logErr(ex);
    }
  }

  private void sendToIntruder(EndpointRow r) {
    try {
      HttpRequest req = buildHttpRequest(r);
      String name = r.method().toUpperCase(Locale.ROOT) + " " + r.path();
      api.intruder().sendToIntruder(req, name);
      setTargetsStatus("Sent to Intruder: " + name, true);
      log("Send to Intruder: " + name);
    } catch (Exception ex) {
      setTargetsStatus("Send to Intruder failed: " + ex.getMessage(), false);
      logErr(ex);
    }
  }

  private HttpRequest buildHttpRequest(EndpointRow r) {
    String base = safe(baseUrlField.getText()).trim();
    if (base.isEmpty()) base = "http://example.com";

    String full = joinUrlSafe(base, r.path());

    HttpRequest req = HttpRequest.httpRequestFromUrl(full)
        .withMethod(r.method().toUpperCase(Locale.ROOT));

    String token = safe(tokenArea.getText()).trim();
    if (!token.isEmpty()) {
      req = req.withAddedHeader("Authorization", "Bearer " + token);
    }

    // BODY + Content-Type (если parser даёт template)
    if (r.bodyTemplate() != null && !r.bodyTemplate().isBlank()) {
      String ct = safe(r.contentType()).trim();
      if (ct.isEmpty()) ct = "application/json";
      req = req.withAddedHeader("Content-Type", ct);
      req = req.withBody(r.bodyTemplate());
    }

    return req;
  }

  private static String joinUrlSafe(String base, String path) {
    try {
      String b = (base == null ? "" : base.trim());
      if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
      if (b.isEmpty()) b = "http://example.com";

      String p = (path == null ? "" : path.trim());
      if (!p.startsWith("/")) p = "/" + p;

      // {id} -> 1 for valid URL preview
      p = p.replaceAll("\\{[^/]+}", "1");

      return new URI(b + p).toString();
    } catch (Exception ignored) {
      return "http://example.com/";
    }
  }

  // ================= Filtering =================

  private void applyFilters() {
    String q = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
    String m = (String) methodFilter.getSelectedItem();

    sorter.setRowFilter(new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends EndpointsTableModel, ? extends Integer> entry) {
        EndpointRow r = tableModel.getAt(entry.getIdentifier());
        if (r == null) return false;

        if (!"ALL".equals(m) && !r.method().equalsIgnoreCase(m)) return false;
        if (q.isEmpty()) return true;

        String hay = (safe(r.method()) + " " + safe(r.path()) + " " + safe(r.operationId()) + " " + safe(r.summary()))
            .toLowerCase(Locale.ROOT);

        return hay.contains(q);
      }
    });
  }

  private EndpointRow getSelectedEndpointRow() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) return null;
    int modelRow = table.convertRowIndexToModel(viewRow);
    return tableModel.getAt(modelRow);
  }

  // ================= Helpers =================

  private void setTargetsStatus(String s, boolean ok) {
    targetsStatus.setText(s);
    targetsStatus.setForeground(ok ? new Color(0x006400) : Color.RED);
  }

  private void setAuthStatus(String s, boolean ok) {
    authStatus.setText(s);
    authStatus.setForeground(ok ? new Color(0x006400) : Color.RED);
  }

  private void log(String s) {
    logArea.append(s + "\n");
    logArea.setCaretPosition(logArea.getDocument().getLength());
    api.logging().logToOutput(s);
  }

  private void logErr(Exception ex) {
    api.logging().logToError(ex.toString());
    logArea.append("[ERROR] " + ex + "\n");
    logArea.setCaretPosition(logArea.getDocument().getLength());
  }

  private static String safe(String s) { return s == null ? "" : s; }

  private static void copyToClipboard(String s) {
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard()
          .setContents(new StringSelection(s), null);
    } catch (Exception ignored) {}
  }
}

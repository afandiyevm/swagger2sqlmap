package swagger2sqlmap;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import swagger2sqlmap.ui.Swagger2SqlmapUi;

public class Swagger2SqlmapExtension implements BurpExtension {
  @Override
  public void initialize(MontoyaApi api) {
    api.extension().setName("Swagger2Sqlmap");

    Swagger2SqlmapUi ui = new Swagger2SqlmapUi(api);
    api.userInterface().registerSuiteTab("Swagger2Sqlmap", ui.getRoot());

    api.logging().logToOutput("Swagger2Sqlmap: UI loaded (table mode).");
  }
}

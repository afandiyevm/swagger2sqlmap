package swagger2sqlmap.ui;

import javax.swing.table.AbstractTableModel;

import swagger2sqlmap.model.EndpointRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EndpointsTableModel extends AbstractTableModel {

  private final List<EndpointRow> data = new ArrayList<>();

  private static final String[] COLS = {
      "Method",
      "Path",
      "OperationId",
      "Summary",
      "Content-Type",
      "Has Body"
  };

  public void setData(List<EndpointRow> rows) {
    data.clear();
    if (rows != null) data.addAll(rows);
    fireTableDataChanged();
  }

  public EndpointRow getAt(int row) {
    if (row < 0 || row >= data.size()) return null;
    return data.get(row);
  }

  /** Used by exporter: returns a snapshot copy of all loaded rows. */
  public List<EndpointRow> getAll() {
    return Collections.unmodifiableList(new ArrayList<>(data));
  }

  @Override
  public int getRowCount() {
    return data.size();
  }

  @Override
  public int getColumnCount() {
    return COLS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLS[column];
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return switch (columnIndex) {
      case 5 -> Boolean.class; // Has Body
      default -> String.class;
    };
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    EndpointRow r = getAt(rowIndex);
    if (r == null) return "";

    return switch (columnIndex) {
      case 0 -> safe(r.method());
      case 1 -> safe(r.path());
      case 2 -> safe(r.operationId());
      case 3 -> safe(r.summary());
      case 4 -> safe(r.contentType());
      case 5 -> r.bodyTemplate() != null && !r.bodyTemplate().isBlank();
      default -> "";
    };
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}

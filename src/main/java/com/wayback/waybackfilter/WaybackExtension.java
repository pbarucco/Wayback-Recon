import burp.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class WaybackExtension implements IBurpExtender, ITab {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JPanel mainPanel;
    private JTable resultsTable;
    private DefaultTableModel resultsTableModel;
    private JTextArea verboseArea;
    private ExecutorService executorService;
    private final Gson gson = new Gson();

    private JCheckBox addToSitemapCheck;
    private JLabel progressLabel;
    private TableRowSorter<DefaultTableModel> sorter;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        callbacks.setExtensionName("Wayback Recon");

        SwingUtilities.invokeLater(() -> {
            mainPanel = new JPanel(new BorderLayout());

            // Top controls
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField domainField = new JTextField(20);
            JButton fetchButton = new JButton("Fetch");
            addToSitemapCheck = new JCheckBox("Add to sitemap", false);
            progressLabel = new JLabel("Progress: 0 / 0");

            // Search bar
            JLabel searchLabel = new JLabel("Search:");
            JTextField searchField = new JTextField(15);

            topPanel.add(new JLabel("Domain:"));
            topPanel.add(domainField);
            topPanel.add(fetchButton);
            topPanel.add(addToSitemapCheck);
            topPanel.add(progressLabel);
            topPanel.add(searchLabel);
            topPanel.add(searchField);

            // Results table - Added MIME-Type column
            resultsTableModel = new DefaultTableModel(new Object[]{"Year", "URL", "Length", "MIME-Type"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            resultsTable = new JTable(resultsTableModel);
            resultsTable.setRowHeight(22);
            resultsTable.setFont(new Font("SansSerif", Font.PLAIN, 16));
            resultsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));
            resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            sorter = new TableRowSorter<>(resultsTableModel);
            resultsTable.setRowSorter(sorter);

            // Search listener
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }

                private void filter() {
                    String text = searchField.getText();
                    if (text.trim().length() == 0) {
                        sorter.setRowFilter(null);
                    } else {
                        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                    }
                }
            });

            // Right-click popup
            resultsTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) showPopup(e);
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) showPopup(e);
                }
                private void showPopup(MouseEvent e) {
                    int row = resultsTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < resultsTable.getRowCount()) {
                        if (!resultsTable.isRowSelected(row)) {
                            resultsTable.addRowSelectionInterval(row, row);
                        }
                        JPopupMenu popup = createTablePopupMenu();
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });

            JScrollPane tableScroll = new JScrollPane(resultsTable);

            // Verbose log
            verboseArea = new JTextArea();
            verboseArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            verboseArea.setEditable(false);
            JScrollPane verboseScroll = new JScrollPane(verboseArea);

            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, verboseScroll);
            splitPane.setResizeWeight(0.7);

            mainPanel.add(topPanel, BorderLayout.NORTH);
            mainPanel.add(splitPane, BorderLayout.CENTER);

            executorService = Executors.newFixedThreadPool(5);

            fetchButton.addActionListener(e -> {
                String domain = domainField.getText().trim();
                if (!domain.isEmpty()) {
                    resultsTableModel.setRowCount(0);
                    verboseArea.setText("");
                    progressLabel.setText("Progress: 0 / 0");
                    executorService.submit(() -> fetchWaybackURLs(domain));
                }
            });

            callbacks.addSuiteTab(WaybackExtension.this);
        });
    }

    private void fetchWaybackURLs(String domain) {
        String apiUrl = String.format(
            "http://web.archive.org/web/timemap/json?url=%s&fl=timestamp:4,original,length,mimetype&matchType=prefix&filter=!mimetype:image/*&filter=statuscode:200&collapse=urlkey&collapse=timestamp:4",
            domain
        );

        int maxRetries = 5;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                verbose("Fetching from Wayback API: " + apiUrl);

                HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);

                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                    List<List<String>> data = gson.fromJson(in, new TypeToken<List<List<String>>>(){}.getType());

                    if (data == null || data.size() <= 1) {
                        verbose("No results found.");
                        return;
                    }

                    int total = data.size() - 1;
                    AtomicInteger processed = new AtomicInteger(0);

                    for (int i = 1; i < data.size(); i++) {
                        List<String> row = data.get(i);
                        if (row == null || row.size() < 4) continue;

                        String year = row.get(0);
                        String url = row.get(1);
                        String length = row.get(2);
                        String mimeType = row.get(3);

                        int current = processed.incrementAndGet();

                        SwingUtilities.invokeLater(() -> {
                            resultsTableModel.addRow(new Object[]{year, url, length, mimeType});
                            progressLabel.setText(String.format("Progress: %d / %d", current, total));
                        });

                        if (addToSitemapCheck.isSelected()) {
                            addToSiteMap(url);
                        }
                    }
                }

                verbose("Fetch complete.");
                success = true; // mark as done if no exception
            } catch (Exception ex) {
                verbose("Error on attempt " + attempt + ": " + ex.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000L * attempt); // exponential-ish backoff
                    } catch (InterruptedException ie) {
                        verbose("Retry sleep interrupted: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    verbose("All " + maxRetries + " attempts failed.");
                }
            }
        }
    }

    private JPopupMenu createTablePopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem sendToSitemapItem = new JMenuItem("Send to Sitemap");
        sendToSitemapItem.addActionListener(e -> {
            int[] selectedRows = resultsTable.getSelectedRows();
            if (selectedRows.length > 0) {
                executorService.submit(() -> {
                    for (int rowIndex : selectedRows) {
                        String url = (String) resultsTableModel.getValueAt(resultsTable.convertRowIndexToModel(rowIndex), 1);
                        if (sendUrlToSitemap(url)) {
                            verbose("Sent to sitemap: " + url);
                        } else {
                            verbose("Failed to send: " + url);
                        }
                    }
                });
            }
        });

        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(e -> {
            int[] selectedRows = resultsTable.getSelectedRows();
            if (selectedRows.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int rowIndex : selectedRows) {
                    String url = (String) resultsTableModel.getValueAt(resultsTable.convertRowIndexToModel(rowIndex), 1);
                    sb.append(url).append("\n");
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
                verbose("Copied URL(s) to clipboard.");
            }
        });

        JMenuItem exportItem = new JMenuItem("Export to File");
        exportItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save results");
            int userSelection = chooser.showSaveDialog(mainPanel);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                exportResultsToFile(file);
            }
        });

        menu.add(sendToSitemapItem);
        menu.add(copyUrl);
        menu.add(exportItem);
        return menu;
    }

    private void addToSiteMap(String originalURL) {
        try {
            URL parsed = new URL(originalURL);
            int port = parsed.getPort();
            if (port == -1) {
                port = parsed.getProtocol().equalsIgnoreCase("https") ? 443 : 80;
            }
            IHttpService service = helpers.buildHttpService(parsed.getHost(), port, parsed.getProtocol());
            byte[] request = helpers.buildHttpRequest(parsed);
            callbacks.addToSiteMap(callbacks.makeHttpRequest(service, request));
        } catch (Exception ex) {
            verbose("Error adding to sitemap: " + ex.getMessage());
        }
    }

    private boolean sendUrlToSitemap(String originalURL) {
        try {
            URL parsed = new URL(originalURL);
            int port = parsed.getPort();
            if (port == -1) {
                port = parsed.getProtocol().equalsIgnoreCase("https") ? 443 : 80;
            }
            IHttpService service = helpers.buildHttpService(parsed.getHost(), port, parsed.getProtocol());
            byte[] request = helpers.buildHttpRequest(parsed);
            IHttpRequestResponse response = callbacks.makeHttpRequest(service, request);
            if (response != null) {
                callbacks.addToSiteMap(response);
                return true;
            }
        } catch (Exception ex) {
            verbose("Error sending to sitemap: " + ex.getMessage());
        }
        return false;
    }

    private void exportResultsToFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (int i = 0; i < resultsTableModel.getRowCount(); i++) {
                String year = (String) resultsTableModel.getValueAt(i, 0);
                String url = (String) resultsTableModel.getValueAt(i, 1);
                String length = (String) resultsTableModel.getValueAt(i, 2);
                String mime = (String) resultsTableModel.getValueAt(i, 3);
                writer.write(String.format("%s\t%s\t%s\t%s%n", year, url, length, mime));
            }
            verbose("Results exported to " + file.getAbsolutePath());
        } catch (IOException ex) {
            verbose("Error exporting results: " + ex.getMessage());
        }
    }

    private void verbose(String msg) {
        SwingUtilities.invokeLater(() -> verboseArea.append(msg + "\n"));
    }

    @Override
    public String getTabCaption() {
        return "Wayback Recon";
    }

    @Override
    public Component getUiComponent() {
        return mainPanel;
    }
}

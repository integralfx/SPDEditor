import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.Border;

public class SPDEditorGUI extends JFrame {
    private JComboBox<Integer> cboXMPNum;
    private JLabel lblSPDFile, lblXMP;
    private JRadioButton rdoTime, rdoCycles, rdoXMPTime, rdoXMPCycles;
    private JSpinner spnXMPVoltage;
    private JTabbedPane tabbedPane;
    private JTextField txtFrequencyns, txtFrequency, txtXMPFrequencyns, txtXMPFrequency;
    private LinkedHashMap<String, TextFieldPair> nameTextFieldMap, XMPnameTextFieldMap;
    private LinkedHashMap<Integer, JCheckBox> clCheckBoxMap, XMPclCheckBoxMap;
    private LinkedHashMap<String, JCheckBox> voltageCheckBoxMap;
    private SPDEditor spd;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SPDEditorGUI());
    }

    public SPDEditorGUI() {
        super("SPD Editor");

        addMenuBar();

        tabbedPane = new JTabbedPane();
        addSPDTab();
        addXMPTab();
        add(tabbedPane);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
    }

    private void addSPDTab() {
        JPanel panelSPD = new JPanel();
        panelSPD.setLayout(new BoxLayout(panelSPD, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        lblSPDFile = new JLabel("No SPD file opened");
        panel.add(lblSPDFile);
        panelSPD.add(panel);

        Frequency[] frequencies = {
                new Frequency(1200/3.0), // 400
                new Frequency(1600/3.0), // 533.33...
                new Frequency(2000/3.0), // 666.66...
                new Frequency(2400/3.0), // 800
                new Frequency(2800/3.0), // 933.33...
                new Frequency(3200/3.0)  // 1066.66...
        };
        panel = new JPanel();
        panel.add(new JLabel("Frequency:"));
        //JComboBox<Frequency> cboFrequencies = new JComboBox<>(frequencies);
        //panel.add(cboFrequencies);
        txtFrequencyns = new JTextField(5);
        txtFrequencyns.setEditable(false);
        panel.add(txtFrequencyns);
        txtFrequency = new JTextField(5);
        txtFrequency.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");

                boolean valid = true;
                String s = txtFrequency.getText();
                try {
                    double f = Double.valueOf(s);
                    if (f < 400) valid = false;
                    else {
                        txtFrequencyns.setText(String.format("%.3f", 1000/f));
                        /*
                         * Timings are calculated from the cycle time (ns),
                         * which don't change when changing frequency.
                         * Thus, we have to save the timings, update the
                         * frequency, then update with the timings that we have
                         * saved.
                         */
                        if (rdoCycles.isSelected()) {
                            LinkedHashMap<String, Integer> t = spd.getTimings();
                            spd.setFrequency(f);
                            spd.setTimings(t);
                        }
                        else if (rdoTime.isSelected()) {
                            spd.setFrequency(f);
                        }

                        updateSPDTimingsText();
                    }
                }
                catch (NumberFormatException ex) {
                    valid = false;
                }

                if (valid)
                    txtFrequency.setBackground(Color.WHITE);
                else
                    txtFrequency.setBackground(new Color(255, 100, 100));
            }
        });
        panel.add(txtFrequency);
        panelSPD.add(panel);

        panel = new JPanel();
        rdoTime = new JRadioButton("Scale from time (ns)");
        rdoTime.setToolTipText("Keeps the same absolute time in ns when changing frequency.");
        rdoTime.setSelected(true);
        panel.add(rdoTime);
        rdoCycles = new JRadioButton("Scale from cycles (ticks)");
        rdoCycles.setToolTipText("Keeps the same amount of cycles in ticks when changing frequency.");
        panel.add(rdoCycles);
        ButtonGroup group = new ButtonGroup();
        group.add(rdoTime);
        group.add(rdoCycles);
        panelSPD.add(panel);

        panel = new JPanel();
        voltageCheckBoxMap = new LinkedHashMap<>();
        String[] voltages = { "1.25v", "1.35v", "1.50v" };
        for (String v : voltages) {
            JCheckBox chk = new JCheckBox(v);
            chk.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (spd == null)
                        showErrorMsg("Please open an SPD file.");

                    spd.setVoltage(v, chk.isSelected());
                }
            });
            panel.add(chk);
            voltageCheckBoxMap.put(v, chk);
        }
        panelSPD.add(panel);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        clCheckBoxMap = new LinkedHashMap<>();
        int cl = 4;
        for (int row = 0; row < 3; row++) {
            JPanel p = new JPanel();
            for (int col = 0; col < 5; col++) {
                if (cl > 18) break;

                JCheckBox chk = new JCheckBox(String.valueOf(cl));
                chk.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (spd == null)
                            showErrorMsg("Please open an SPD file.");
                        else
                            spd.setSupportedCL(Integer.valueOf(chk.getText()), chk.isSelected());
                    }
                });
                p.add(chk);
                clCheckBoxMap.put(cl, chk);
                cl++;
            }
            panel.add(p);
        }
        Border b = BorderFactory.createLineBorder(Color.BLACK);
        panel.setBorder(BorderFactory.createTitledBorder(b, "Supported CLs"));
        panelSPD.add(panel);

        JPanel timingsPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        String[] timingNames = {
                "tCL", "tRCD", "tRP", "tRAS", "tRC","tRFC",
                "tRRD", "tFAW", "tWR", "tWTR", "tRTP"
        };
        nameTextFieldMap = new LinkedHashMap<>();
        for (String name : timingNames) {
            panel = new JPanel();

            JLabel lbl = new JLabel(name + ":");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panel.add(lbl);

            JTextField txtns = new JTextField(5);
            txtns.setEditable(false);
            panel.add(txtns);
            JTextField txtTicks = new JTextField(3);
            txtTicks.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (spd == null)
                        showErrorMsg("Please open an SPD file.");

                    boolean valid = true;
                    String s = txtTicks.getText();
                    try {
                        int t = Integer.valueOf(s);
                        if (t < 0) valid = false;
                        else if (name.equals("tCL")) {
                            if (t < 4 || t > 18) valid = false;
                            else {
                                spd.setTiming(name, t);
                                if (clCheckBoxMap.containsKey(t)) {
                                    clCheckBoxMap.get(t).setSelected(true);
                                    spd.setSupportedCL(t, true);
                                }
                            }
                        }
                        else spd.setTiming(name, t);

                        updateSPDTimingsText();
                    }
                    catch (NumberFormatException ex) {
                        valid = false;
                    }

                    if (valid)
                        txtTicks.setBackground(Color.WHITE);
                    else
                        txtTicks.setBackground(new Color(255, 100, 100));
                }
            });
            panel.add(txtTicks);
            timingsPanel.add(panel);

            nameTextFieldMap.put(name, new TextFieldPair(txtns, txtTicks));
        }
        panel = new JPanel();
        JButton btnSet = new JButton("Set");
        btnSet.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");

                for (Map.Entry<String, TextFieldPair> entry : nameTextFieldMap.entrySet()) {
                    JTextField txt = entry.getValue().right;
                    String name = entry.getKey(),
                            input = txt.getText();
                    boolean valid = true;
                    try {
                        int t = Integer.valueOf(input);
                        if (t < 0) valid = false;
                        else if (name.equals("tCL")) {
                            if (t < 4 || t > 18) valid = false;
                            else {
                                spd.setTiming(name, t);
                                if (clCheckBoxMap.containsKey(t)) {
                                    clCheckBoxMap.get(t).setSelected(true);
                                    spd.setSupportedCL(t, true);
                                }
                            }
                        }
                        else spd.setTiming(name, t);

                        updateSPDTimingsText();
                    }
                    catch (NumberFormatException ex) {
                        valid = false;
                    }

                    if (valid)
                        txt.setBackground(Color.WHITE);
                    else
                        txt.setBackground(new Color(255, 100, 100));
                }
            }
        });
        panel.add(btnSet);
        timingsPanel.add(panel);
        timingsPanel.setBorder(BorderFactory.createTitledBorder(b, "Timings"));
        panelSPD.add(timingsPanel);

        tabbedPane.addTab("SPD", panelSPD);
    }

    private void addXMPTab() {
        JPanel panelXMP = new JPanel();
        panelXMP.setLayout(new BoxLayout(panelXMP, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        lblXMP = new JLabel("No XMP");
        panel.add(lblXMP);
        cboXMPNum = new JComboBox<>(new Integer[]{ 0, 1 });
        // TODO
        cboXMPNum.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        panel.add(cboXMPNum);
        panelXMP.add(panel);

        panel = new JPanel();
        panel.add(new JLabel("Frequency:"));
        txtXMPFrequencyns = new JTextField(5);
        txtXMPFrequencyns.setEditable(false);
        panel.add(txtXMPFrequencyns);
        txtXMPFrequency = new JTextField(5);
        // TODO
        txtXMPFrequency.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        panel.add(txtXMPFrequency);
        panelXMP.add(panel);

        panel = new JPanel();
        rdoXMPTime = new JRadioButton("Scale from time (ns)");
        rdoXMPTime.setToolTipText("Keeps the same absolute time in ns when changing frequency.");
        rdoXMPTime.setSelected(true);
        panel.add(rdoXMPTime);
        rdoXMPCycles = new JRadioButton("Scale from cycles (ticks)");
        rdoXMPCycles.setToolTipText("Keeps the same amount of cycles in ticks when changing frequency.");
        panel.add(rdoXMPCycles);
        ButtonGroup group = new ButtonGroup();
        group.add(rdoXMPTime);
        group.add(rdoXMPCycles);
        panelXMP.add(panel);

        panel = new JPanel();
        panel.add(new JLabel("Voltage:"));
        SpinnerNumberModel model = new SpinnerNumberModel(1.20, 1.20, 2.00, 0.05);
        spnXMPVoltage = new JSpinner(model);
        ((JSpinner.DefaultEditor)spnXMPVoltage.getEditor()).getTextField().setEditable(false);
        panel.add(spnXMPVoltage);
        panelXMP.add(panel);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        XMPclCheckBoxMap = new LinkedHashMap<>();
        int cl = 4;
        for (int row = 0; row < 3; row++) {
            JPanel p = new JPanel();
            for (int col = 0; col < 5; col++) {
                if (cl > 18) break;

                JCheckBox chk = new JCheckBox(String.valueOf(cl));
                chk.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (spd == null)
                            showErrorMsg("Please open an SPD file.");
                        else if (spd.getXMP() == null)
                            showErrorMsg("No XMPs found.");
                        else {
                            // TODO: enable CL in selected XMP
                        }
                    }
                });
                p.add(chk);
                XMPclCheckBoxMap.put(cl, chk);
                cl++;
            }
            panel.add(p);
        }
        Border b = BorderFactory.createLineBorder(Color.BLACK);
        panel.setBorder(BorderFactory.createTitledBorder(b, "Supported CLs"));
        panelXMP.add(panel);

        JPanel timingsPanel = new JPanel(new GridLayout(7, 2, 10, 10));
        String[] timingNames = {
                "tCL", "tRCD", "tRP", "tRAS", "tRC","tRFC",
                "tRRD", "tFAW", "tWR", "tWTR", "tRTP", "tCWL", "tREFI"
        };
        XMPnameTextFieldMap = new LinkedHashMap<>();
        for (String name : timingNames) {
            panel = new JPanel();

            JLabel lbl = new JLabel(name + ":");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panel.add(lbl);

            JTextField txtns = new JTextField(5);
            txtns.setEditable(false);
            panel.add(txtns);
            JTextField txtTicks = new JTextField(3);
            txtTicks.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (spd == null)
                        showErrorMsg("Please open an SPD file.");
                    else if (spd.getXMP() == null)
                        showErrorMsg("No XMPs found.");
                    else {
                        boolean valid = true;
                        String s = txtTicks.getText();
                        try {
                            int t = Integer.valueOf(s);
                            if (t < 0) valid = false;
                            else if (name.equals("tCL")) {
                                if (t < 4 || t > 18) valid = false;
                                else {
                                    //spd.setTiming(name, t);
                                    if (XMPclCheckBoxMap.containsKey(t)) {
                                        XMPclCheckBoxMap.get(t).setSelected(true);
                                        // TODO: enable CL in selected XMP
                                    }
                                }
                            }
                            //else spd.setTiming(name, t);

                            //updateSPDTimingsText();
                        }
                        catch (NumberFormatException ex) {
                            valid = false;
                        }

                        if (valid)
                            txtTicks.setBackground(Color.WHITE);
                        else
                            txtTicks.setBackground(new Color(255, 100, 100));
                    }
                }
            });
            panel.add(txtTicks);
            timingsPanel.add(panel);

            XMPnameTextFieldMap.put(name, new TextFieldPair(txtns, txtTicks));
        }
        panel = new JPanel();
        JButton btnSet = new JButton("Set");
        btnSet.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");
                else if (spd.getXMP() == null)
                    showErrorMsg("No XMPs found.");
                else {
                    for (Map.Entry<String, TextFieldPair> entry : XMPnameTextFieldMap.entrySet()) {
                        JTextField txt = entry.getValue().right;
                        String name = entry.getKey(),
                                input = txt.getText();
                        boolean valid = true;
                        try {
                            int t = Integer.valueOf(input);
                            if (t < 0) valid = false;
                            else if (name.equals("tCL")) {
                                if (t < 4 || t > 18) valid = false;
                                else {
                                    //spd.setTiming(name, t);
                                    if (XMPclCheckBoxMap.containsKey(t)) {
                                        XMPclCheckBoxMap.get(t).setSelected(true);
                                        // TODO: set timing in selected XMP
                                    }
                                }
                            }
                            //else spd.setTiming(name, t);

                            //updateSPDTimingsText();
                        }
                        catch (NumberFormatException ex) {
                            valid = false;
                        }

                        if (valid)
                            txt.setBackground(Color.WHITE);
                        else
                            txt.setBackground(new Color(255, 100, 100));
                    }
                }
            }
        });
        panel.add(btnSet);
        timingsPanel.add(panel);
        timingsPanel.setBorder(BorderFactory.createTitledBorder(b, "Timings"));
        panelXMP.add(timingsPanel);

        tabbedPane.addTab("XMP", panelXMP);
    }

    private void addMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu menuFile = new JMenu("File");
        menuBar.add(menuFile);

        JMenuItem menuItemOpen = new JMenuItem("Open"),
                  menuItemSaveAs = new JMenuItem("Save as");
        menuFile.add(menuItemOpen);
        menuFile.add(menuItemSaveAs);

        ActionListener l = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(Paths.get(".").toFile());
                
                if (e.getSource() == menuItemOpen) {
                    if (fc.showOpenDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        try {
                            spd = new SPDEditor(Files.readAllBytes(file.toPath()));

                            lblSPDFile.setText(file.getName());
                            updateSPDTab();
                            updateXMPTab();
                        }
                        catch (Exception ex) {
                            spd = null;
                            showErrorMsg("Failed to read " + file.getName());
                            ex.printStackTrace();
                        }
                    }
                }
                else if (e.getSource() == menuItemSaveAs) {
                    if (spd == null) {
                        showErrorMsg("Please open an SPD file first");
                        return;
                    }

                    if (fc.showSaveDialog(getContentPane()) == 
                        JFileChooser.APPROVE_OPTION) {
                        String path = fc.getSelectedFile().getAbsolutePath();
                        if (spd.save(path))
                            showSuccessMsg("Successfully saved to " + path);
                        else showErrorMsg("Failed to save SPD file");
                    }
                }
            }
        };
        menuItemOpen.addActionListener(l);
        menuItemSaveAs.addActionListener(l);

        setJMenuBar(menuBar);
    }

    private void updateSPDTab() {
        if (spd == null) return;

        SwingUtilities.invokeLater(() -> {
            double f = spd.getFrequency();
            txtFrequencyns.setText(String.format("%.3f", 1000/f));
            txtFrequency.setText(String.format("%.2f", f));
    
            LinkedHashMap<String, Boolean> voltages = spd.getVoltages();
            for (Map.Entry<String, Boolean> e : voltages.entrySet()) {
                if (voltageCheckBoxMap.containsKey(e.getKey()))
                    voltageCheckBoxMap.get(e.getKey()).setSelected(e.getValue());
            }

            // update supported CLs
            LinkedHashMap<Integer, Boolean> cls = spd.getSupportedCLs();
            for (Map.Entry<Integer, Boolean> e : cls.entrySet()) {
                if (clCheckBoxMap.containsKey(e.getKey()))
                    clCheckBoxMap.get(e.getKey()).setSelected(e.getValue());
            }
    
            updateSPDTimingsText();
        });
    }

    private void updateSPDTimingsText() {
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<String, Integer> e : spd.getTimings().entrySet()) {
                if (nameTextFieldMap.containsKey(e.getKey())) {
                    TextFieldPair pair = nameTextFieldMap.get(e.getKey());
                    pair.left.setText(String.format("%.3f", 1000/spd.getFrequency()*e.getValue()));
                    pair.right.setText(e.getValue().toString());
                }
            }
        });
    }

    private void updateXMPTab() {
        if (spd == null) return;

        SwingUtilities.invokeLater(() -> {
            XMP xmp = spd.getXMP();
            if (xmp != null) {
                XMP.Profile[] profiles = xmp.getProfiles();
                lblXMP.setText("Found " + (profiles[1] != null ? "2 profiles." : "1 profile."));

                int num = (Integer)cboXMPNum.getSelectedItem();
                XMP.Profile selected = profiles[num];
                if (selected != null) {
                    double frequency = selected.getFrequency();
                    txtXMPFrequencyns.setText(String.format("%.3f", 1000 / frequency));
                    txtXMPFrequency.setText(String.format("%.2f", frequency));

                    double voltage = selected.getVoltage() / 100.0;
                    spnXMPVoltage.setValue(voltage);

                    LinkedHashMap<Integer, Boolean> cls = selected.getSupportedCLs();
                    for (Map.Entry<Integer, Boolean> e : cls.entrySet()) {
                        if (XMPclCheckBoxMap.containsKey(e.getKey()))
                            XMPclCheckBoxMap.get(e.getKey()).setSelected(e.getValue());
                    }

                    updateXMPTimingsText();
                }
                else lblXMP.setText("There is no profile #" + num + ".");
            }
        });
    }

    private void updateXMPTimingsText() {
        if (spd == null) return;

        SwingUtilities.invokeLater(() -> {
            XMP xmp = spd.getXMP();
            if (xmp == null) return;

            int num = (Integer)cboXMPNum.getSelectedItem();
            XMP.Profile selected = xmp.getProfiles()[num];
            if (selected == null) return;

            for (Map.Entry<String, Integer> e : selected.getTimings().entrySet()) {
                if (XMPnameTextFieldMap.containsKey(e.getKey())) {
                    TextFieldPair pair = XMPnameTextFieldMap.get(e.getKey());
                    double time = 1000 / selected.getFrequency() * e.getValue();
                    pair.left.setText(String.format("%.3f", time));
                    pair.right.setText(e.getValue().toString());
                }
            }
        });
    }

    private void showErrorMsg(String msg) {
        JOptionPane.showMessageDialog(
            this,
            msg,
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    private void showSuccessMsg(String msg) {
        JOptionPane.showMessageDialog(
            this,
            msg,
            "Success",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}

class TextFieldPair {
    public final JTextField left, right;
    public TextFieldPair(JTextField l, JTextField r) {
        left = l;
        right = r;
    }
}

class Frequency {
    public final double frequency;
    public Frequency(double f) {
        frequency = f;
    }

    @Override
    public String toString() { 
        return String.format("%.2f", frequency); 
    }
}
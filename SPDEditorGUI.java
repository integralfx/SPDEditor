import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SPDEditorGUI extends JFrame {
    private JButton btnSet, btnXMPset;
    private JComboBox<Integer> cboXMPNum;
    private JLabel lblSPDFile, lblXMP;
    private JRadioButton rdoTime, rdoCycles, rdoXMPTime, rdoXMPCycles;
    private JSpinner spnXMPVoltage;
    private JTabbedPane tabbedPane;
    private JTextField txtFrequencyns, txtFrequency, txtXMPmtbDividend, txtXMPmtbDivisor, txtXMPmtbns,
                       txtXMPFrequencyValue, txtXMPFrequencyns, txtXMPFrequency;
    private LinkedHashMap<String, TextFieldPair> timingsTextFieldMap, XMPtimingsTextFieldMap;
    private LinkedHashMap<Integer, JCheckBox> clCheckBoxMap, XMPclCheckBoxMap;
    private LinkedHashMap<String, JCheckBox> voltageCheckBoxMap;
    private SPDEditor spd;
    private final String VERSION = "Version 1.0.0", DISCORD = "∫ntegral#7834";
    private XMP xmp;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SPDEditorGUI());
    }

    public SPDEditorGUI() {
        super("SPD Editor");

        addMenuBar();

        tabbedPane = new JTabbedPane();
        addSPDTab();
        setSPDControlsEnabled(false);
        addXMPTab();
        setXMPControlsEnabled(false);
        addAboutTab();
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

        panel = new JPanel();
        panel.add(new JLabel("Frequency:"));
        txtFrequency = new JTextField(5);
        txtFrequency.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null) {
                    showErrorMsg("Please open an SPD file.");
                    return;
                }

                boolean valid = true;
                String s = txtFrequency.getText();
                try {
                    double f = Double.valueOf(s);
                    if (f < 400) valid = false;
                    else {
                        txtFrequencyns.setText(String.format("%.3f ns", 1000/f));
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
        txtFrequencyns = new JTextField(5);
        txtFrequencyns.setEditable(false);
        panel.add(txtFrequencyns);
        panelSPD.add(panel);

        panel = new JPanel();
        rdoCycles = new JRadioButton("Scale from cycles (ticks)");
        rdoCycles.setToolTipText("Keeps the same amount of cycles in ticks when changing frequency.");
        rdoCycles.setSelected(true);
        panel.add(rdoCycles);
        rdoTime = new JRadioButton("Scale from time (ns)");
        rdoTime.setToolTipText("Keeps the same absolute time in ns when changing frequency.");
        panel.add(rdoTime);
        ButtonGroup group = new ButtonGroup();
        group.add(rdoCycles);
        group.add(rdoTime);
        panelSPD.add(panel);

        panel = new JPanel();
        voltageCheckBoxMap = new LinkedHashMap<>();
        String[] voltages = { "1.25v", "1.35v", "1.50v" };
        for (String v : voltages) {
            JCheckBox chk = new JCheckBox(v);
            chk.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (spd == null) showErrorMsg("Please open an SPD file.");
                    else spd.setVoltage(v, chk.isSelected());
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
                            spd.setCLSupported(Integer.valueOf(chk.getText()), chk.isSelected());
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
        timingsTextFieldMap = new LinkedHashMap<>();
        for (String name : timingNames) {
            panel = new JPanel();

            JLabel lbl = new JLabel(name + ":");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panel.add(lbl);

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
                                    spd.setCLSupported(t, true);
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
            JTextField txtns = new JTextField(6);
            txtns.setEditable(false);
            panel.add(txtns);
            timingsPanel.add(panel);

            timingsTextFieldMap.put(name, new TextFieldPair(txtTicks, txtns));
        }
        panel = new JPanel();
        btnSet = new JButton("Set");
        btnSet.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");

                for (Map.Entry<String, TextFieldPair> entry : timingsTextFieldMap.entrySet()) {
                    JTextField txt = entry.getValue().left;
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
                                    spd.setCLSupported(t, true);
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

        // default layout manager is FlowLayout, which will only use as much space as necessary
        panel = new JPanel();
        panel.add(panelSPD);
        tabbedPane.addTab("SPD", panel);
    }

    private void addXMPTab() {
        JPanel panelXMP = new JPanel();
        panelXMP.setLayout(new BoxLayout(panelXMP, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        lblXMP = new JLabel("No XMP");
        panel.add(lblXMP);
        cboXMPNum = new JComboBox<>(new Integer[]{ 0, 1 });
        cboXMPNum.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateXMPTab();
            }
        });
        panel.add(cboXMPNum);
        panelXMP.add(panel);

        panel = new JPanel();
        panel.add(new JLabel("MTB:"));
        txtXMPmtbDividend = new JTextField(2);
        txtXMPmtbDividend.setToolTipText("Dividend for MTB.");
        panel.add(txtXMPmtbDividend);
        txtXMPmtbDivisor = new JTextField(2);
        txtXMPmtbDivisor.setToolTipText("Divisor for MTB.");
        panel.add(txtXMPmtbDivisor);
        txtXMPmtbns = new JTextField(5);
        txtXMPmtbns.setEditable(false);
        panel.add(txtXMPmtbns);
        panelXMP.add(panel);
        ActionListener l = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");
                else if (xmp == null)
                    showErrorMsg("No XMP found.");
                else {
                    int dividend = Integer.valueOf(txtXMPmtbDividend.getText()),
                        divisor = Integer.valueOf(txtXMPmtbDivisor.getText());
                    XMP.Profile selected = xmp.getProfiles()[cboXMPNum.getSelectedIndex()];
                    selected.setMTB((byte)dividend, (byte)divisor);

                    txtXMPmtbns.setText(String.format("%.3f ns", selected.getMTB().getTime()));

                    updateXMPFrequencyText();

                    updateXMPTimingsText();
                }

            }
        };
        txtXMPmtbDividend.addActionListener(l);
        txtXMPmtbDivisor.addActionListener(l);

        panel = new JPanel();
        panel.add(new JLabel("Frequency:"));
        txtXMPFrequencyValue = new JTextField(2);
        txtXMPFrequencyValue.setToolTipText("This value is multiplied by the MTB to derive the frequency.");
        txtXMPFrequencyValue.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");
                else if (xmp == null)
                    showErrorMsg("No XMP found.");
                else {
                    updateXMPFrequencyText();

                    updateXMPTimingsText();
                }
            }
        });
        panel.add(txtXMPFrequencyValue);
        txtXMPFrequencyns = new JTextField(5);
        txtXMPFrequencyns.setEditable(false);
        panel.add(txtXMPFrequencyns);
        txtXMPFrequency = new JTextField(7);
        txtXMPFrequency.setEditable(false);
        panel.add(txtXMPFrequency);
        panelXMP.add(panel);

        panel = new JPanel();
        rdoXMPCycles = new JRadioButton("Scale from cycles (ticks)");
        rdoXMPCycles.setToolTipText("Keeps the same amount of cycles in ticks when changing frequency.");
        rdoXMPCycles.setSelected(true);
        panel.add(rdoXMPCycles);
        rdoXMPTime = new JRadioButton("Scale from time (ns)");
        rdoXMPTime.setToolTipText("Keeps the same absolute time in ns when changing frequency.");
        panel.add(rdoXMPTime);
        ButtonGroup group = new ButtonGroup();
        group.add(rdoXMPTime);
        group.add(rdoXMPCycles);
        panelXMP.add(panel);

        panel = new JPanel();
        panel.add(new JLabel("Voltage:"));
        ArrayList<String> voltages = new ArrayList<>();
        for (int i = 120; i <= 200; i += 5)
            voltages.add(String.format("%.2f", i / 100.0));
        SpinnerListModel model = new SpinnerListModel(voltages.toArray(new String[0]));
        spnXMPVoltage = new JSpinner(model);
        ((JSpinner.DefaultEditor)spnXMPVoltage.getEditor()).getTextField().setEditable(false);
        spnXMPVoltage.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                XMP.Profile selected = xmp.getProfile(cboXMPNum.getSelectedIndex());
                double value = Double.valueOf((String)spnXMPVoltage.getValue());
                selected.setVoltage((int)Math.round(100 * value));
            }
        });
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
                            int num = (Integer)cboXMPNum.getSelectedItem();
                            XMP.Profile selected = spd.getXMP().getProfiles()[num];
                            if (selected != null)
                                selected.setCLSupported(Integer.valueOf(chk.getText()), chk.isSelected());
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
        XMPtimingsTextFieldMap = new LinkedHashMap<>();
        for (String name : timingNames) {
            panel = new JPanel();

            JLabel lbl = new JLabel(name + ":");
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panel.add(lbl);

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
                        int num = (Integer)cboXMPNum.getSelectedItem();
                        XMP.Profile selected = spd.getXMP().getProfiles()[num];

                        if (selected == null) return;
                        try {
                            int t = Integer.valueOf(s);
                            if (t < 0) valid = false;
                            else if (name.equals("tCL")) {
                                if (t < 4 || t > 18) valid = false;
                                else {
                                    selected.setTiming(name, t);
                                    if (XMPclCheckBoxMap.containsKey(t)) {
                                        XMPclCheckBoxMap.get(t).setSelected(true);
                                        selected.setCLSupported(t, true);
                                    }
                                }
                            }
                            else selected.setTiming(name, t);

                            updateXMPTimingsText();
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
            JTextField txtns = new JTextField(6);
            txtns.setEditable(false);
            panel.add(txtns);
            timingsPanel.add(panel);

            XMPtimingsTextFieldMap.put(name, new TextFieldPair(txtTicks, txtns));
        }
        panel = new JPanel();
        btnXMPset = new JButton("Set");
        btnXMPset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.");
                else if (spd.getXMP() == null)
                    showErrorMsg("No XMPs found.");
                else {
                    for (Map.Entry<String, TextFieldPair> entry : XMPtimingsTextFieldMap.entrySet()) {
                        JTextField txt = entry.getValue().left;
                        String name = entry.getKey(),
                                input = txt.getText();
                        boolean valid = true;
                        int num = (Integer)cboXMPNum.getSelectedItem();
                        XMP.Profile selected = spd.getXMP().getProfiles()[num];

                        try {
                            int t = Integer.valueOf(input);
                            if (t < 0) valid = false;
                            else if (name.equals("tCL")) {
                                if (t < 4 || t > 18) valid = false;
                                else {
                                    selected.setTiming(name, t);
                                    if (XMPclCheckBoxMap.containsKey(t)) {
                                        XMPclCheckBoxMap.get(t).setSelected(true);
                                        selected.setCLSupported(t, true);
                                    }
                                }
                            }
                            else selected.setTiming(name, t);

                            updateXMPTimingsText();
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
        panel.add(btnXMPset);
        timingsPanel.add(panel);
        timingsPanel.setBorder(BorderFactory.createTitledBorder(b, "Timings"));
        panelXMP.add(timingsPanel);

        tabbedPane.addTab("XMP", panelXMP);
    }

    private void addAboutTab() {
        JPanel panelAbout = new JPanel();
        panelAbout.setLayout(new BoxLayout(panelAbout, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.add(new JLabel(VERSION));
        panelAbout.add(panel);

        panel = new JPanel();
        panel.add(new JLabel("Discord:"));
        JTextField txtDiscord = new JTextField(DISCORD);
        txtDiscord.setEditable(false);
        panel.add(txtDiscord);
        panelAbout.add(panel);

        // wrap in a GridBagLayout to centre content
        panel = new JPanel(new GridBagLayout());
        panel.add(panelAbout);
        tabbedPane.addTab("About", panel);
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
                            xmp = spd.getXMP();

                            lblSPDFile.setText(file.getName());
                            updateSPDTab();
                            updateXMPTab();
                        }
                        catch (Exception ex) {
                            spd = null;
                            xmp = null;
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

                    if (fc.showSaveDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();

                        if (file.exists()) {
                            int option = JOptionPane.showConfirmDialog(
                                SPDEditorGUI.this,
                                "Are you sure you want to overwrite " + file.getName() + "?",
                                "Overwrite",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            );

                            switch (option) {
                                case JOptionPane.CANCEL_OPTION:
                                case JOptionPane.CLOSED_OPTION:
                                case JOptionPane.NO_OPTION:
                                    return;
                            }
                        }

                        if (spd.save(file.getAbsolutePath()))
                            showSuccessMsg("Successfully saved to " + file.getAbsolutePath());
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
            setSPDControlsEnabled(true);

            double f = spd.getFrequency();
            txtFrequencyns.setText(String.format("%.3f ns", 1000/f));
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
                if (timingsTextFieldMap.containsKey(e.getKey())) {
                    TextFieldPair pair = timingsTextFieldMap.get(e.getKey());
                    pair.left.setText(e.getValue().toString());
                    pair.right.setText(String.format("%.3f ns", 1000/spd.getFrequency()*e.getValue()));
                }
            }
        });
    }

    private void updateXMPTab() {
        if (spd == null) return;

        SwingUtilities.invokeLater(() -> {
            if (xmp != null) {
                XMP.Profile[] profiles = xmp.getProfiles();
                lblXMP.setText("Found " + (profiles[1] != null ? "2 profiles." : "1 profile."));

                int num = (Integer)cboXMPNum.getSelectedItem();
                XMP.Profile selected = profiles[num];
                if (selected != null) {
                    setXMPControlsEnabled(true);

                    XMP.MTB mtb = selected.getMTB();
                    txtXMPmtbDividend.setText(String.valueOf(mtb.dividend));
                    txtXMPmtbDivisor.setText(String.valueOf(mtb.divisor));
                    txtXMPmtbns.setText(String.format("%.3f ns", mtb.getTime()));

                    double frequency = selected.getFrequency();
                    txtXMPFrequencyValue.setText(String.valueOf(Byte.toUnsignedInt(selected.gettCKmin())));
                    txtXMPFrequency.setText(String.format("%.2f MHz", frequency));
                    txtXMPFrequencyns.setText(String.format("%.3f ns", 1000 / frequency));

                    double voltage = selected.getVoltage() / 100.0;
                    spnXMPVoltage.setValue(String.format("%.2f", voltage));

                    LinkedHashMap<Integer, Boolean> cls = selected.getSupportedCLs();
                    for (Map.Entry<Integer, Boolean> e : cls.entrySet()) {
                        if (XMPclCheckBoxMap.containsKey(e.getKey()))
                            XMPclCheckBoxMap.get(e.getKey()).setSelected(e.getValue());
                    }

                    updateXMPTimingsText();
                }
                else {
                    lblXMP.setText("There is no profile #" + num + ".");
                    setXMPControlsEnabled(false);
                }
            }
            else {
                lblXMP.setText("No XMP found.");
                setXMPControlsEnabled(false);
            }
        });
    }

    private void setSPDControlsEnabled(boolean enable) {
        SwingUtilities.invokeLater(() -> {
            txtFrequency.setEnabled(enable);
            rdoCycles.setEnabled(enable);
            rdoTime.setEnabled(enable);

            for (Map.Entry<String, JCheckBox> e : voltageCheckBoxMap.entrySet())
                e.getValue().setEnabled(enable);

            for (Map.Entry<Integer, JCheckBox> e : clCheckBoxMap.entrySet())
                e.getValue().setEnabled(enable);

            for (Map.Entry<String, TextFieldPair> e : timingsTextFieldMap.entrySet())
                e.getValue().left.setEnabled(enable);

            btnSet.setEnabled(enable);
        });
    }

    private void setXMPControlsEnabled(boolean enable) {
        SwingUtilities.invokeLater(() -> {
            cboXMPNum.setEnabled(enable);

            txtXMPmtbDividend.setEnabled(enable);
            txtXMPmtbDivisor.setEnabled(enable);
            txtXMPmtbDividend.setEnabled(enable);

            txtXMPFrequencyValue.setEnabled(enable);
            txtXMPFrequencyns.setEnabled(enable);
            txtXMPFrequency.setEnabled(enable);

            rdoXMPTime.setEnabled(enable);
            rdoXMPCycles.setEnabled(enable);

            spnXMPVoltage.setEnabled(enable);

            for (Map.Entry<Integer, JCheckBox> e : XMPclCheckBoxMap.entrySet())
                e.getValue().setEnabled(enable);

            for (Map.Entry<String, TextFieldPair> e : XMPtimingsTextFieldMap.entrySet()) {
                e.getValue().left.setEnabled(enable);
                e.getValue().right.setEnabled(enable);
            }

            btnXMPset.setEnabled(enable);
        });
    }

    private void updateXMPFrequencyText() {
        if (spd == null || xmp == null) return;

        SwingUtilities.invokeLater(() -> {
            int input = Integer.valueOf(txtXMPFrequencyValue.getText()),
                num = cboXMPNum.getSelectedIndex();
            XMP.Profile selected = xmp.getProfiles()[num];

            if (rdoCycles.isSelected()) {
                LinkedHashMap<String, Integer> t = selected.getTimings();
                selected.settCKmin((byte)input);
                selected.setTimings(t);
            }
            else if (rdoTime.isSelected()) {
                selected.settCKmin((byte)input);
            }

            double freqns = input * selected.getMTB().getTime();
            txtXMPFrequency.setText(String.format("%.2f MHz", 1000 / freqns));
            txtXMPFrequencyns.setText(String.format("%.3f ns", freqns));
        });
    }

    private void updateXMPTimingsText() {
        if (spd == null || xmp == null) return;

        SwingUtilities.invokeLater(() -> {
            int num = (Integer)cboXMPNum.getSelectedItem();
            XMP.Profile selected = xmp.getProfiles()[num];
            if (selected == null) return;

            for (Map.Entry<String, Integer> e : selected.getTimings().entrySet()) {
                if (XMPtimingsTextFieldMap.containsKey(e.getKey())) {
                    TextFieldPair pair = XMPtimingsTextFieldMap.get(e.getKey());
                    double time = 1000 / selected.getFrequency() * e.getValue();
                    pair.left.setText(e.getValue().toString());
                    pair.right.setText(String.format("%.3f ns", time));
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
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

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

public class SPDEditorGUI extends JFrame {
    private SPDEditor spd;
    private JLabel lblSPDFile;
    private JRadioButton rdoTime, rdoCycles;
    private JTextField txtFrequencyns, txtFrequency;
    private LinkedHashMap<String, TextFieldPair> nameTextFieldMap;
    private LinkedHashMap<Integer, JCheckBox> clCheckBoxMap;
    private LinkedHashMap<String, JCheckBox> voltageCheckBoxMap;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SPDEditorGUI());
    }

    public SPDEditorGUI() {
        super("SPD Editor");

        addMenuBar();

        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        lblSPDFile = new JLabel("No SPD file opened");
        panel.add(lblSPDFile);
        add(panel);

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

                        updateTimingsText();
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
        add(panel);

        panel = new JPanel();
        rdoTime = new JRadioButton("Scale from time (ns)");
        rdoTime.setToolTipText("Keeps the same absolute time in ns when " + 
                               "changing frequency.");
        rdoCycles = new JRadioButton("Scale from cycles (ticks)");
        rdoCycles.setToolTipText("Keeps the same amount of cycles in ticks " + 
                                 "when changing frequency.");
        rdoTime.setSelected(true);
        ButtonGroup group = new ButtonGroup();
        group.add(rdoTime);
        group.add(rdoCycles);
        panel.add(rdoTime);
        panel.add(rdoCycles);
        add(panel);

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
        add(panel);

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
        add(panel);

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

                        updateTimingsText();
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

                        updateTimingsText();
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
        add(timingsPanel);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
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
                            spd = new SPDEditor(
                                Files.readAllBytes(file.toPath())
                            );

                            lblSPDFile.setText(file.getName());
                            updateGUI();
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

    private void updateGUI() {
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
    
            updateTimingsText();
        });
    }

    private void updateTimingsText() {
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<String, Integer> e : spd.getTimings().entrySet()) {
                if (nameTextFieldMap.containsKey(e.getKey())) {
                    TextFieldPair pair = nameTextFieldMap.get(e.getKey());
                    pair.left.setText(String.format("%.3f", 1000/spd.getFrequency()*e.getValue()));
                    pair.right.setText("" + e.getValue());
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
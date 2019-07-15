import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.ArrayList
import java.util.LinkedHashMap

import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.round

class SPDEditorGUI : JFrame("SPD Editor") {
    private val btnSet = JButton("Set")
    private val btnXMPset = JButton("Set")
    private val cboXMPNum = JComboBox(arrayOf(0, 1))
    private val lblSPDFile = JLabel("No SPD file opened")
    private val lblXMP = JLabel("No XMP")
    private val rdoTime = JRadioButton("Scale from time (ns)")
    private val rdoCycles = JRadioButton("Scale from cycles (ticks)")
    private val rdoXMPTime = JRadioButton("Scale from time (ns)")
    private val rdoXMPCycles = JRadioButton("Scale from cycles (ticks)")
    private lateinit var spnXMPVoltageModel: SpinnerListModel
    private lateinit var spnXMPVoltage: JSpinner
    private val tabbedPane: JTabbedPane
    private val txtFrequencyns = JTextField(5)
    private val txtFrequency = JTextField(5)
    private val txtXMPmtbDividend = JTextField(2)
    private val txtXMPmtbDivisor = JTextField(2)
    private val txtXMPmtbns = JTextField(5)
    private val txtXMPFrequencyValue = JTextField(2)
    private val txtXMPFrequencyns = JTextField(5)
    private val txtXMPFrequency = JTextField(7)
    private val timingsTextFieldMap = LinkedHashMap<String, TextFieldPair>()
    private val XMPtimingsTextFieldMap = LinkedHashMap<String, TextFieldPair>()
    private val clCheckBoxMap = LinkedHashMap<UInt, JCheckBox>()
    private val XMPclCheckBoxMap = LinkedHashMap<UInt, JCheckBox>()
    private val voltageCheckBoxMap = LinkedHashMap<String, JCheckBox>()
    private var spd: SPDEditor? = null
    private var xmp: XMP? = null

    companion object {
        private const val VERSION = "Version 1.0.0"
        private const val DISCORD = "∫ntegral#7834"

        @JvmStatic
        fun main(args: Array<String>) {
            SwingUtilities.invokeLater { SPDEditorGUI() }
        }
    }

    init {
        addMenuBar()

        tabbedPane = JTabbedPane()
        addSPDTab()
        setSPDControlsEnabled(false)
        addXMPTab()
        setXMPControlsEnabled(false)
        addAboutTab()
        add(tabbedPane)

        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        pack()
        setLocationRelativeTo(null)
        isResizable = false
        isVisible = true
    }

    private fun addSPDTab() {
        val panelSPD = JPanel()
        panelSPD.layout = BoxLayout(panelSPD, BoxLayout.Y_AXIS)

        var panel = JPanel()
        panel.add(lblSPDFile)
        panelSPD.add(panel)

        panel = JPanel()
        panel.add(JLabel("Frequency:"))
        txtFrequency.addActionListener(ActionListener {
            if (spd == null) {
                showErrorMsg("Please open an SPD file.")
                return@ActionListener
            }

            var valid = true
            val s = txtFrequency.text
            try {
                val f = java.lang.Double.valueOf(s)
                if (f < 400)
                    valid = false
                else {
                    txtFrequencyns.text = String.format("%.3f ns", 1000 / f)
                    /*
                     * Timings are calculated from the cycle time (ns),
                     * which don't change when changing frequency.
                     * Thus, we have to save the timings, update the
                     * frequency, then update with the timings that we have
                     * saved.
                     */
                    if (rdoCycles.isSelected) {
                        val t = spd!!.timings
                        spd!!.frequency = f
                        spd!!.timings = t
                    }
                    else if (rdoTime.isSelected) {
                        spd!!.frequency = f
                    }

                    updateSPDTimingsText()
                }
            } catch (ex: NumberFormatException) {
                valid = false
            }

            if (valid)
                txtFrequency.background = Color.WHITE
            else
                txtFrequency.background = Color(255, 100, 100)
        })
        panel.add(txtFrequency)
        txtFrequencyns.isEditable = false
        panel.add(txtFrequencyns)
        panelSPD.add(panel)

        panel = JPanel()
        rdoCycles.toolTipText = "Keeps the same amount of cycles in ticks when changing frequency."
        rdoCycles.isSelected = true
        panel.add(rdoCycles)
        rdoTime.toolTipText = "Keeps the same absolute time in ns when changing frequency."
        panel.add(rdoTime)
        val group = ButtonGroup()
        group.add(rdoCycles)
        group.add(rdoTime)
        panelSPD.add(panel)

        panel = JPanel()
        val voltages = arrayOf("1.25v", "1.35v", "1.50v")
        for (v in voltages) {
            val chk = JCheckBox(v)
            chk.addActionListener {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.")
                else
                    spd!!.setVoltage(v, chk.isSelected)
            }
            panel.add(chk)
            voltageCheckBoxMap[v] = chk
        }
        panelSPD.add(panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        var cl = 4u
        for (row in 0..2) {
            val p = JPanel()
            for (col in 0..4) {
                if (cl > 18u) break

                val chk = JCheckBox(cl.toString())
                chk.addActionListener {
                    if (spd == null)
                        showErrorMsg("Please open an SPD file.")
                    else
                        spd!!.setCLSupported(chk.text.toUInt(), chk.isSelected)
                }
                p.add(chk)
                clCheckBoxMap[cl] = chk
                cl++
            }
            panel.add(p)
        }
        val b = BorderFactory.createLineBorder(Color.BLACK)
        panel.border = BorderFactory.createTitledBorder(b, "Supported CLs")
        panelSPD.add(panel)

        val timingsPanel = JPanel(GridLayout(6, 2, 10, 10))
        val timingNames = arrayOf("tCL", "tRCD", "tRP", "tRAS", "tRC", "tRFC", "tRRD", "tFAW", "tWR", "tWTR", "tRTP")
        for (name in timingNames) {
            panel = JPanel()

            val lbl = JLabel("$name:")
            lbl.font = lbl.font.deriveFont(Font.BOLD)
            panel.add(lbl)

            val txtTicks = JTextField(3)
            txtTicks.addActionListener {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.")

                var valid = true
                val s = txtTicks.text
                try {
                    val t = s.toUInt()
                    if (t < 0u)
                        valid = false
                    else if (name == "tCL") {
                        if (t < 4u || t > 18u)
                            valid = false
                        else {
                            spd!!.setTiming(name, t)
                            if (clCheckBoxMap.containsKey(t)) {
                                clCheckBoxMap[t]!!.isSelected = true
                                spd!!.setCLSupported(t, true)
                            }
                        }
                    } else
                        spd!!.setTiming(name, t)

                    updateSPDTimingsText()
                } catch (ex: NumberFormatException) {
                    valid = false
                }

                if (valid)
                    txtTicks.background = Color.WHITE
                else
                    txtTicks.background = Color(255, 100, 100)
            }
            panel.add(txtTicks)
            val txtns = JTextField(6)
            txtns.isEditable = false
            panel.add(txtns)
            timingsPanel.add(panel)

            timingsTextFieldMap[name] = TextFieldPair(txtTicks, txtns)
        }
        panel = JPanel()
        btnSet.addActionListener {
            if (spd == null)
                showErrorMsg("Please open an SPD file.")

            for ((name, value) in timingsTextFieldMap) {
                val txt = value.left
                val input = txt.text
                var valid = true
                try {
                    val t = input.toUInt()
                    if (t < 0u)
                        valid = false
                    else if (name == "tCL") {
                        if (t < 4u || t > 18u)
                            valid = false
                        else {
                            spd!!.setTiming(name, t)
                            if (clCheckBoxMap.containsKey(t)) {
                                clCheckBoxMap[t]!!.isSelected = true
                                spd!!.setCLSupported(t, true)
                            }
                        }
                    } else spd!!.setTiming(name, t)

                    updateSPDTimingsText()
                }
                catch (ex: NumberFormatException) {
                    valid = false
                }

                if (valid)
                    txt.background = Color.WHITE
                else
                    txt.background = Color(255, 100, 100)
            }
        }
        panel.add(btnSet)
        timingsPanel.add(panel)
        timingsPanel.border = BorderFactory.createTitledBorder(b, "Timings")
        panelSPD.add(timingsPanel)

        // default layout manager is FlowLayout, which will only use as much space as necessary
        panel = JPanel()
        panel.add(panelSPD)
        tabbedPane.addTab("SPD", panel)
    }

    private fun addXMPTab() {
        val panelXMP = JPanel()
        panelXMP.layout = BoxLayout(panelXMP, BoxLayout.Y_AXIS)

        var panel = JPanel()
        panel.add(lblXMP)
        cboXMPNum.addActionListener { updateXMPTab() }
        panel.add(cboXMPNum)
        panelXMP.add(panel)

        panel = JPanel()
        panel.add(JLabel("MTB:"))
        txtXMPmtbDividend.toolTipText = "Dividend for MTB."
        panel.add(txtXMPmtbDividend)
        txtXMPmtbDivisor.toolTipText = "Divisor for MTB."
        panel.add(txtXMPmtbDivisor)
        txtXMPmtbns.isEditable = false
        panel.add(txtXMPmtbns)
        panelXMP.add(panel)
        val l = ActionListener {
            if (spd == null)
                showErrorMsg("Please open an SPD file.")
            else if (xmp == null)
                showErrorMsg("No XMP found.")
            else {
                updateXMPFrequencyText()

                updateXMPTimingsText()
            }
        }
        txtXMPmtbDividend.addActionListener(l)
        txtXMPmtbDivisor.addActionListener(l)

        panel = JPanel()
        panel.add(JLabel("Frequency:"))
        txtXMPFrequencyValue.toolTipText = "This value is multiplied by the MTB to derive the frequency."
        txtXMPFrequencyValue.addActionListener {
            if (spd == null)
                showErrorMsg("Please open an SPD file.")
            else if (xmp == null)
                showErrorMsg("No XMP found.")
            else {
                updateXMPFrequencyText()

                updateXMPTimingsText()
            }
        }
        panel.add(txtXMPFrequencyValue)
        txtXMPFrequencyns.isEditable = false
        panel.add(txtXMPFrequencyns)
        txtXMPFrequency.isEditable = false
        panel.add(txtXMPFrequency)
        panelXMP.add(panel)

        panel = JPanel()
        rdoXMPCycles.toolTipText = "Keeps the same amount of cycles in ticks when changing frequency."
        rdoXMPCycles.isSelected = true
        panel.add(rdoXMPCycles)
        rdoXMPTime.toolTipText = "Keeps the same absolute time in ns when changing frequency."
        panel.add(rdoXMPTime)
        val group = ButtonGroup()
        group.add(rdoXMPTime)
        group.add(rdoXMPCycles)
        panelXMP.add(panel)

        panel = JPanel()
        panel.add(JLabel("Voltage:"))
        val voltages = ArrayList<String>()
        var i = 120
        while (i <= 200) {
            voltages.add(String.format("%.2f", i / 100.0))
            i += 5
        }
        spnXMPVoltageModel = SpinnerListModel(voltages.toTypedArray())
        spnXMPVoltage = JSpinner(spnXMPVoltageModel)
        (spnXMPVoltage.editor as JSpinner.DefaultEditor).textField.isEditable = false
        spnXMPVoltage.addChangeListener {
            val selected = xmp!!.profile!![cboXMPNum.selectedIndex]!!
            val value = java.lang.Double.valueOf(spnXMPVoltage.value as String)
            selected.voltage = round(100 * value).toInt().toUInt()
        }
        panel.add(spnXMPVoltage)
        panelXMP.add(panel)

        panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        var cl = 4u
        for (row in 0..2) {
            val p = JPanel()
            for (col in 0..4) {
                if (cl > 18u) break

                val chk = JCheckBox(cl.toString())
                chk.addActionListener {
                    if (spd == null)
                        showErrorMsg("Please open an SPD file.")
                    else if (spd!!.xmp == null)
                        showErrorMsg("No XMPs found.")
                    else {
                        val num = cboXMPNum.selectedItem as Int
                        val selected = spd!!.xmp!!.profile!![num]
                        selected?.setCLSupported(chk.text.toUInt(), chk.isSelected)
                    }
                }
                p.add(chk)
                XMPclCheckBoxMap[cl] = chk
                cl++
            }
            panel.add(p)
        }
        val b = BorderFactory.createLineBorder(Color.BLACK)
        panel.border = BorderFactory.createTitledBorder(b, "Supported CLs")
        panelXMP.add(panel)

        val timingsPanel = JPanel(GridLayout(7, 2, 10, 10))
        val timingNames = arrayOf("tCL", "tRCD", "tRP", "tRAS", "tRC", "tRFC", "tRRD", "tFAW", "tWR", "tWTR", "tRTP", "tCWL", "tREFI")
        for (name in timingNames) {
            panel = JPanel()

            val lbl = JLabel("$name:")
            lbl.font = lbl.font.deriveFont(Font.BOLD)
            panel.add(lbl)

            val txtTicks = JTextField(3)
            txtTicks.addActionListener(ActionListener {
                if (spd == null)
                    showErrorMsg("Please open an SPD file.")
                else if (spd!!.xmp == null)
                    showErrorMsg("No XMPs found.")
                else {
                    var valid = true
                    val s = txtTicks.text
                    val num = cboXMPNum.selectedItem as Int
                    val selected = spd!!.xmp!!.profile!![num] ?: return@ActionListener

                    try {
                        val t = s.toUInt()
                        if (t < 0u)
                            valid = false
                        else if (name == "tCL") {
                            if (t < 4u || t > 18u)
                                valid = false
                            else {
                                selected.setTiming(name, t)
                                if (XMPclCheckBoxMap.containsKey(t)) {
                                    XMPclCheckBoxMap[t]!!.isSelected = true
                                    selected.setCLSupported(t, true)
                                }
                            }
                        } else
                            selected.setTiming(name, t)

                        updateXMPTimingsText()
                    } catch (ex: NumberFormatException) {
                        valid = false
                    }

                    if (valid)
                        txtTicks.background = Color.WHITE
                    else
                        txtTicks.background = Color(255, 100, 100)
                }
            })
            panel.add(txtTicks)
            val txtns = JTextField(6)
            txtns.isEditable = false
            panel.add(txtns)
            timingsPanel.add(panel)

            XMPtimingsTextFieldMap[name] = TextFieldPair(txtTicks, txtns)
        }
        panel = JPanel()
        btnXMPset.addActionListener {
            if (spd == null)
                showErrorMsg("Please open an SPD file.")
            else if (spd!!.xmp == null)
                showErrorMsg("No XMPs found.")
            else {
                for ((name, value) in XMPtimingsTextFieldMap) {
                    val txt = value.left
                    val input = txt.text
                    var valid = true
                    val num = cboXMPNum.selectedItem as Int
                    val selected = spd!!.xmp!!.profile!![num]

                    try {
                        val t = input.toUInt()
                        if (t < 0u)
                            valid = false
                        else if (name == "tCL") {
                            if (t < 4u || t > 18u)
                                valid = false
                            else {
                                selected!!.setTiming(name, t)
                                if (XMPclCheckBoxMap.containsKey(t)) {
                                    XMPclCheckBoxMap[t]!!.isSelected = true
                                    selected.setCLSupported(t, true)
                                }
                            }
                        } else
                            selected!!.setTiming(name, t)

                        updateXMPTimingsText()
                    } catch (ex: NumberFormatException) {
                        valid = false
                    }

                    if (valid)
                        txt.background = Color.WHITE
                    else
                        txt.background = Color(255, 100, 100)
                }
            }
        }
        panel.add(btnXMPset)
        timingsPanel.add(panel)
        timingsPanel.border = BorderFactory.createTitledBorder(b, "Timings")
        panelXMP.add(timingsPanel)

        tabbedPane.addTab("XMP", panelXMP)
    }

    private fun addAboutTab() {
        val panelAbout = JPanel()
        panelAbout.layout = BoxLayout(panelAbout, BoxLayout.Y_AXIS)

        var panel = JPanel()
        panel.add(JLabel(VERSION))
        panelAbout.add(panel)

        panel = JPanel()
        panel.add(JLabel("Discord:"))
        val txtDiscord = JTextField(DISCORD)
        txtDiscord.isEditable = false
        panel.add(txtDiscord)
        panelAbout.add(panel)

        // wrap in a GridBagLayout to centre content
        panel = JPanel(GridBagLayout())
        panel.add(panelAbout)
        tabbedPane.addTab("About", panel)
    }

    private fun addMenuBar() {
        val menuBar = JMenuBar()

        val menuFile = JMenu("File")
        menuBar.add(menuFile)

        val menuItemOpen = JMenuItem("Open")
        val menuItemSaveAs = JMenuItem("Save as")
        menuFile.add(menuItemOpen)
        menuFile.add(menuItemSaveAs)

        val l = ActionListener { e ->
            val fc = JFileChooser(Paths.get(".").toFile())

            when (e.source) {
                menuItemOpen -> {
                    if (fc.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                        val file = fc.selectedFile
                        try {
                            spd = SPDEditor(Files.readAllBytes(file.toPath()).toUByteArray())
                            xmp = spd!!.xmp

                            lblSPDFile.text = file.name
                            updateSPDTab()
                            updateXMPTab()
                        }
                        catch (ex: Exception) {
                            spd = null
                            xmp = null
                            showErrorMsg("Failed to read " + file.name)
                            ex.printStackTrace()
                        }
                    }
                }
                menuItemSaveAs -> {
                    if (spd == null) {
                        showErrorMsg("Please open an SPD file first")
                        return@ActionListener
                    }

                    if (fc.showSaveDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                        val file = fc.selectedFile

                        if (file.exists()) {
                            val option = JOptionPane.showConfirmDialog(
                                    this@SPDEditorGUI,
                                    "Are you sure you want to overwrite " + file.name + "?",
                                    "Overwrite",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            )

                            when (option) {
                                JOptionPane.CANCEL_OPTION, JOptionPane.CLOSED_OPTION, JOptionPane.NO_OPTION -> return@ActionListener
                            }
                        }

                        if (spd!!.save(file.absolutePath))
                            showSuccessMsg("Successfully saved to " + file.absolutePath)
                        else
                            showErrorMsg("Failed to save SPD file")
                    }
                }
            }
        }
        menuItemOpen.addActionListener(l)
        menuItemSaveAs.addActionListener(l)

        jMenuBar = menuBar
    }

    private fun updateSPDTab() {
        if (spd == null) return

        SwingUtilities.invokeLater {
            setSPDControlsEnabled(true)

            val f = spd!!.frequency
            txtFrequencyns.text = String.format("%.3f ns", 1000 / f)
            txtFrequency.text = String.format("%.2f", f)

            val voltages = spd!!.voltages
            for ((key, value) in voltages) {
                if (voltageCheckBoxMap.containsKey(key))
                    voltageCheckBoxMap[key]!!.setSelected(value)
            }

            // update supported CLs
            val cls = spd!!.supportedCLs
            for ((key, value) in cls) {
                if (clCheckBoxMap.containsKey(key))
                    clCheckBoxMap[key]!!.setSelected(value)
            }

            updateSPDTimingsText()
        }
    }

    private fun updateSPDTimingsText() {
        SwingUtilities.invokeLater {
            for ((key, value) in spd!!.timings) {
                if (timingsTextFieldMap.containsKey(key)) {
                    val pair = timingsTextFieldMap[key]!!
                    pair.left.text = value.toString()
                    pair.right.text = String.format("%.3f ns", 1000 / spd!!.frequency * value.toInt())
                }
            }
        }
    }

    private fun updateXMPTab() {
        if (spd == null) return

        SwingUtilities.invokeLater {
            if (xmp != null) {
                val profiles = xmp!!.profile!!
                lblXMP.text = "Found " + if (profiles[1] != null) "2 profiles." else "1 profile."

                val num = cboXMPNum.selectedItem as Int
                val selected = profiles[num]
                if (selected != null) {
                    setXMPControlsEnabled(true)

                    val mtb = selected.mtb
                    txtXMPmtbDividend.text = mtb.dividend.toString()
                    txtXMPmtbDivisor.text = mtb.divisor.toString()
                    txtXMPmtbns.text = String.format("%.3f ns", mtb.getTime())

                    val frequency = selected.getFrequency()
                    txtXMPFrequencyValue.text = selected.tCKmin.toString()
                    txtXMPFrequency.text = String.format("%.2f MHz", frequency)
                    txtXMPFrequencyns.text = String.format("%.3f ns", 1000 / frequency)

                    val voltage = selected.voltage.toInt() / 100.0
                    spnXMPVoltage.value = String.format("%.2f", voltage)

                    val cls = selected.supportedCLs
                    for (e in cls.entries) {
                        if (XMPclCheckBoxMap.containsKey(e.key))
                            XMPclCheckBoxMap[e.key]!!.setSelected(e.value)
                    }

                    updateXMPTimingsText()
                } else {
                    lblXMP.text = "There is no profile #$num."
                    setXMPControlsEnabled(false)
                }
            } else {
                lblXMP.text = "No XMP found."
                setXMPControlsEnabled(false)
            }
        }
    }

    private fun setSPDControlsEnabled(enable: Boolean) {
        SwingUtilities.invokeLater {
            txtFrequency.isEnabled = enable
            rdoCycles.isEnabled = enable
            rdoTime.isEnabled = enable

            for ((_, value) in voltageCheckBoxMap)
                value.isEnabled = enable

            for ((_, value) in clCheckBoxMap)
                value.isEnabled = enable

            for ((_, value) in timingsTextFieldMap)
                value.left.isEnabled = enable

            btnSet.isEnabled = enable
        }
    }

    private fun setXMPControlsEnabled(enable: Boolean) {
        SwingUtilities.invokeLater {
            txtXMPmtbDivisor.isEnabled = enable
            txtXMPmtbDividend.isEnabled = enable
            txtXMPmtbns.isEnabled = enable

            txtXMPFrequencyValue.isEnabled = enable
            txtXMPFrequencyns.isEnabled = enable
            txtXMPFrequency.isEnabled = enable

            rdoXMPTime.isEnabled = enable
            rdoXMPCycles.isEnabled = enable

            spnXMPVoltage.isEnabled = enable

            for ((_, value) in XMPclCheckBoxMap)
                value.isEnabled = enable

            for ((_, value) in XMPtimingsTextFieldMap) {
                value.left.isEnabled = enable
                value.right.isEnabled = enable
            }

            btnXMPset.isEnabled = enable
        }
    }

    private fun updateXMPFrequencyText() {
        if (spd == null || xmp == null) return

        SwingUtilities.invokeLater {
            val input = Integer.valueOf(txtXMPFrequencyValue.text)
            val dividend = Integer.valueOf(txtXMPmtbDividend.text)
            val divisor = Integer.valueOf(txtXMPmtbDivisor.text)
            val selected = xmp!!.profile!![cboXMPNum.selectedIndex]

            selected!!.mtb = XMP.MTB(dividend.toUByte(), divisor.toUByte())
            txtXMPmtbns.text = String.format("%.3f ns", selected.mtb.getTime())

            if (rdoCycles.isSelected) {
                val t = selected.getTimings()
                selected.tCKmin = input.toUByte()
                selected.setTimings(t)
            }
            else if (rdoTime.isSelected) {
                selected.tCKmin = input.toUByte()
            }

            val freqns = input * selected.mtb.getTime()
            txtXMPFrequency.text = String.format("%.2f MHz", 1000 / freqns)
            txtXMPFrequencyns.text = String.format("%.3f ns", freqns)
        }
    }

    private fun updateXMPTimingsText() {
        if (spd == null || xmp == null) return

        SwingUtilities.invokeLater {
            val num = cboXMPNum.selectedItem as Int
            val selected = xmp!!.profile!![num]
            if (selected != null) {
                for (e in selected.getTimings().entries) {
                    if (XMPtimingsTextFieldMap.containsKey(e.key)) {
                        val pair = XMPtimingsTextFieldMap[e.key]
                        val time = 1000 / selected.getFrequency() * e.value.toInt()
                        pair!!.left.text = e.value.toString()
                        pair.right.text = String.format("%.3f ns", time)
                    }
                }
            }
        }
    }

    private fun showErrorMsg(msg: String) {
        JOptionPane.showMessageDialog(
                this,
                msg,
                "Error",
                JOptionPane.ERROR_MESSAGE
        )
    }

    private fun showSuccessMsg(msg: String) {
        JOptionPane.showMessageDialog(
                this,
                msg,
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        )
    }
}

internal class TextFieldPair(val left: JTextField, val right: JTextField)
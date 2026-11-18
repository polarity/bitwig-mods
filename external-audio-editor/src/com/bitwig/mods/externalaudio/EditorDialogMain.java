package com.bitwig.mods.externalaudio;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Standalone UI process. Must never reference Bitwig or its headless JVM's mod classes. */
public final class EditorDialogMain {
    private EditorDialogMain() {}

    public static void main(String[] args) {
        try {
            String current = args.length > 0 ? args[0] : "";
            boolean openAfter = args.length > 1 && Boolean.parseBoolean(args[1]);
            SwingUtilities.invokeAndWait(() -> build(current, openAfter));
        } catch (Exception | LinkageError error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void build(String current, boolean openAfter) {
        JDialog window = new JDialog((Frame) null, "External Audio Editor", Dialog.ModalityType.MODELESS);
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { System.exit(0); }
        });
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.add(new JLabel("Full path to an editor executable or macOS .app (without quotes):"), BorderLayout.NORTH);
        JTextField path = new JTextField(current, 48);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose an audio editor");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setAcceptAllFileFilterUsed(true);
            if (!path.getText().isBlank()) chooser.setSelectedFile(new File(path.getText()));
            if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                path.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.add(path, BorderLayout.CENTER);
        input.add(browse, BorderLayout.EAST);
        body.add(input, BorderLayout.CENTER);
        JLabel status = new JLabel("The original sample file will be passed as one argument.");
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> window.dispose());
        JButton save = new JButton(openAfter ? "Save and open" : "Save");
        save.addActionListener(event -> {
            try {
                EditorCommand.validate(path.getText(), System.getProperty("os.name", ""));
                String encoded = Base64.getEncoder().encodeToString(path.getText().strip().getBytes(StandardCharsets.UTF_8));
                System.out.println(DialogProcess.PATH_PREFIX + encoded);
                System.out.flush();
                window.dispose();
            } catch (java.io.IOException error) {
                status.setText(error.getMessage());
                window.pack();
            }
        });
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);
        window.setContentPane(body);
        style(body);
        window.getRootPane().setDefaultButton(save);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    private static void style(Component component) {
        component.setBackground(new Color(35, 37, 40));
        component.setForeground(new Color(212, 216, 220));
        component.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        if (component instanceof JTextField field) {
            field.setBackground(new Color(24, 26, 29));
            field.setCaretColor(new Color(212, 216, 220));
        }
        if (component instanceof JButton button) button.setFocusPainted(false);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) style(child);
        }
    }
}

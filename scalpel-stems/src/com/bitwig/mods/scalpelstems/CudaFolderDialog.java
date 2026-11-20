package com.bitwig.mods.scalpelstems;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** The folder chooser runs in its own desktop JVM. */
public final class CudaFolderDialog {
    static final String PATH_PREFIX = "SCALPEL_CUDA_FOLDER=";

    private CudaFolderDialog() {}

    public static void main(String[] args) throws Exception {
        Path current = Path.of(args[0]);
        while (current != null && !Files.isDirectory(current)) current = current.getParent();
        Path initial = current;
        SwingUtilities.invokeAndWait(() -> choose(initial));
    }

    private static void choose(Path initial) {
        JFrame window = new JFrame("Choose a CUDA installation folder");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setAlwaysOnTop(true);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
        chooser.setApproveButtonText("Select Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (initial != null) {
            chooser.setCurrentDirectory(initial.toFile());
            chooser.setSelectedFile(initial.toFile());
        }
        chooser.addActionListener(event -> {
            if (JFileChooser.APPROVE_SELECTION.equals(event.getActionCommand())) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                System.out.println(PATH_PREFIX + Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8)));
                System.exit(0);
            } else if (JFileChooser.CANCEL_SELECTION.equals(event.getActionCommand())) {
                System.exit(0);
            }
        });
        window.setContentPane(chooser);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }
}

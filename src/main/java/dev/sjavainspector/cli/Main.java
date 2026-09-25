package dev.sjavainspector.cli;

import dev.sjavainspector.io.FileSourceRepository;
import dev.sjavainspector.ui.Workbench;
import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Composition root; System.exit appears only at the process boundary. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("gui")) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("Desktop mode needs a graphical display. Use 'check' for headless environments.");
                System.exit(2);
            }
            Workbench.launch();
            return;
        }
        int status = new Cli(new FileSourceRepository()).run(args,
                new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        System.exit(status);
    }
}

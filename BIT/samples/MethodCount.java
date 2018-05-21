import BIT.highBIT.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;

public class MethodCount {
    private static int m_count = 0;

    public static void main(String argv[]) {

        String classfile = argv[0];

        if (!classfile.endsWith(".class")) {
            System.err.println("USAGE: java MethodCount FILENAME.class");
            return;
        }

        ClassInfo ci = new ClassInfo(classfile);

        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
            Routine routine = (Routine) e.nextElement();
            routine.addBefore("MethodCount", "mcount", new Integer(1));
        }
        ci.addAfter("MethodCount", "printMCount", ci.getClassName());
        ci.write(classfile);
    }

    public static synchronized void printMCount(String foo) {
        String FILENAME = "instrumentation_data.txt";
        try {
            File file = new File(FILENAME);
            if (!file.exists()) file.createNewFile();

            FileWriter writer = new FileWriter(file, true);
            writer.write(String.format("CALLS: %d\n", m_count));
            writer.flush();
            writer.close();

            System.out.println(String.format("WROTE %d to FILE %s", m_count, FILENAME));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static synchronized void mcount(int incr) {
        m_count++;
    }
}

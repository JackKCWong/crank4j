package scaffolding;

import com.danielflower.crank4j.sharedstuff.Crank4jException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

import static com.danielflower.crank4j.sharedstuff.Dirs.dirPath;

public class FileFinder {

    public static File e2eTestResourcesDir() {
        File module = new File("e2e-tests");
        if (!module.isDirectory()) {
            module = new File(".");
        }
        File dir = new File(module, FilenameUtils.separatorsToSystem("src/test/resources"));
        if (!dir.isDirectory()) {
            throw new Crank4jException("Could not find e2e resource dir at " + dirPath(module));
        }
        return dir;
    }

    public static File testFile(String path) {
        File file = new File(e2eTestResourcesDir(), FilenameUtils.separatorsToSystem(path));
        if (!file.isFile()) {
            throw new Crank4jException("Could not find " + path + " at " + dirPath(file));
        }
        return file;
    }

    public static String helloHtmlContents() throws IOException {
        return FileUtils.readFileToString(testFile("web/hello.html"), "UTF-8");
    }
}

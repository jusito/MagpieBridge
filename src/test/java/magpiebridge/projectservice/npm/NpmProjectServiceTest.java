package magpiebridge.projectservice.npm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The NpmProjectServiceTest.
 *
 * @author Jonas
 */
public class NpmProjectServiceTest {

  private static final Path rootPath = Paths.get("src", "test", "resources", "DemoProjectNpm").toAbsolutePath();

  private static String npmCommand = "npm";

  @BeforeClass
  public static void setup() throws InterruptedException, IOException {
    assumeTrue("Did not find demo project", Files.exists(rootPath));
    boolean npmExists = false;
    try {
      if (System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).contains("win")) {
        npmCommand = "npm.cmd";
      }
      int exitCode = new ProcessBuilder(npmCommand, "--version")
          .directory(rootPath.toFile())
          .inheritIO()
          .start()
          .waitFor();
      npmExists = exitCode == 0;
    } catch (IOException e) {
      npmExists = false;
    }
    assumeTrue("Npm is not installed", npmExists);

    // install the project to download packages to system
    int exitCode = new ProcessBuilder(npmCommand, "install")
        .directory(rootPath.toFile())
        .inheritIO()
        .start()
        .waitFor();
    assertEquals(0, exitCode);
  }

  private NpmProjectService npmProjectService;

  @Before
  public void before() {
    Logger.getLogger(NpmProjectService.class.getName()).setLevel(Level.FINE);
    npmProjectService = new NpmProjectService();
    npmProjectService.setRootPath(rootPath);
  }

  // TODO why is this always enabled? Not everyone needs npm DemoProject and why
  // are you forced to install it to run tests successful.
  // TODO webpack is not tested but mandatory to setup, so npm build doesn't work
  // but the dependency for it is installed

  @Test
  public void testProjectServiceInitialized() {
    assertEquals("npm", npmProjectService.getProjectType());
    assertTrue(npmProjectService.getDependencyPath().isPresent());
    assertTrue(npmProjectService.getPackageJson().isPresent());
    assertTrue(npmProjectService.getProjectPackage().isPresent());
    assertEquals(rootPath.resolve("node_modules"), npmProjectService.getDependencyPath().get());
    assertEquals(1, npmProjectService.getDirectDependencies().get().size());
    assertEquals(1, npmProjectService.getDependencies().size());
  }

  @Test
  public void testInstalledNpmPackageLodash() {
    NpmPackage npmPackage = npmProjectService.getDependency("lodash").get();
    assertFalse(npmPackage.getDependencies().isPresent());
    assertTrue(npmPackage.getPath().isPresent());
    assertEquals(rootPath.resolve("node_modules").resolve("lodash"), npmPackage.getPath().get());
    System.out.println(npmPackage.getVersion());
    assertTrue(npmPackage.getVersion().matches("^4\\.17\\..*$"));
  }
}

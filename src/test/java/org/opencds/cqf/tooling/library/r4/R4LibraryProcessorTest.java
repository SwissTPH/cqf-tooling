package org.opencds.cqf.tooling.library.r4;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.ToolsVersion;
import org.opencds.cqf.tooling.RefreshTest;
import org.opencds.cqf.tooling.library.LibraryProcessorTest;
import org.opencds.cqf.tooling.utilities.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;

public class R4LibraryProcessorTest extends LibraryProcessorTest {
    private String resourceDirectory = "r4";

    private FilesystemPackageCacheManager pcm;
    public R4LibraryProcessorTest() {
        super(new R4LibraryProcessor(), FhirContext.forCached(FhirVersionEnum.R4), "R4LibraryProcessorTest");
        try {
            pcm = new FilesystemPackageCacheManager(true, ToolsVersion.TOOLS_VERSION);
        }
        catch(IOException e) {}
    }

    @BeforeClass
    public void setup() {
        try {
            if (pcm != null) {
                pcm.loadPackage(VersionUtilities.packageForVersion("4.0.1"), "4.0.1");
            }
        }
        catch(IOException e) {}
    }

    @BeforeMethod
    public void setUp() throws Exception {
        IOUtils.resourceDirectories = new ArrayList<String>();
        IOUtils.clearDevicePaths();
        File dir  = new File("target" + separator + "refreshLibraries" + separator + "r4");
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
    }
    
    @Test
    private void testRefreshOverwriteLibraries() throws Exception {
        String targetDirectory = "target" + separator + "refreshLibraries" + separator + this.resourceDirectory;
        copyResourcesToTargetDir(targetDirectory, this.resourceDirectory);
        
        String libraryPath = separator + "input" + separator + "resources" + separator + "library" + separator + "library-EXM124_FHIR4-8.2.000.json";
        runRefresh(
            targetDirectory,
            targetDirectory + libraryPath,
            targetDirectory + separator + "input" + separator + "pagecontent" + separator + "cql" + separator + "EXM124_FHIR4-8.2.000.cql",
            false
        );

        validateCqfmSofwareSystemExtension(targetDirectory + libraryPath);
    }

    @Test
    private void testRefreshOutputDirectory() throws Exception {
        // create a output directory under target directory
        File targetDirectory = new File("target" + separator + "refreshLibraries" + separator + resourceDirectory);
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }
        String resourceDirPath = RefreshTest.class.getResource(resourceDirectory).getPath();
        assertTrue(targetDirectory.listFiles().length == 0);

        String libraryPath = separator + "input" + separator + "resources" + separator + "library" + separator + "library-EXM124_FHIR4-8.2.000.json";
        runRefresh(
            resourceDirPath,
            resourceDirPath + libraryPath,
            targetDirectory.getAbsolutePath(),
            resourceDirPath + separator + "input" + separator + "pagecontent" + separator + "cql" + separator + "EXM124_FHIR4-8.2.000.cql",
            false
        );

        assertTrue(targetDirectory.listFiles().length > 0);
    }
}

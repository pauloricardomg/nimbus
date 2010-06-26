/*
 * Copyright 1999-2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.globus.workspace.testing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;
import org.globus.workspace.ReturnException;
import org.globus.workspace.WorkspaceException;
import org.globus.workspace.WorkspaceUtil;
import org.globus.workspace.testing.utils.ReprPopulator;
import org.nimbustools.api.brain.ModuleLocator;
import org.nimbustools.api.brain.NimbusHomePathResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import commonj.timers.TimerManager;

/**
 * Class to extend from to use the spring-loaded Nimbus Workspace Service environment in tests.
 * Will be helpful to look at an example subclass to see how it's done.  
 */
public abstract class NimbusTestBase extends AbstractTestNGSpringContextTests {

    // -----------------------------------------------------------------------------------------
    // STATIC VARIABLES
    // -----------------------------------------------------------------------------------------

    private static final String DATA_SOURCE_BEAN_NAME = "other.MainDataSource";
    private static final String TIMER_MANAGER_BEAN_NAME = "other.timerManager";
    public static final String FORCE_SUITES_DIR_PATH = "nimbus.servicetestsuites.abspath";
    public static final String NO_TEARDOWN = "nimbus.servicetestsuites.noteardown";
    private static final String LOG_SEP =
            "\n-----------------------------------------------------------------------";


    // -----------------------------------------------------------------------------------------
    // INSTANCE VARIABLES
    // -----------------------------------------------------------------------------------------

    // How tests get a reference into the system that NimbusTestBase bootstraps, it is the
    // same object (via interface) that the protocol layers use (it is "the RM API").
    protected ModuleLocator locator;

    // 'logger' should be used only after suite setup, this class prevents NPEs beforehand
    protected Log logger = new FakeLog();


    // -----------------------------------------------------------------------------------------
    // ABSTRACT METHODS
    // -----------------------------------------------------------------------------------------

    /**
     * This is how coordinate your Java test suite code with the conf files to use.
     * @return absolute path to the value that should be set for $NIMBUS_HOME
     * @throws Exception if $NIMBUS_HOME cannot be determined
     */
    protected abstract String getNimbusHome() throws Exception;

    // -----------------------------------------------------------------------------------------
    // @Overrides AbstractTestNGSpringContextTests
    // -----------------------------------------------------------------------------------------
    
    @Override
    @BeforeClass(alwaysRun=true)
    protected void springTestContextPrepareTestInstance() throws Exception {
        this.suiteSetup();
        
        super.springTestContextPrepareTestInstance();
    }
    
    @Override
    @BeforeMethod(alwaysRun=true)
    protected void springTestContextBeforeTestMethod(Method testMethod)
            throws Exception {      
        super.springTestContextBeforeTestMethod(testMethod);
        
        //Looked up before each test method in case @DirtiesContext was used in previous method
        this.locator = (ModuleLocator) applicationContext.getBean("nimbus-brain.ModuleLocator");
    }
    
    @Override
    @AfterMethod(alwaysRun=true)
    protected void springTestContextAfterTestMethod(Method testMethod)
            throws Exception {
        
        if(testMethod.isAnnotationPresent(DirtiesContext.class)){
            logger.debug(LOG_SEP + "\n*** @DirtiesContext FOUND - STARTING CLEANUP: " + LOG_SEP);       
            stopTimerManager();
            shutdownDB();
            resetDB();
            logger.debug(LOG_SEP + "\n*** @DirtiesContext FOUND - FINISHED CLEANUP: " + LOG_SEP);
        } 
        
        super.springTestContextAfterTestMethod(testMethod);  
    }    
    
    // -----------------------------------------------------------------------------------------
    // TEST SETUP / TEARDOWN
    // -----------------------------------------------------------------------------------------

    /**
     * Set up logger and var directory for this test suite.
     *
     * Configures NIMBUS_HOME via system property.
     *
     * Should not use logger until this setup happens.
     * 
     * @throws Exception problem setting up var dir
     */
    protected void suiteSetup() throws Exception {

        logger = this.setUpLogger();
        logger.debug(LOG_SEP + "\n*** SUITE SETUP: " +
                        this.getClass().getSimpleName() + LOG_SEP);

        final String nimbusHome = this.getNimbusHome();
        logger.info("NIMBUS_HOME: " + nimbusHome);
        
        System.setProperty(NimbusHomePathResolver.NIMBUS_HOME_ENV_NAME,
                           nimbusHome);

        final File vardir = new File(nimbusHome, "services/var");
        if (vardir.exists()) {
            // teardown was not invoked or developer is trying something 'tricky'
            logger.error("Generated directory exists, cowardly refusing to remove it; " +
                        "leaving it alone: " + vardir.getAbsolutePath());
        } else {
            this.setUpShareDir(nimbusHome);
            final File setupExe =
                    new File(nimbusHome,
                             "services/share/nimbus/full-reset.sh"); // requires ant on PATH
            this.setUpVarDir(vardir, setupExe);
        }

        logger.debug(LOG_SEP + "\n*** SUITE SETUP DONE (tests will begin): " +
                        this.getClass().getSimpleName() + LOG_SEP);
    }

    /**
     * Remove var directory used in this test suite.  Intended to be called from
     * your subclass's @AfterSuite method.
     *
     * Requires NIMBUS_HOME is set as a system property.
     *
     * @throws Exception problem removing var dir
     */
    protected void suiteTeardown() throws Exception {

        final Properties props = System.getProperties();
        final String override = props.getProperty(NO_TEARDOWN);
        if (override != null) {
            logger.warn(LOG_SEP + "\n*** TESTS DONE, BUT NOT TEARING DOWN: " +
                    this.getClass().getSimpleName() + LOG_SEP);
            return;
        }

        logger.debug(LOG_SEP + "\n*** TESTS DONE (beginning teardown): " +
                        this.getClass().getSimpleName() + LOG_SEP);


        final String nh = System.getProperty(NimbusHomePathResolver.NIMBUS_HOME_ENV_NAME);
        if (nh == null) {
            throw new Exception("Could not tear down, no NIMBUS_HOME is set");
        }
        final File vardir = new File(nh, "services/var");
        if (!vardir.exists()) {
            throw new Exception("Could not tear down, var dir " +
                    "does not exist: " + vardir.getAbsolutePath());
        }

        FileUtils.deleteDirectory(vardir);
        logger.info("Deleted test suite var dir '" + vardir.getAbsolutePath() + '\'');
        logger.debug(LOG_SEP + "\n*** TEARDOWN DONE: " +
                        this.getClass().getSimpleName() + LOG_SEP);
    }

    // -----------------------------------------------------------------------------------------
    // HELPER METHODS
    // -----------------------------------------------------------------------------------------

    /**
     * Returns ReprPopulator instance, a class that helps a test populate RM API
     * requests easily.
     *
     * @see org.globus.workspace.testing.utils.ReprPopulator
     * @return ReprPopulator
     */
    protected ReprPopulator populator() {
        return new ReprPopulator(this.locator.getReprFactory());
    }

    /**
     * Return the path to the spring xml configuration to instantiate for this suite of tests.
     *
     * It must be a relative path starting from $NIMBUS_HOME
     *
     * A valid value is usually going to be the default but this method is here in case a
     * suite needs to change it.
     *
     * Default: "services/etc/nimbus/workspace-service/other/main.xml"
     *
     * @return relative path to spring conf
     */
    protected String getRelativeSpringConf() {
        return "services/etc/nimbus/workspace-service/other/main.xml";
    }

    /**
     * Helper for you to implement #getNimbusHome() (see 'basic' example).
     *
     * Determine the path to the "service/service/java/tests/suites" directory so
     * that paths can be conveniently (systematically) constructed to conf files.
     *
     * This can be rigged by setting the "nimbus.servicetestsuites.abspath" system property
     * if you find that necessary (to invoke from ant, for example).
     *
     * @return the "service/service/java/tests/suites" directory, never null
     * @throws Exception if the path can not be determined
     */
    public File determineSuitesPath() throws Exception {

        final Properties props = System.getProperties();
        final String override = props.getProperty(FORCE_SUITES_DIR_PATH);
        if (override != null) {
            return new File(override);
        }

        final String token = "lib" + File.separator + "services" + File.separator;
        final String classpath = props.getProperty("java.class.path");
        if (classpath == null) {
            throw new Exception("java.class.path property was deleted?");
        }
        
        final String[] parts = classpath.split(File.pathSeparator);
        for (String part : parts) {
            final int idx = part.indexOf(token);
            if (idx > 0) {
                if (part.contains(token)) {
                    final File candidate =
                            candidate(part.substring(0,idx), "service/service/java/tests/suites/");
                    if (candidate != null && suitesSubdirsPresent(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        // as a last resort, try analyzing cwd and all of its parent directories
        // to find repo topdir
        final File cwd = new File(props.getProperty("user.dir"));

        final String bail = "Adjust CWD or consider using the '" + FORCE_SUITES_DIR_PATH + "' property";
        
        if (!okdir(cwd)) {
            throw new Exception(bail);
        }

        String apath = cwd.getAbsolutePath();
        while (apath != null) {
            final File candidate = candidate(apath, "service/service/java/tests/suites/");
            if (candidate != null && suitesSubdirsPresent(candidate)) {
                return candidate;
            }
            apath = new File(apath).getParent();
        }

        throw new Exception(bail);
    }

    protected Log setUpLogger() {
        final String log4jPropKey = "log4j.configuration";

        // allow test runner to override
        final String config = System.getProperty(log4jPropKey);
        if (config != null) {
            return LogFactory.getLog(NimbusTestBase.class.getName());
        }

        final String propPath;
        try {
            final File suites = this.determineSuitesPath();
            final File common = new File(suites, "common");
            if (!okdir(common)) {
                throw new Exception("could not find common dir");
            }
            final File props = new File(common, "suites.log4j.properties");
            if (!props.exists()) {
                throw new Exception("file does not exist: " + props.getAbsolutePath());
            }
            propPath = props.getAbsolutePath();

        } catch (Exception e) {
            System.err.println("Warning, could not find any logger configuration: " + e.getMessage());
            return LogFactory.getLog(NimbusTestBase.class.getName());
        }

        PropertyConfigurator.configure(propPath);
        return LogFactory.getLog(NimbusTestBase.class.getName());
    }

    // candidate repo topdir
    private static File candidate(String dirString, String append) {
        final File dir = new File(dirString);
        if (!okdir(dir)) {
            return null;
        }
        final File testdir = new File(dir, append);
        if (!okdir(testdir)) {
            return null;
        }
        return testdir;
    }

    // look for "common" and "basic" subdirs as a sanity check
    private static boolean suitesSubdirsPresent(File suitedirCandidate) {
        final File common = new File(suitedirCandidate, "common");
        if (!okdir(common)) {
            return false;
        }
        final File basic = new File(suitedirCandidate, "basic");
        return okdir(basic);
    }

    private static boolean okdir(File dir) {
        return dir.isAbsolute() && dir.exists() && dir.isDirectory();
    }

    // set up a fresh var directory, initialize databases etc. using exe
    protected void setUpVarDir(File vardir, File exe) throws Exception {
        if (vardir == null) {
            throw new IllegalArgumentException("vardir is missing");
        }
        if (exe == null) {
            throw new IllegalArgumentException("sharedir is missing");
        }

        if (!exe.exists()) {
            throw new IllegalArgumentException(
                    "setup exe does not exist: " + exe.getAbsolutePath());
        }
        if (vardir.exists()) {
            throw new IllegalArgumentException("directory exists: " + vardir.getAbsolutePath());
        }
        
        final boolean created = vardir.mkdir();
        if (!created) {
            throw new IOException("directory couldn't be created: " + vardir.getAbsolutePath());
        }

        final String[] cmd = {exe.getAbsolutePath()};
        WorkspaceUtil.runCommand(cmd, true, false);

    }

    // overwrite "workspace.persistence.conf" to match path
    protected void setUpShareDir(String nimbusHome) throws Exception {
        if (nimbusHome == null) {
            throw new IllegalArgumentException("nimbusHome is missing");
        }
        final File nh = new File(nimbusHome);
        if (!nh.exists()) {
            throw new IllegalArgumentException(
                    "directory does not exist: " + nh.getAbsolutePath());
        }

        final File targetdir = new File(nh, "services/share/nimbus");
        if (!targetdir.exists()) {
            throw new IllegalArgumentException(
                    "directory does not exist: " + targetdir.getAbsolutePath());
        }
        final File targetpath = new File(targetdir, "workspace.persistence.conf");


        final Properties conf = new Properties();
        conf.setProperty("derby.relative.dir.prop", "nimbus");
        conf.setProperty("workspace.sqldir.prop",
                         new File(nh, "services/share/nimbus/lib").getAbsolutePath());
        conf.setProperty("derby.system.home.prop",
                         new File(nh, "services/var").getAbsolutePath());
        conf.setProperty("pwGen.path.prop",
                         new File(nh, "services/etc/nimbus/workspace-service/other/shared-secret-suggestion.py").getAbsolutePath());
        conf.setProperty("workspace.dbdir.prop",
                         new File(nh, "services/var/nimbus").getAbsolutePath());
        conf.setProperty("workspace.notifdir.prop",
                         new File(nh, "services/share/nimbus/lib").getAbsolutePath());

        // guess where lib/services is for derby classpath setting
        String libdir = null;
        String apath = nh.getAbsolutePath();
        while (apath != null) {
            final File candidate = candidate(apath, "lib/services/");
            if (candidate != null) {
                libdir = candidate.getAbsolutePath();
            }
            apath = new File(apath).getParent();
        }
        if (libdir == null) {
            throw new Exception("could not determine proper lib/services directory, " +
                    "is your nimbus home somewhere outside of the repository");
        }
        final File sanityCheck = new File(libdir, "derby.jar");
        if (!sanityCheck.exists()) {
            throw new Exception("could not determine proper lib/services directory, " +
               "is your nimbus home somewhere outside of the repository (derby.jar not found)");
        }
        conf.setProperty("derby.classpath.dir.prop", libdir);


        final FileOutputStream fos = new FileOutputStream(targetpath);
        try {
            conf.store(fos, null);
        } finally {
            fos.close();
        }
    }
    
    // -----------------------------------------------------------------------------------------
    // CLEAN-UP METHODS
    // -----------------------------------------------------------------------------------------

    private void stopTimerManager() {
        logger.info("Stopping Timer Manager..");
        TimerManager timerManager = (TimerManager) applicationContext.getBean(TIMER_MANAGER_BEAN_NAME);
        timerManager.stop();
        logger.info("Timer Manager succesfully stopped");        
    }

    private void resetDB() throws Exception, WorkspaceException,
            ReturnException {
        final File exe = new File(getNimbusHome(), "services/share/nimbus/servicedb-reset.sh"); // requires ant on PATH
        final String[] cmd = {exe.getAbsolutePath()};
        WorkspaceUtil.runCommand(cmd, true, true);
    }

    private void shutdownDB() {
        logger.info("Shutting down DB..");
        BasicDataSource ds = (BasicDataSource) applicationContext.getBean(DATA_SOURCE_BEAN_NAME);
        ds.addConnectionProperty("shutdown", "true");
        try{
            //Calling twice to be sure that connection is going to be shutdown (calling once doesn't shutdown, probably a bug)
            ds.getConnection();
            ds.getConnection();
        } catch (SQLException e){
            logger.info("DB succesfully shutdown.");
        }
        
    }
}

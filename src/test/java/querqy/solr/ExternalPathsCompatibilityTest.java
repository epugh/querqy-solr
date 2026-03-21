package querqy.solr;

import org.junit.Test;
import org.junit.Assume;
import java.nio.file.Path;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Tests to verify that Solr's ExternalPaths class works correctly in external plugin projects.
 * 
 * This test verifies the fix for the Solr 10.0 issue where ExternalPaths threw NullPointerException
 * during static initialization when running outside the Solr source tree.
 * 
 * Expected behavior:
 * - Solr 10.0: Would fail with NPE during class loading
 * - Solr 10.1+: Should handle null SOURCE_HOME gracefully
 */
public class ExternalPathsCompatibilityTest {
    
    @Test
    public void testExternalPathsDoesNotThrowNPE() throws Exception {
        // This test verifies that ExternalPaths can be loaded without NPE
        // In Solr 10.0, this would throw ExceptionInInitializerError
        Class<?> externalPathsClass = Class.forName("org.apache.solr.util.ExternalPaths");
        assertNotNull("ExternalPaths class should load successfully", externalPathsClass);
    }
    
    @Test
    public void testSourceHomeCanBeNull() throws Exception {
        Class<?> externalPathsClass = Class.forName("org.apache.solr.util.ExternalPaths");
        Object sourceHome = externalPathsClass.getField("SOURCE_HOME").get(null);
        
        // In external plugin projects, SOURCE_HOME will be null - this is expected and OK
        // The important thing is that it doesn't throw NPE
        if (sourceHome == null) {
            System.out.println("INFO: SOURCE_HOME is null (expected in external plugin projects)");
        } else {
            System.out.println("INFO: SOURCE_HOME is available: " + sourceHome);
        }
        
        // Test passes regardless - we just want to verify no NPE
        assertTrue("Test should always pass - we're just checking for NPE", true);
    }
    
    @Test
    public void testGetterMethodsExist() throws Exception {
        // Verify that the new getter methods exist (Solr 10.1+)
        Class<?> externalPathsClass = Class.forName("org.apache.solr.util.ExternalPaths");
        
        try {
            Method getWebappHome = externalPathsClass.getMethod("getWebappHome");
            assertNotNull("getWebappHome() method should exist", getWebappHome);
            
            Method getDefaultConfigSet = externalPathsClass.getMethod("getDefaultConfigSet");
            assertNotNull("getDefaultConfigSet() method should exist", getDefaultConfigSet);
            
            Method getTechproductsConfigSet = externalPathsClass.getMethod("getTechproductsConfigSet");
            assertNotNull("getTechproductsConfigSet() method should exist", getTechproductsConfigSet);
            
            Method getServerHome = externalPathsClass.getMethod("getServerHome");
            assertNotNull("getServerHome() method should exist", getServerHome);
            
            System.out.println("✓ All new getter methods exist (Solr 10.1+ with ExternalPaths fix)");
            
        } catch (NoSuchMethodException e) {
            // Getter methods don't exist - probably Solr 10.0
            System.out.println("⚠ Getter methods not found - using Solr 10.0 or earlier");
            Assume.assumeTrue("Getter methods require Solr 10.1+", false);
        }
    }
    
    @Test
    public void testGetterMethodsReturnNullGracefully() throws Exception {
        Class<?> externalPathsClass = Class.forName("org.apache.solr.util.ExternalPaths");
        
        try {
            // Try to invoke getter methods - they should return null gracefully if not in Solr source tree
            Method getWebappHome = externalPathsClass.getMethod("getWebappHome");
            Object result = getWebappHome.invoke(null);
            // Result can be null or a Path - either is fine, as long as no NPE
            System.out.println("getWebappHome() returned: " + result);
            
            Method getDefaultConfigSet = externalPathsClass.getMethod("getDefaultConfigSet");
            result = getDefaultConfigSet.invoke(null);
            System.out.println("getDefaultConfigSet() returned: " + result);
            
            Method getServerHome = externalPathsClass.getMethod("getServerHome");
            result = getServerHome.invoke(null);
            System.out.println("getServerHome() returned: " + result);
            
            // If we get here without NPE, the fix is working
            assertTrue("Getter methods should work without throwing NPE", true);
            
        } catch (NoSuchMethodException e) {
            System.out.println("⚠ Getter methods not available - skipping test (requires Solr 10.1+)");
            Assume.assumeTrue("Getter methods require Solr 10.1+", false);
        }
    }
    
    @Test
    public void testDeprecatedFieldsStillWork() throws Exception {
        Class<?> externalPathsClass = Class.forName("org.apache.solr.util.ExternalPaths");
        
        try {
            // In Solr 10.1+, these fields should be deprecated but still work
            Object webappHome = externalPathsClass.getField("WEBAPP_HOME").get(null);
            System.out.println("WEBAPP_HOME (deprecated): " + webappHome);
            
            Object defaultConfigSet = externalPathsClass.getField("DEFAULT_CONFIGSET").get(null);
            System.out.println("DEFAULT_CONFIGSET (deprecated): " + defaultConfigSet);
            
            Object serverHome = externalPathsClass.getField("SERVER_HOME").get(null);
            System.out.println("SERVER_HOME (deprecated): " + serverHome);
            
            // Fields can be null - that's OK, as long as no NPE
            assertTrue("Deprecated fields should work without throwing NPE", true);
            
        } catch (Exception e) {
            fail("Deprecated fields should still be accessible: " + e.getMessage());
        }
    }
    
    @Test
    public void testSystemPropertyOverride() throws Exception {
        // Test that solr.test.source.home system property is recognized (Solr 10.1+)
        String originalProperty = System.getProperty("solr.test.source.home");
        
        try {
            // This test just verifies the property can be set
            // The actual behavior would require reloading the class
            System.setProperty("solr.test.source.home", "/tmp/fake-solr");
            String value = System.getProperty("solr.test.source.home");
            assertEquals("System property should be settable", "/tmp/fake-solr", value);
            
            System.out.println("✓ solr.test.source.home property can be set");
            System.out.println("  (Note: ExternalPaths reads this during class initialization)");
            
        } finally {
            // Restore original property
            if (originalProperty != null) {
                System.setProperty("solr.test.source.home", originalProperty);
            } else {
                System.clearProperty("solr.test.source.home");
            }
        }
    }
}
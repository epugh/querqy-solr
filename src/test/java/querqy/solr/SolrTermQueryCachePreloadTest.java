package querqy.solr;

import static querqy.solr.QuerqyQParserPlugin.PARAM_REWRITERS;
import static querqy.solr.StandaloneSolrTestSupport.withCommonRulesRewriter;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QueryParsing;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@SolrTestCaseJ4.SuppressSSL
public class SolrTermQueryCachePreloadTest extends SolrTestCaseJ4 {

    @BeforeClass
    public static void beforeTest() throws Exception{
        initCore("solrconfig.xml", "schema.xml", getFile("cache-preload-test/collection1").getParent());
        withCommonRulesRewriter(h.getCore(), "common_rules", "configs/commonrules/rules-cache.txt");

        // this leaves the rewriter file in place so that it will be available
        // for the firstSearcher event in testThatCacheIsAvailableAndPrefilledNotUpdatedByQueryAndUpdatedByRewriter()
        h.close();
        initCore("solrconfig.xml", "schema.xml", getFile("cache-preload-test/collection1").getParent());
    }
     
    @Ignore("Metrics API changed in Solr 11 - needs to be updated to use OpenTelemetry instead of Dropwizard")
    @Test
    public void testThatCacheIsAvailableAndPrefilledNotUpdatedByQueryAndUpdatedByRewriter() throws Exception {
        // TODO: Update this test to use Solr 11's OpenTelemetry-based metrics API
        // The old Dropwizard/Codahale metrics API (registry(), Gauge, etc.) has been replaced
        // For now, this test is disabled until the metrics access is updated
         
        String q = "a b c";
        SolrQueryRequest req3 = req(
                 
                CommonParams.Q, q,
                DisMaxParams.QF, "f1 f2",
                QueryParsing.OP, "AND",
                "defType", "querqy",
                "debugQuery", "true",
                PARAM_REWRITERS, "common_rules"
                 );
         
        // f1:b and f2:b would be produced by synonym rule, but
        // due to pre-testing for hits in preload they should not
        // occur in the parsed query
        assertQ("Terms w/o hits found in parsedquery",
                 req3,
                 "//result[@name='response'][@numFound='0']",
                 "//str[@name='parsedquery'][not(contains(.,'f1:b'))]",
                 "//str[@name='parsedquery'][not(contains(.,'f2:b'))]"
                );

        req3.close();
    }
   
}

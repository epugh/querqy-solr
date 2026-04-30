package querqy.solr;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.handler.admin.MetricsHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SolrCache;
import org.apache.solr.util.stats.MetricUtils;

import java.io.IOException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SolrTestCaseJ4.SuppressSSL
public class SolrTermQueryCacheTest extends SolrTestCaseJ4 {

    private static final String CACHE_NAME = "querqyTermQueryCache";
    private static final String LOOKUPS_METRIC = "solr_core_indexsearcher_cache_lookups";
    private static final String SIZE_METRIC = "solr_core_indexsearcher_cache_size";

    public void index() throws Exception {

        assertU(adoc("id", "1", "f1", "a"));
        assertU(adoc("id", "2", "f1", "a", "f2", "b"));
        assertU(adoc("id", "3", "f1", "a", "f2", "c"));
        assertU(commit());
    }

    @BeforeClass
    public static void beforeTests() throws Exception {
        initCore("solrconfig-cache.xml", "schema.xml");
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        clearIndex();
        index();
    }

    @Test
    public void testThatCacheIsAvailable() throws IOException {
        SolrCache<?, ?> cache = h.getCore().withSearcher(s -> s.getCache(CACHE_NAME));
        assertNotNull("Missing querqy cache", cache);
    }

    @Test
    public void testThatTermQueriesArePutIntoAndServedFromCache() throws Exception {

        String q = "c";

        SolrQueryRequest req = req("q", q,
              DisMaxParams.QF, "f1 f2",
              QueryParsing.OP, "OR",
              DisMaxParams.TIE, "0.1",
              "defType", "querqy",
              "debugQuery", "true"
        );

        assertQ("Unexpected query result while caching",
                req,
                "//result[@name='response'][@numFound='1']");
        req.close();

        CacheStats stats1 = readCacheStats(CACHE_NAME);
        assertEquals("lookups after first query", 2L, stats1.lookups);
        assertEquals("hits after first query", 0L, stats1.hits);
        assertEquals("size after first query", 2L, stats1.size);

        SolrQueryRequest req2 = req("q", q,
                DisMaxParams.QF, "f1 f2",
                QueryParsing.OP, "OR",
                DisMaxParams.TIE, "0.1",
                "defType", "querqy",
                "debugQuery", "true"
        );

        assertQ("Unexpected query result while using cache",
                req2,
                "//result[@name='response'][@numFound='1']");
        req2.close();

        CacheStats stats2 = readCacheStats(CACHE_NAME);
        assertEquals("lookups after second query", 4L, stats2.lookups);
        assertEquals("hits after second query", 2L, stats2.hits);
        assertEquals("size after second query", 2L, stats2.size);
    }

    /**
     * Reads cache stats from the Prometheus metrics snapshot exposed by {@link MetricsHandler}.
     * The {@code /admin/mbeans} handler the original test used was removed in Solr 9; Solr 10
     * exposes cache metrics via OpenTelemetry / Prometheus.
     */
    private static CacheStats readCacheStats(String cacheName) throws Exception {
        try (MetricsHandler handler = new MetricsHandler(h.getCoreContainer())) {
            SolrQueryResponse resp = new SolrQueryResponse();
            handler.handleRequestBody(
                    req(
                            CommonParams.QT, CommonParams.METRICS_PATH,
                            CommonParams.WT, MetricUtils.PROMETHEUS_METRICS_WT,
                            MetricUtils.METRIC_NAME_PARAM,
                            LOOKUPS_METRIC + "," + SIZE_METRIC),
                    resp);
            MetricSnapshots snapshots = (MetricSnapshots) resp.getValues().get("metrics");

            long hits = sumDataPoints(snapshots, LOOKUPS_METRIC, cacheName, "hit");
            long misses = sumDataPoints(snapshots, LOOKUPS_METRIC, cacheName, "miss");
            long size = sumDataPoints(snapshots, SIZE_METRIC, cacheName, null);
            return new CacheStats(hits + misses, hits, size);
        }
    }

    /**
     * Sums all data point values for {@code metricName} where the {@code name} label matches
     * {@code cacheName}, and (if non-null) the {@code result} label matches {@code resultLabel}.
     */
    private static long sumDataPoints(MetricSnapshots snapshots, String metricName,
                                      String cacheName, String resultLabel) {
        long total = 0L;
        for (MetricSnapshot snapshot : snapshots) {
            if (!metricName.equals(snapshot.getMetadata().getPrometheusName())) {
                continue;
            }
            for (DataPointSnapshot dp : snapshot.getDataPoints()) {
                if (!cacheName.equals(dp.getLabels().get("name"))) {
                    continue;
                }
                if (resultLabel != null && !resultLabel.equals(dp.getLabels().get("result"))) {
                    continue;
                }
                if (dp instanceof CounterSnapshot.CounterDataPointSnapshot c) {
                    total += (long) c.getValue();
                } else if (dp instanceof GaugeSnapshot.GaugeDataPointSnapshot g) {
                    total += (long) g.getValue();
                }
            }
        }
        return total;
    }

    private record CacheStats(long lookups, long hits, long size) { }
}

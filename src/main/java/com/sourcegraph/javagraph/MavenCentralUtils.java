package com.sourcegraph.javagraph;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class MavenCentralUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCentralUtils.class);

    private static final String BASE_URL = "http://search.maven.org/solrsearch";

    private MavenCentralUtils() {
    }

    public static RawDependency searchInCentral(Path jar) {
        try {
            return searchInCentral(calculateSha(jar));
        } catch (Exception e) {
            LOGGER.warn("Failed to search for jar dependency", e);
            return null;
        }
    }

    private static String calculateSha(Path file) throws IOException {
        try (InputStream stream = new FileInputStream(file.toFile())) {
            return Hex.encodeHexString(DigestUtils.sha1(stream));
        }
    }

    private static RawDependency searchInCentral(String sha) throws IOException, SolrServerException {
        LOGGER.info("Looking for artifact by SHA {}", sha);
        XMLResponseParser p = new XMLResponseParser();
        SolrClient client = new HttpSolrClient(BASE_URL, null, new XMLResponseParser() {
            @Override
            public String getContentType() {
                return "text/xml; charset=UTF-8";
            }
        });
        SolrQuery query = new SolrQuery();
        query.setQuery("1:\"" + sha + "\"");
        query.setRows(2);
        query.setShowDebugInfo(true);
        QueryResponse resp = client.query(query);
        SolrDocumentList docs = resp.getResults();
        if (docs.size() != 1) {
            return null;
        }
        SolrDocument document = docs.iterator().next();
        RawDependency ret = new RawDependency((String) document.get("g"),
                (String) document.get("a"),
                (String) document.get("v"),
                null,
                null);
        LOGGER.info("Found {}/{}-{}", ret.groupID, ret.artifactID, ret.version);
        return ret;

    }

}

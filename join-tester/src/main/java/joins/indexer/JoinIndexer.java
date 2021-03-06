package joins.indexer;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JoinIndexer {
  protected final int NUM_TEXT_DOCS = 4500 * 1000; // Gives us about 27M docs
  protected final int NUM_INSTANCE_RECS_PER_DOC = 5;
  private final int NUM_WORDS_IN_TEXT_DOC = 1024;
  private final int RATIO_COMMON_WORDS = 7;
  private final int RATIO_ENGLISH_WORDS = 3;
  protected final int NUM_DOCS_IN_PACKET = 10000; // Completely arbitrary!
  protected final int COMMIT_WITHIN = 600000; // 10 minutes, again arbitrary!

  private final String DATA_SOURCE = "4";
  private final String DATA_SOURCE_NAME = "JOINS";
  private final String DATA_SOURCE_TYPE = "Custom";

  private final String SOLR_SERVER_URL = "http://localhost:8983/solr";

  long _start = System.currentTimeMillis();

  int _counter = 26104000; // Just a counter to insure doc IDs are unique.

  long _addedDocs = 0;

  StringBuilder _sb = new StringBuilder();

  ConcurrentUpdateSolrServer _server;
  List<SolrInputDocument> _docList = new ArrayList<SolrInputDocument>(NUM_DOCS_IN_PACKET + NUM_INSTANCE_RECS_PER_DOC + 10);

  public static void main(String[] args) throws Exception {
	  main(new JoinIndexer());
  }

protected static void main(JoinIndexer ji) {
	try {
      WordLists.init();
      ji.generateAllDocs();
    } catch (Exception e) {
      e.printStackTrace();
    }
}

  public JoinIndexer() throws Exception {

    _server = new ConcurrentUpdateSolrServer(SOLR_SERVER_URL, 4, 2);

    _server.setParser(new XMLResponseParser()); // binary parser is used by default

  }

  void generateAllDocs() throws Exception {
    generateDocs();
    if (_docList.size() > 0) {
      addDocs(_server, _docList);
      _docList.clear();
    }
    _server.commit();
  }

	protected void addDocs(ConcurrentUpdateSolrServer server, List<SolrInputDocument> list) throws SolrServerException, IOException {
		server.add(list);
	}

  //int tripper = 0;
  void generateDocs() throws Exception {
    for (int idx = 0; idx < NUM_TEXT_DOCS; ++idx) {
      SolrInputDocument doc = generateTextDoc();
      String joinId = doc.get("id").getValue().toString();
      String few_id = doc.get("few_id").getValue().toString();
      for (int jdx = 0; jdx < NUM_INSTANCE_RECS_PER_DOC; ++jdx) {
    	  SolrInputDocument child = generateInstanceRecord(joinId, few_id);
		addChild(doc, child);
      }
      if (_docList.size() >= NUM_DOCS_IN_PACKET) {
        _server.add(_docList, COMMIT_WITHIN);
        _addedDocs += _docList.size();
        long rate = _addedDocs / (1 + ((System.currentTimeMillis() - _start) / 1000));
        long endSecs = ((NUM_TEXT_DOCS * (NUM_INSTANCE_RECS_PER_DOC + 1)) - _addedDocs) / rate;
        log(String.format("Total docs indexed: %s, rate (docs/sec): %s. Projected end time (seconds from now): %s",
            WordLists.formatNum(_addedDocs),
            WordLists.formatNum(rate),
            WordLists.formatNum(endSecs)));
        _docList.clear();
//        if (++tripper > 100) break;
      }
    }
//    writeList();
  }

	protected void addChild(SolrInputDocument doc, SolrInputDocument child) {
		_docList.add( child );
	}

  SolrInputDocument generateTextDoc() throws Exception {
    // Fill the word buffer.
    _sb.setLength(0);
    int wordCount = 0;
    while (wordCount < NUM_WORDS_IN_TEXT_DOC) {
      for (int jdx = 0; jdx < RATIO_COMMON_WORDS; ++jdx) {
        WordLists.getCommonWord(_sb);
        ++wordCount;
      }
      for (int jdx = 0; jdx < RATIO_ENGLISH_WORDS; ++jdx) {
        WordLists.getEnglishWord(_sb);
        ++wordCount;
      }
    }

    SolrInputDocument doc = getMinimalDoc();
    doc.addField("text_all", _sb.toString());
    doc.addField("kind", "body");
    doc.addField("few_id", WordLists.getFew());
    _docList.add(doc);
    return doc;

  }

  SolrInputDocument generateInstanceRecord(String joinId, String few_id) throws Exception {
    SolrInputDocument doc = getMinimalDoc();
    doc.addField("join_id", joinId);
    doc.addField("few_join_id", few_id);
    doc.addField("date_one", WordLists.getDate());
    doc.addField("acl", WordLists.getInt(500000));
    doc.addField("date_two", WordLists.getDate());
    doc.addField("source", WordLists.getSource());
    doc.addField("place", WordLists.genLatLon());
    doc.addField("kind", "instance");
    return doc;
    
  }

  SolrInputDocument getMinimalDoc() {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", WordLists.getCommonWord() + "_" + _counter++);
    doc.addField("data_source", DATA_SOURCE);
    doc.addField("data_source_name", DATA_SOURCE_NAME);
    doc.addField("data_source_type", DATA_SOURCE_TYPE);

    return doc;
  }

  static void log(String msg) {
    System.out.println(msg);
  }
}
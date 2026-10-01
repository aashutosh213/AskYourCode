package com.askyourcode.app.ingestion.search;

import com.askyourcode.app.ingestion.model.CodeChunkEntity;
import com.askyourcode.app.ingestion.model.FileEntity;
import com.askyourcode.app.ingestion.model.RepositoryEntity;
import com.askyourcode.app.ingestion.repo.CodeChunkRepository;
import com.askyourcode.app.ingestion.repo.FileEntityRepository;
import com.askyourcode.app.ingestion.repo.RepositoryEntityRepository;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class KeywordSearchService {

    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchService.class);
    private static final String[] SEARCH_FIELDS = {"keywordText"};

    private final RepositoryEntityRepository repositoryRepository;
    private final FileEntityRepository fileRepository;
    private final CodeChunkRepository chunkRepository;

    public KeywordSearchService(RepositoryEntityRepository repositoryRepository,
                                FileEntityRepository fileRepository,
                                CodeChunkRepository chunkRepository) {
        this.repositoryRepository = repositoryRepository;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Builds a repository-scoped Lucene index from persisted chunks and runs BM25.
     * The in-memory index keeps this first implementation simple and avoids stale
     * search documents while indexing lifecycle/versioning is still evolving.
     */
    public KeywordSearchResult search(String queryText, String repositoryPath, int limit) {
        var repository = repositoryRepository.findByPath(repositoryPath);
        if (repository.isEmpty()) {
            return new KeywordSearchResult(List.of(), queryText);
        }

        try (WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
             ByteBuffersDirectory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                addRepositoryChunks(writer, repository.get());
            }

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Query query = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer)
                        .parse(normalizeForSearch(queryText));
                TopDocs topDocs = searcher.search(query, limit);
                List<KeywordSearchResult.SearchHit> results = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.doc(scoreDoc.doc);
                    results.add(new KeywordSearchResult.SearchHit(
                            document.getField("chunkId").numericValue().longValue(),
                            document.get("filePath"),
                            document.get("fileName"),
                            document.get("symbolName"),
                            document.get("symbolType"),
                            document.get("content"),
                            document.getField("startLine").numericValue().intValue(),
                            document.getField("endLine").numericValue().intValue(),
                            scoreDoc.score));
                }
                logger.info("Keyword search for '{}' returned {} results", queryText, results.size());
                return new KeywordSearchResult(results, queryText);
            }
        } catch (Exception e) {
            logger.warn("Keyword search failed for query '{}': {}", queryText, e.getMessage());
            return new KeywordSearchResult(List.of(), queryText);
        }
    }

    private void addRepositoryChunks(IndexWriter writer, RepositoryEntity repository) throws IOException {
        for (FileEntity file : fileRepository.findByRepository(repository)) {
            for (CodeChunkEntity chunk : chunkRepository.findByFile(file)) {
                Document document = new Document();
                document.add(new StoredField("chunkId", chunk.getId()));
                document.add(new TextField("content", chunk.getContent(), Field.Store.YES));
                document.add(new TextField("symbolName", chunk.getSymbolName(), Field.Store.YES));
                document.add(new TextField("filePath", file.getRelativePath(), Field.Store.YES));
                document.add(new TextField("keywordText", normalizeForSearch(
                        chunk.getContent() + " " + chunk.getSymbolName() + " " + file.getRelativePath()), Field.Store.NO));
                document.add(new StoredField("fileName", file.getFileName()));
                document.add(new StoredField("symbolType", chunk.getSymbolType()));
                document.add(new StoredField("startLine", chunk.getStartLine()));
                document.add(new StoredField("endLine", chunk.getEndLine()));
                writer.addDocument(document);
            }
        }
    }

    private String normalizeForSearch(String value) {
        return value.replaceAll("[^A-Za-z0-9_$]+", " ").trim();
    }
}

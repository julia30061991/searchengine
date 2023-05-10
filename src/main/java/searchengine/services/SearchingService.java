package searchengine.services;

import searchengine.model.Page;

import java.util.List;
import java.util.Map;

public interface SearchingService {
    void getLemmasFromQuery(String query);

    void sortWords(Map<String, Integer> words);

    void searchInfo(String query);

    List<Page> getSortedPages();
}
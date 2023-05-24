package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedSearchedResult;
import searchengine.dto.statistics.SearchedDataResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class SearchingServiceImpl implements SearchingService {
    private Map<String, Integer> wordsMap = new TreeMap<>();
    private Set<String> lemmasQuery = new HashSet<>();
    private TreeMap<String, Integer> sortedMap = new TreeMap<>();
    private List<Lemma> allLemmasList = new ArrayList<>();
    private int countResult = 0;
    private List<Index> indexes = new ArrayList<>();

    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;

    @Override
    public void getLemmasFromQuery(String query) {
        try {
            LemmatizationServiceImpl lemService = new LemmatizationServiceImpl();
            wordsMap = lemService.getLemmasFromText(query.toLowerCase());
            lemmasQuery.addAll(wordsMap.keySet());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void sortWords(Map<String, Integer> words) {
        ValueComparator bvc = new ValueComparator(words);
        sortedMap = new TreeMap<>(bvc);
        sortedMap.putAll(words);
    }

    @Override
    public void searchInfo(String query, String site) {
        sortWords(getLemmasWithFrequency(query));
        countResult = getSortedPages().size();
    }

    private Map<String, Integer> getLemmasWithFrequency(String query) {
        Map<String, Integer> mapLemmas = new HashMap<>();
        getLemmasFromQuery(query);
        for (String str : lemmasQuery) {
            List<Lemma> list = lemmaRepository.findAllByLemmaOrderByFrequencyAsc(str);
            allLemmasList.addAll(list);
        }
        for (Lemma lemma : allLemmasList) {
            String lemm = lemma.getLemma();
            if (mapLemmas.containsKey(lemm)) {
                mapLemmas.put(lemm, mapLemmas.get(lemm) + lemma.getFrequency());
            }
            mapLemmas.putIfAbsent(lemm, lemma.getFrequency());
        }
        return mapLemmas;
    }

    private List<Page> getPages(String lemma, List<Lemma> allLemmasList, List<Page> pages) {
        List<Lemma> selectedLemmas = allLemmasList.stream().filter(l -> lemma.equals(l.getLemma())).collect(Collectors.toList());
        indexes = indexRepository.findAllByLemmaIn(selectedLemmas);
        List<Page> indexPages = indexes.stream().map(Index::getPage).collect(Collectors.toList());
        if (pages != null && !pages.isEmpty()) {
            pages.retainAll(indexPages);
            return pages;
        }
        return indexPages;
    }

    @Override
    public List<Page> getSortedPages() {
        List<String> sortedLemmas = new ArrayList<>(sortedMap.keySet());
        List<Page> pages = new ArrayList<>();
        for (String sortedLemma : sortedLemmas) {
            pages = getPages(sortedLemma, allLemmasList, pages);
        }
        return pages;
    }

    public SearchedDataResponse getResultResponse(String query, String siteUrl, int offset, int limit) {
        DetailedSearchedResult result = new DetailedSearchedResult();
        List<DetailedSearchedResult> resList = new ArrayList<>();
        SearchedDataResponse response = new SearchedDataResponse();
        if (calculateRelevance().isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            return response;
        } else {
            for (Map.Entry<Page, Float> entry : calculateRelevance().entrySet()) {
                Page res = entry.getKey();
                Document doc = Jsoup.parse(res.getContent());
                String title = doc.title();
                String snippet = buildSnippet(entry.getKey(), query).toString();
                float relevance = entry.getValue();
                if (siteUrl == null) {
                    resList = buildDetailedResponse(res.getSite().getUrl(), res.getSite().getName(), res.getPath(), title, snippet,
                            relevance, result);
                } else {
                    if (res.getSite().getUrl().equals(siteUrl)) {
                        resList = buildDetailedResponse(siteUrl, res.getSite().getName(), res.getPath(), title, snippet,
                                relevance, result);
                    }
                }
            }
            response.setResult(true);
            response.setCount(countResult);
            if(resList.size() > limit) {
                response.setData(resList.subList(offset, offset + limit));
            } else {
                response.setData(resList);
            }
        }
        return response;
    }

    private List<DetailedSearchedResult> buildDetailedResponse(String siteUrl, String siteName, String uri, String title, String snippet,
                                       float relevance, DetailedSearchedResult result) {
        result.setSite(siteUrl);
        result.setSiteName(siteName);
        result.setUri(uri);
        result.setTitle(title);
        result.setSnippet(snippet);
        result.setRelevance(relevance);
        List<DetailedSearchedResult> resultList = new ArrayList<>();
        resultList.add(result);
        return resultList;
    }

    private Hashtable<Page, Float> calculateRelevance() {
        HashMap<Page, Float> pagesRelevance = new HashMap<>();
        for (Page page : getSortedPages()) {
            float relevanceOfPage = 0;
            for (Index index : indexes) {
                if (index.getPage() == page) {
                    relevanceOfPage += index.getRank();
                }
            }
            pagesRelevance.put(page, relevanceOfPage);
        }
        HashMap<Page, Float> pagesAbsRelevance = new HashMap<>();
        for (Page page : pagesRelevance.keySet()) {
            float absRelevant = pagesRelevance.get(page) / Collections.max(pagesRelevance.values());
            pagesAbsRelevance.put(page, absRelevant);
        }
        return pagesAbsRelevance.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, Hashtable::new));
    }

    private StringBuilder buildSnippet(Page page, String query) {
        StringBuilder builder = new StringBuilder();
        String[] words = query.split(" ");
        Document document = Jsoup.parse(page.getContent());
        String content = document.body().text();
        String[] sentences = content.split("[\\.\\!\\?]");
        for (String word : words) {
            for (String sentence : sentences) {
                if (sentence.toLowerCase().contains(word.toLowerCase())) {
                    String boldWord = "<b>" + word + "</b>";
                    String result = sentence.replaceAll(word, boldWord);
                    builder.append(result);
                    builder.append("...");
                    break;
                }
            }
        }
        return builder;
    }

    class ValueComparator implements Comparator<String> {
        Map<String, Integer> base;

        public ValueComparator(Map<String, Integer> base) {
            this.base = base;
        }

        public int compare(String a, String b) {
            if (base.get(b) >= base.get(a)) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
//максимальное кол-во строк в методе - 32
package searchengine.services;

import lombok.RequiredArgsConstructor;
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
public class SearchingServiceImpl implements SearchingService {
    private Map<String, Integer> wordsMap = new TreeMap<>();
    private Set<String> lemmasQuery = new HashSet<>();
    private TreeMap<String, Integer> sortedMap = new TreeMap<>();
    private List<Lemma> allLemmasList = new ArrayList<>();
    private int countResult = 0;

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
    public void searchInfo(String query) { //TODO: написать поиск по одному сайту, сейчас ищет по всем
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
        sortWords(mapLemmas);
        getSortedPages();
        countResult = getSortedPages().size();
        getContentFromPage(query);
    }

    private List<Page> getPages(String lemma, List<Lemma> allLemmasList, List<Page> pages) {
        List<Lemma> selectedLemmas = allLemmasList.stream().filter(l -> lemma.equals(l.getLemma())).collect(Collectors.toList());
        List<Index> indexes = indexRepository.findAllByLemmaIn(selectedLemmas);
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
        List<Page> pages = null;
        for (String sortedLemma : sortedLemmas) {
            pages = getPages(sortedLemma, allLemmasList, pages);
        }
        return pages;
    }

    public SearchedDataResponse getContentFromPage(String query) { //TODO: предусмотреть лимит и оффсет
        DetailedSearchedResult result = new DetailedSearchedResult();
        SearchedDataResponse response = new SearchedDataResponse();
        if (getSortedPages().isEmpty()) {
            response.setResult(true);
            response.setCount(0);
        } else {
            for (Page page : getSortedPages()) {
                result.setSite(page.getSite().getUrl());
                result.setSiteName(page.getSite().getName());
                result.setUri(page.getPath());
                Document doc = Jsoup.parse(page.getContent());
                String title = doc.title();
                result.setTitle(title);
                result.setSnippet(buildSnippet(page, query).toString());
                result.setRelevance(0.09898f); //TODO: изменить метод для отображения страниц по релевантности
                List<DetailedSearchedResult> resultList = new ArrayList<>();
                resultList.add(result);
                response.setResult(true);
                response.setCount(countResult);
                response.setData(resultList);
            }
        }
        return response;
    }

    private Hashtable<Page, Float> calculateRelevance(List<Page> pageList, List<Index> indexList) {
        HashMap<Page, Float> pagesRelevance = new HashMap<>();
        for (Page page : pageList) {
            float relevanceOfPage = 0;
            for (Index index : indexList) {
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
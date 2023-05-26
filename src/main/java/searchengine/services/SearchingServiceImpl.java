package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
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
                    buildDetailedResponse(res.getSite().getUrl(), res.getSite().getName(), res.getPath(), title, snippet,
                            relevance, resList);
                } else {
                    if (res.getSite().getUrl().equals(siteUrl)) {
                        buildDetailedResponse(siteUrl, res.getSite().getName(), res.getPath(), title, snippet,
                                relevance, resList);
                    }
                }
            }
            response.setResult(true);
            response.setCount(countResult);
            if (resList.size() > limit) {
                response.setData(resList.subList(offset, offset + limit));
            } else {
                response.setData(resList);
            }
        }
        return response;
    }

    private void buildDetailedResponse(String siteUrl, String siteName, String uri, String title, String snippet,
                                                               float relevance, List<DetailedSearchedResult> resultList) {
        DetailedSearchedResult result = new DetailedSearchedResult();
        result.setSite(siteUrl);
        result.setSiteName(siteName);
        result.setUri(uri);
        result.setTitle(title);
        result.setSnippet(snippet);
        result.setRelevance(relevance);
        resultList.add(result);
    }

    private Map<Page, Float> calculateRelevance() {
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
        Map<Page, Float> resultPages = pagesAbsRelevance.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).
                collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        return resultPages;
    }

    private StringBuilder buildSnippet(Page page, String query) {
        StringBuilder builder = new StringBuilder();
        String[] words = query.split(" ");
        Document document = Jsoup.parse(page.getContent());
        String content = document.body().text();
        String[] sentences = content.split("[\\.\\!\\?]");
        for (String word : getWordsValues(content, words)) {
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

    private Set<String> getWordsValues(String content, String[] words) {
        Map<String, String> fromText = new TreeMap<>();
        String[] wordsFromPage = content.replaceAll("\\.\\!\\?[0-9]@,\\\\_—", " ").split(" ");
        Map<String, String> fromText2 = new TreeMap<>();
        try {
            LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
            for (String word: words) {
                List<String> resultAll = new ArrayList<>();
                resultAll.addAll(luceneMorphology.getNormalForms(word));
                fromText.put(resultAll.get(0), word);
            }
            for (String word: wordsFromPage) {
                List<String> resultAll2 = new ArrayList<>();
                if(word.matches("[а-я]+")) {
                    resultAll2.addAll(luceneMorphology.getNormalForms(word.toLowerCase()));
                    fromText2.put(resultAll2.get(0), word);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Set<String> resValues = new HashSet<>();
        for(String key: fromText.keySet()) {
            for (String key2: fromText2.keySet()) {
                if(key.equals(key2)) {
                    resValues.add(fromText.get(key));
                    resValues.add(fromText2.get(key));
                }
            }
        }
        return resValues;
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
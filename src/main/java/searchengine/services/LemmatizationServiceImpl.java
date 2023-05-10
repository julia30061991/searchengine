package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class LemmatizationServiceImpl implements LemmatizationService {
    private final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private Map<String, Integer> collectionLemmas = new TreeMap<>();

    public Map<String, Integer> getLemmasFromText(String text) throws IOException {
        if (text.isEmpty()) {
            System.out.println("Текст отсутствует");
        }
        String[] words = text.toLowerCase().trim().replaceAll("([^а-я])", " ").split(" ");
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        String WORD_TYPE_REGEX = "[а-яА-Я]+";
        for (String word : words) {
            if (!word.isEmpty() && word.matches(WORD_TYPE_REGEX)) {
                List<String> wordBaseForms = luceneMorphology.getNormalForms(word);
                List<String> morphInfo = luceneMorphology.getMorphInfo(word);
                String normalWord = wordBaseForms.get(0);
                if (anyWordBaseBelongToParticle(morphInfo)) {
                    continue;
                }
                if (collectionLemmas.containsKey(normalWord)) {
                    collectionLemmas.put(normalWord, collectionLemmas.get(normalWord) + 1);
                } else {
                    collectionLemmas.put(normalWord, 1);
                }
            }
        }
        return collectionLemmas;
    }

    @Override
    public boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    @Override
    public boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getTextWithoutTegs(String text) {
        String REGEX_WITHOUT_TEGS = "<.+>";
        return text.replaceAll(REGEX_WITHOUT_TEGS, "");
    }

    public void indexingPageAndGetLemmas(Page page, Site site, LemmaRepository lemmaRep, IndexRepository indexRep) throws Exception {
        String textFromPage = getTextWithoutTegs(page.getContent());
        collectionLemmas = getLemmasFromText(textFromPage);
        for (Map.Entry<String, Integer> entry : collectionLemmas.entrySet()) {
            Lemma lemma = lemmaRep.findLemmaByLemmaAndSite(entry.getKey(), site);
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setLemma(entry.getKey());
                lemma.setSite(site);
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRep.save(lemma);
            Index index = new Index();
            index.setLemma(lemma);
            index.setPage(page);
            index.setRank(entry.getValue());
            indexRep.save(index);
        }
        collectionLemmas.clear();
    }
}
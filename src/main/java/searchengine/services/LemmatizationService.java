package searchengine.services;

import java.util.List;

public interface LemmatizationService {
    boolean anyWordBaseBelongToParticle(List<String> wordBaseForms);

    boolean hasParticleProperty(String wordBase);

    String getTextWithoutTegs(String text);
}
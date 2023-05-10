package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Lemma findLemmaByLemmaAndSite(String lemma, Site site);

    List<Lemma> findAllBySite(Site site);

    List<Lemma> findAllByLemmaOrderByFrequencyAsc(String lemma);
}
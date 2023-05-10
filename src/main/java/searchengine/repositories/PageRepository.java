package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;


@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    boolean existsPageByPathAndAndSite(String path, Site site);

    boolean existsPageByPath(String path);

    Page findPageByPath(String path);

    List<Page> findPagesBySite(Site site);
}
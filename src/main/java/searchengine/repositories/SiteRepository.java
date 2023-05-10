package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;
import searchengine.model.Status;


@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    boolean existsByStatus(Status status);

    boolean existsSiteByUrl(String url);

    Site findSiteByUrl(String url);
}
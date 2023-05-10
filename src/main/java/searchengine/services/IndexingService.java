package searchengine.services;

import searchengine.model.Page;
import searchengine.model.Site;

public interface IndexingService {
    boolean isValidLink(String link);

    void startIndexing();

    void stopIndexing();

    boolean isLinkFromConfig(String url);

    void updateOnePage(String url);

    void clearRepositories();

    void pageStatus(Page page, Site site, int code);
}
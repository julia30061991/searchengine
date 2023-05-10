package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    private final SitesList sites;
    @Autowired
    private IndexingServiceImpl indexingServiceImpl;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        if (!indexingServiceImpl.isIndexing()) {
            total.setIndexing(true);
        } else {
            total.setIndexing(false);
        }

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        int countPages = 0;
        int countLemmas = 0;
        for (SiteConfig site : sitesList) {
            String link = site.getUrl();
            if (siteRepository.existsSiteByUrl(link)) {
                DetailedStatisticsItem item = new DetailedStatisticsItem();
                item.setName(site.getName());
                item.setUrl(site.getUrl());
                int pages = pageRepository.findPagesBySite(siteRepository.findSiteByUrl(site.getUrl())).size();
                int lemmas = lemmaRepository.findAllBySite(siteRepository.findSiteByUrl(site.getUrl())).size();
                item.setPages(pages);
                item.setLemmas(lemmas);
                item.setError(siteRepository.findSiteByUrl(site.getUrl()).getLastError());
                item.setStatusTime(siteRepository.findSiteByUrl(site.getUrl()).getStatusTime());
                item.setStatus(siteRepository.findSiteByUrl(site.getUrl()).getStatus().toString());
                detailed.add(item);
                countPages += pages;
                countLemmas += lemmas;
            }
        }

        total.setPages(countPages);
        total.setLemmas(countLemmas);

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
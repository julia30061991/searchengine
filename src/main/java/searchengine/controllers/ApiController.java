package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.SearchedDataResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.DataResponse;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.SearchingServiceImpl;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    @Autowired
    private IndexingServiceImpl indexingServiceImpl;
    @Autowired
    private SearchingServiceImpl searchingServiceImpl;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<DataResponse> startIndexing() {
        if (!indexingServiceImpl.isIndexing()) {
            DataResponse dataResponse = new DataResponse(true);
            indexingServiceImpl.clearRepositories();
            indexingServiceImpl.startIndexing();
            return ResponseEntity.status(200).body(dataResponse);
        } else {
            DataResponse dataResponse = new DataResponse(false, "Индексация уже запущена");
            return ResponseEntity.status(405).body(dataResponse);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<DataResponse> stopIndexing() {
        if (indexingServiceImpl.isIndexing()) {
            DataResponse dataResponse = new DataResponse(true);
            indexingServiceImpl.stopIndexing();
            return ResponseEntity.status(200).body(dataResponse);
        } else {
            DataResponse dataResponse = new DataResponse(false, "Индексация не запущена");
            return ResponseEntity.status(405).body(dataResponse);
        }
    }

    @RequestMapping(value = "/indexPage", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<DataResponse> indexPage(@RequestParam(value = "url") String url) {
        if (!indexingServiceImpl.isLinkFromConfig(url)) {
            DataResponse dataResponse = new DataResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return ResponseEntity.status(404).body(dataResponse);
        } else {
            DataResponse dataResponse = new DataResponse(true);
            indexingServiceImpl.updateOnePage(url);
            return ResponseEntity.status(200).body(dataResponse);
        }
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public ResponseEntity<SearchedDataResponse> search(@RequestParam(value = "query") String query,
                                                       @RequestParam(value = "site", required = false) String site,
                                                       @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
                                                       @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        if (query.isEmpty()) {
            SearchedDataResponse dataResponse = new SearchedDataResponse();
            dataResponse.setResult(false);
            dataResponse.setError("Задан пустой поисковый запрос");
            return ResponseEntity.status(400).body(dataResponse);
        } else {
            searchingServiceImpl.searchInfo(query);
            SearchedDataResponse dataResponse = searchingServiceImpl.getContentFromPage(query);
            return ResponseEntity.status(200).body(dataResponse);
        }
    }
}
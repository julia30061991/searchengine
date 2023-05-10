package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.IndexedLinks;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private Site site;
    private String lastError = null;
    private boolean isIndexing = false;
    protected String parentLink = "";
    private ForkJoinPool forkJoinPool;
    private List<RecursiveIndexingUrl> allTasks = new CopyOnWriteArrayList<>();

    @Autowired
    private IndexedLinks indexedLinks;
    @Autowired
    private final SitesList sites;
    @Autowired
    private final SiteRepository siteRep;
    @Autowired
    private final PageRepository pageRep;
    @Autowired
    private final LemmaRepository lemmaRep;
    @Autowired
    private final IndexRepository indexRep;

    public boolean isIndexing() {
        return isIndexing;
    }

    public void setIndexing(boolean indexing) {
        isIndexing = indexing;
    }

    @Override
    public boolean isValidLink(String link) {
        link = link.toLowerCase();
        return link.contains(".jpg") || link.contains(".jpeg") || link.contains(".png") || link.contains(".gif")
                || link.contains(".webp") || link.contains(".pdf") || link.contains(".eps") || link.contains(".xlsx")
                || link.contains(".doc") || link.contains(".pptx") || link.contains(".docx") || link.contains("?_ga")
                || link.contains("#") || link.contains("?") || link.contains(".zip") || link.contains(".mp4")
                || link.contains(".wma") || link.contains(".ppt") || link.contains("/pdf") || link.contains("@mail.ru")
                || link.contains("@yandex.ru") || link.contains("@bk.ru");
    }

    @Override
    public void startIndexing() {
        setIndexing(true);
        for (SiteConfig siteConfig : sites.getSites()) {
            if (isIndexing) {
                site = new Site(lastError, siteConfig.getName(), Status.INDEXING, LocalDateTime.now(), siteConfig.getUrl());
                siteRep.save(site);
                parentLink = site.getUrl();
                System.out.println("Запускаю индексацию сайта " + site.getName());
                try {
                    RecursiveIndexingUrl recursiveIndexing = new RecursiveIndexingUrl(site.getUrl(), site);
                    forkJoinPool = new ForkJoinPool();
                    forkJoinPool.invoke(recursiveIndexing);
                    System.out.println("Индексация cайта " + site.getName() + " завершена");
                    site.setStatusTime(LocalDateTime.now());
                    site.setStatus(Status.INDEXED);
                    siteRep.save(site);
                } catch (Exception ex) {
                    System.out.println("Неизвестная ошибка подключения к сайту " + site.getName());
                    System.out.println("Индексация cайта " + site.getName() + " будет завершена с ошибкой");
                    ex.printStackTrace();
                    lastError = ex.getMessage();
                    site.setStatus(Status.FAILED);
                    siteRep.save(site);
                }
            } else {
                System.out.println("Индексация сайта " + siteConfig.getName() + " была остановлена");
            }
        }
    }

    @Override
    public void stopIndexing() { //TODO: не меняется кнопка start на stop на фронте, в постмене отрабатывает нормально
        if (siteRep.existsByStatus(Status.INDEXING)) {
            setIndexing(false);
            forkJoinPool.shutdownNow();
            allTasks.clear();
            System.out.println("Индексация сайта остановлена");
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            siteRep.save(site);
        } else {
            System.out.println("Индексация не запущена или уже завершена");
        }
    }

    class RecursiveIndexingUrl extends RecursiveAction {
        private Page page;
        private Site site;
        private String url;
        private int statusCode;

        public RecursiveIndexingUrl(String url, Site site) {
            this.url = url;
            this.site = site;
        }

        @Override
        public void compute() {
            synchronized (indexedLinks.getIndexedLinks()) {
                if (!isValidLink(url) && !indexedLinks.getIndexedLinks().contains(url) && url.startsWith(parentLink)) {
                    try {
                        Thread.sleep(2000);
                        System.out.println("Запускаю индексацию страницы " + url);
                        indexingPage(url, site);
                        Document document = Jsoup.connect(url).ignoreContentType(true).timeout(2000).get();
                        Elements linkParse = document.select("a");
                        for (Element element : linkParse) {
                            String childUrl = element.absUrl("href");
                            RecursiveIndexingUrl recursiveIndexingUrl = new RecursiveIndexingUrl(childUrl, site);
                            recursiveIndexingUrl.fork();
                            allTasks.add(recursiveIndexingUrl);
                        }
                    } catch (HttpStatusException ex) {
                        System.out.println("Ошибка HTTP-статуса страницы " + url);
                        pageStatus(page, site, ex.getStatusCode());
                    } catch (Exception e) {
                        System.out.println("Неизвестная ошибка обработки страницы " + url);
                        lastError = e.getMessage();
                    }
                }
            }
            if (!allTasks.isEmpty()) {
                for (RecursiveIndexingUrl task : allTasks) {
                    task.join();
                }
            }
        }

        public void indexingPage(String url, Site site) {
            try {
                Thread.sleep(2000);
                Connection.Response response = Jsoup.connect(url).followRedirects(false).timeout(1500).execute();
                statusCode = response.statusCode();
                Document document = Jsoup.connect(url).ignoreContentType(true).timeout(1500).get();
                if (document != null & isIndexing) {
                    URL link = new URL(url);
                    String subLink = link.getPath();
                    page = new Page(statusCode, document.html(), subLink, site);
                    pageStatus(page, site, statusCode);
                    site.setStatusTime(LocalDateTime.now());
                    synchronized (pageRep) {
                        if (!pageRep.existsPageByPathAndAndSite(page.getPath(), site)) {
                            pageRep.save(page);
                            siteRep.save(site);
                        }
                    }
                    indexedLinks.getIndexedLinks().add(url);
                    LemmatizationServiceImpl lemmaService = new LemmatizationServiceImpl();
                    synchronized (lemmaRep) {
                        lemmaService.indexingPageAndGetLemmas(page, site, lemmaRep, indexRep);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void pageStatus(Page page, Site site, int code) {
        switch (code) {
            case 200:
                page.setCode(code);
                break;
            case 301:
                page.setCode(code);
                site.setLastError("Страница была перемещена");
                siteRep.save(site);
                break;
            case 302:
                page.setCode(code);
                site.setLastError("Cтраница временно недоступна по данному адресу");
                siteRep.save(site);
                break;
            case 404:
                page.setCode(code);
                site.setLastError("Cтраница не найдена");
                siteRep.save(site);
                break;
            case 500:
                page.setCode(code);
                site.setLastError("Ошибка на стороне сервера");
                siteRep.save(site);
                break;
            case 400:
                page.setCode(code);
                site.setLastError("Некорректный запрос");
                siteRep.save(site);
                break;
            case 403:
                page.setCode(code);
                site.setLastError("Доступ к ресурсу ограничен");
                siteRep.save(site);
                break;
            case 401:
                page.setCode(code);
                site.setLastError("Требуется аутентифиация");
                siteRep.save(site);
                break;
            case 405:
                page.setCode(code);
                site.setLastError("Метод нельзя применить к текущему ресурсу");
                siteRep.save(site);
                break;
            case 408:
                page.setCode(code);
                site.setLastError("Время ожидания сервером передачи от клиента истекло");
                siteRep.save(site);
                break;
        }
    }

    @Override
    public boolean isLinkFromConfig(String url) {
        boolean result = false;
        for (SiteConfig site : sites.getSites()) {
            if (url.contains(site.getUrl())) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public void updateOnePage(String url) {
        if (url == null) {
            System.out.println("Передана пустая ссылка");
            return;
        }
        try {
            URL link = new URL(url);
            String subLink = link.getPath();
            if (pageRep.existsPageByPath(subLink)) {
                System.out.println("Страница имеется в базе, начинаю обновление");
                String siteUrl = "https://" + link.getHost() + "/";
                site = siteRep.findSiteByUrl(siteUrl);
                pageRep.flush();
                pageRep.delete(pageRep.findPageByPath(subLink));
                RecursiveIndexingUrl task = new RecursiveIndexingUrl(url, site);
                task.indexingPage(url, site);
            } else {
                System.out.println("Страницы нет в базе, добавляю");
                String siteUrl = "https://" + link.getHost() + "/";
                site = siteRep.findSiteByUrl(siteUrl);
                pageRep.flush();
                RecursiveIndexingUrl task = new RecursiveIndexingUrl(url, site);
                task.indexingPage(url, site);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearRepositories() {
        siteRep.deleteAll();
        pageRep.deleteAll();
        indexRep.deleteAll();
        lemmaRep.deleteAll();
    }
}
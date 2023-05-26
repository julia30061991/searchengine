package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Log4j2
public class IndexingServiceImpl implements IndexingService {
    private Site site;
    private String lastError = null;
    private boolean isIndexing = false;
    protected String parentLink = "";
    private ForkJoinPool forkJoinPool;

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
        String REGEX_URL = "^(?:.*(?=(jpg|lang=en|lang=ru|jpeg|png|gif|webp|pdf|eps|xlsx|doc|pptx|docx|zip|mp4|wma|ppt|ru)$))?[^.]*$";
        return !link.matches(REGEX_URL)
                && !link.contains("#")
                && !link.contains("?")
                && link.startsWith(parentLink);
    }

    @Override
    public void startIndexing() {
        setIndexing(true);
        for (SiteConfig siteConfig : sites.getSites()) {
            if (isIndexing) {
                site = new Site(lastError, siteConfig.getName(), Status.INDEXING, LocalDateTime.now(), siteConfig.getUrl());
                siteRep.save(site);
                parentLink = site.getUrl();
                log.info("Запускаю индексацию сайта " + site.getName());
                try {
                    RecursiveIndexingUrl recursiveIndexing = new RecursiveIndexingUrl(site.getUrl(), site);
                    forkJoinPool = new ForkJoinPool();
                    forkJoinPool.invoke(recursiveIndexing);
                    site.setStatusTime(LocalDateTime.now());
                    site.setStatus(Status.INDEXED);
                    siteRep.save(site);
                    log.info("Индексация cайта " + site.getName() + " завершена");
                } catch (Exception ex) {
                    log.error("Неизвестная ошибка подключения к сайту " + site.getName());
                    log.warn("Индексация cайта " + site.getName() + " будет завершена с ошибкой");
                    lastError = ex.getMessage();
                    site.setStatus(Status.FAILED);
                    siteRep.save(site);
                }
            } else {
                log.info("Индексация сайта " + siteConfig.getName() + " была остановлена");
            }
        }
    }

    @Override
    public void stopIndexing() {
        if (siteRep.existsByStatus(Status.INDEXING)) {
            setIndexing(false);
            forkJoinPool.shutdownNow();
            log.info("Индексация сайта остановлена пользователем");
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("Индексация остановлена пользователем");
            siteRep.save(site);
        } else {
            log.info("Индексация не запущена или уже завершена");
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
            List<RecursiveIndexingUrl> allTasks = new ArrayList<>();
            synchronized (indexedLinks.getIndexedLinks()) {
                if (isValidLink(url) && !indexedLinks.getIndexedLinks().contains(url) && isIndexing) {
                    try {
                        Thread.sleep(3000);
                        log.info("Запускаю индексацию страницы " + url);
                        Document document = indexingPage(url, site);
                        Elements linkParse = document.select("a");
                        for (Element element : linkParse) {
                            String childUrl = element.absUrl("href");
                            if (isValidLink(childUrl) && !indexedLinks.getIndexedLinks().contains(childUrl) && isIndexing) {
                                RecursiveIndexingUrl recursiveIndexingUrl = new RecursiveIndexingUrl(childUrl, site);
                                recursiveIndexingUrl.fork();
                                allTasks.add(recursiveIndexingUrl);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Ошибка обработки страницы " + url + ", cтраница будет сохранена со статусом ошибки");
                    }
                }
            }
            if (!allTasks.isEmpty()) {
                for (RecursiveIndexingUrl task : allTasks) {
                    task.join();
                }
            }
        }

        private Document indexingPage(String url, Site site) {
            Document document = new Document("");
            try {
                Connection connection = Jsoup.connect(url);
                document = connection.ignoreContentType(true).timeout(3000).get();
                statusCode = connection.followRedirects(false).execute().statusCode();
                if (document == null) {
                    throw new HttpStatusException("Подключение отсутствует", 404, url);
                }
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
                lemmaService.indexingPageAndGetLemmas(page, site, lemmaRep, indexRep);
            } catch (HttpStatusException ex) {
                log.error("Ошибка HTTP-статуса страницы " + url);
                page = new Page(ex.getStatusCode(), document.html(), url.replaceAll(site.getUrl(), "/"), site);
                pageStatus(page, site, ex.getStatusCode());
                pageRep.save(page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return document;
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
            case 400:
                page.setCode(code);
                site.setLastError("Некорректный запрос");
                siteRep.save(site);
                break;
            case 401:
                page.setCode(code);
                site.setLastError("Требуется аутентифиация");
                siteRep.save(site);
                break;
            case 403:
                page.setCode(code);
                site.setLastError("Доступ к ресурсу ограничен");
                siteRep.save(site);
                break;
            case 404:
                page.setCode(code);
                site.setLastError("Cтраница не найдена");
                siteRep.save(site);
                break;
            case 405:
                page.setCode(code);
                site.setLastError("Метод нельзя применить к текущему ресурсу");
                siteRep.save(site);
                break;
            case 500:
                page.setCode(code);
                site.setLastError("Ошибка на стороне сервера");
                siteRep.save(site);
                break;
            default:
                site.setLastError("Неизвестная ошибка при индексации");
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
            log.warn("Передана пустая ссылка");
            return;
        }
        try {
            URL link = new URL(url);
            String subLink = link.getPath();
            if (pageRep.existsPageByPath(subLink)) {
                log.info("Страница имеется в базе, начинаю обновление");
                String siteUrl = "https://" + link.getHost() + "/";
                site = siteRep.findSiteByUrl(siteUrl);
                pageRep.flush();
                pageRep.delete(pageRep.findPageByPath(subLink));
                setIndexing(true);
                RecursiveIndexingUrl task = new RecursiveIndexingUrl(url, site);
                task.indexingPage(url, site);
            } else {
                log.info("Страницы нет в базе, добавляю");
                String siteUrl = "https://" + link.getHost() + "/";
                site = siteRep.findSiteByUrl(siteUrl);
                pageRep.flush();
                setIndexing(true);
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
//максимальное кол-во строк кода в методе - 28
//самый длинный - pageStatus со switch - 45 строк (возможно сократить при переходе на джаву 12, версию не повышала)
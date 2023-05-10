package searchengine.dto.statistics;

import lombok.Data;

@Data
public class DetailedSearchedResult {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
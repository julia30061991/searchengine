package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class SearchedDataResponse {
    private boolean result;
    private int count;
    private String error;
    private List<DetailedSearchedResult> data;
}
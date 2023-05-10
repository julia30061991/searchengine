package searchengine.dto.statistics;

import lombok.Data;

@Data
public class DataResponse {
    private boolean result;
    private String error;

    public DataResponse(boolean result) {
        this.result = result;
    }

    public DataResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}